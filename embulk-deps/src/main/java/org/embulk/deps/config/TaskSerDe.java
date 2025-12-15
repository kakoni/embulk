package org.embulk.deps.config;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.Version;
import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.deser.Deserializers;
import tools.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.embulk.config.Config;
import org.embulk.config.ConfigDefault;
import org.embulk.config.Task;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;

class TaskSerDe {
    public static class TaskSerializer extends ValueSerializer<Task> {
        private final ObjectMapper nestedObjectMapper;

        public TaskSerializer(ObjectMapper nestedObjectMapper) {
            this.nestedObjectMapper = nestedObjectMapper;
        }

        @Override
        public void serialize(Task value, JsonGenerator jgen, SerializationContext provider) {
            if (value instanceof Proxy) {
                Object handler = Proxy.getInvocationHandler(value);
                if (handler instanceof TaskInvocationHandler) {
                    TaskInvocationHandler h = (TaskInvocationHandler) handler;
                    Map<String, Object> objects = h.getObjects();
                    jgen.writeStartObject();
                    for (Map.Entry<String, Object> pair : objects.entrySet()) {
                        jgen.writeName(pair.getKey());
                        nestedObjectMapper.writeValue(jgen, pair.getValue());
                    }
                    jgen.writeEndObject();
                    return;
                }
            }
            // TODO exception class & message
            throw new UnsupportedOperationException("Serializing Task is not supported");
        }
    }

    public static class TaskDeserializer<T> extends ValueDeserializer<T> {
        private final ObjectMapper nestedObjectMapper;

        private final ModelManagerDelegateImpl model;

        private final Class<?> iface;
        private final Map<String, List<FieldEntry>> mappings;

        public TaskDeserializer(ObjectMapper nestedObjectMapper, ModelManagerDelegateImpl model, Class<T> iface) {
            this.nestedObjectMapper = nestedObjectMapper;
            this.model = model;
            this.iface = iface;
            this.mappings = getterMappings(iface);
        }

        protected Map<String, List<FieldEntry>> getterMappings(Class<?> iface) {
            final LinkedHashMap<String, ArrayList<FieldEntry>> builder = new LinkedHashMap<>();
            for (Map.Entry<String, Method> getter : TaskInvocationHandler.fieldGetters(iface)) {
                Method getterMethod = getter.getValue();
                String fieldName = getter.getKey();

                Type fieldType = getterMethod.getGenericReturnType();

                final Optional<String> jsonKey = getJsonKey(getterMethod, fieldName);
                if (!jsonKey.isPresent()) {
                    // skip this field
                    continue;
                }
                final Optional<String> defaultJsonString = getDefaultJsonString(getterMethod);
                builder.compute(jsonKey.get(), (key, oldValue) -> {
                    final ArrayList<FieldEntry> newValue;
                    if (oldValue == null) {
                        newValue = new ArrayList<>();
                    } else {
                        newValue = oldValue;
                    }
                    newValue.add(new FieldEntry(fieldName, fieldType, defaultJsonString));
                    return newValue;
                });
            }
            return Collections.unmodifiableMap(StreamSupport.stream(builder.entrySet().spliterator(), false)
                    .map(entry -> new AbstractMap.SimpleImmutableEntry<>(entry.getKey(), Collections.unmodifiableList(entry.getValue())))
                    .collect(Collectors.toMap(
                            Map.Entry<String, List<FieldEntry>>::getKey,
                            Map.Entry<String, List<FieldEntry>>::getValue,
                            (e1, e2) -> e1,
                            LinkedHashMap::new)));
        }

        protected Optional<String> getJsonKey(final Method getterMethod, final String fieldName) {
            return Optional.of(fieldName);
        }

        protected Optional<String> getDefaultJsonString(final Method getterMethod) {
            return Optional.empty();
        }

        @Override
        @SuppressWarnings("unchecked")
        public T deserialize(JsonParser jp, DeserializationContext ctxt) {
            Map<String, Object> objects = new ConcurrentHashMap<String, Object>();
            final ArrayList<Map.Entry<String, FieldEntry>> unusedMappings = new ArrayList<>();
            for (final Map.Entry<String, List<FieldEntry>> entry : this.mappings.entrySet()) {
                for (final FieldEntry fieldEntry : entry.getValue()) {
                    unusedMappings.add(new AbstractMap.SimpleImmutableEntry(entry.getKey(), fieldEntry));
                }
            }

            String key;
            JsonToken current = jp.currentToken();
            if (current == JsonToken.START_OBJECT) {
                current = jp.nextToken();
                key = jp.currentName();
            } else {
                key = jp.nextName();
            }

            for (; key != null; key = jp.nextName()) {
                JsonToken t = jp.nextToken(); // to get to value
                final Collection<FieldEntry> fields = mappings.get(key);
                if (fields == null || fields.isEmpty()) {
                    jp.skipChildren();
                } else {
                    final JsonNode children = nestedObjectMapper.readValue(jp, JsonNode.class);
                    for (final FieldEntry field : fields) {
                        final Object value = nestedObjectMapper.convertValue(children, new GenericTypeReference(field.getType()));
                        if (value == null) {
                            throw DatabindException.from(jp, "Setting null to a task field is not allowed. Use Optional<T> to represent null.");
                        }
                        objects.put(field.getName(), value);
                        if (!unusedMappings.remove(new AbstractMap.SimpleImmutableEntry(key, field))) {
                            throw DatabindException.from(jp, String.format(
                                    "FATAL: Expected to be a bug in Embulk. Mapping \"%s: (%s) %s\" might have already been processed, or not in %s.",
                                    key,
                                    field.getType().toString(),
                                    field.getName(),
                                    this.iface.toString()));
                        }
                    }
                }
            }

            // set default values
            for (final Map.Entry<String, FieldEntry> unused : unusedMappings) {
                FieldEntry field = unused.getValue();
                if (field.getDefaultJsonString().isPresent()) {
                    Object value = nestedObjectMapper.readValue(field.getDefaultJsonString().get(), new GenericTypeReference(field.getType()));
                    if (value == null) {
                        throw DatabindException.from(jp, "Setting null to a task field is not allowed. Use Optional<T> to represent null.");
                    }
                    objects.put(field.getName(), value);
                } else {
                    // required field
                    throw DatabindException.from(jp, "Field '" + unused.getKey() + "' is required but not set");
                }
            }

            return (T) Proxy.newProxyInstance(
                    iface.getClassLoader(), new Class<?>[] {iface},
                    new TaskInvocationHandler(model, iface, objects));
        }

        private static class FieldEntry {
            private final String name;
            private final Type type;
            private final Optional<String> defaultJsonString;

            public FieldEntry(String name, Type type, Optional<String> defaultJsonString) {
                this.name = name;
                this.type = type;
                this.defaultJsonString = defaultJsonString;
            }

            public String getName() {
                return name;
            }

            public Type getType() {
                return type;
            }

            public Optional<String> getDefaultJsonString() {
                return defaultJsonString;
            }

            @Override
            public int hashCode() {
                return Objects.hash(this.name, this.type, this.defaultJsonString);
            }

            @Override
            public boolean equals(final Object otherObject) {
                if (this == otherObject) {
                    return true;
                }
                if (!(otherObject instanceof FieldEntry)) {
                    return false;
                }
                final FieldEntry other = (FieldEntry) otherObject;
                return Objects.equals(this.name, other.name)
                        && Objects.equals(this.type, other.type)
                        && Objects.equals(this.defaultJsonString, other.defaultJsonString);
            }
        }
    }

    public static class TaskSerializerModule extends SimpleModule {
        public TaskSerializerModule(ObjectMapper nestedObjectMapper) {
            super();
            addSerializer(Task.class, new TaskSerializer(nestedObjectMapper));
        }
    }

    public static class ConfigTaskDeserializer<T> extends TaskDeserializer<T> {
        public ConfigTaskDeserializer(ObjectMapper nestedObjectMapper, ModelManagerDelegateImpl model, Class<T> iface) {
            super(nestedObjectMapper, model, iface);
        }

        @Override
        protected Optional<String> getJsonKey(final Method getterMethod, final String fieldName) {
            final Config a = getterMethod.getAnnotation(Config.class);
            if (a != null) {
                return Optional.of(a.value());
            } else {
                return Optional.empty();  // skip this field
            }
        }

        @Override
        protected Optional<String> getDefaultJsonString(final Method getterMethod) {
            final ConfigDefault a = getterMethod.getAnnotation(ConfigDefault.class);
            if (a != null && !a.value().isEmpty()) {
                return Optional.of(a.value());
            }
            return super.getDefaultJsonString(getterMethod);
        }
    }

    public static class TaskDeserializerModule extends JacksonModule {  // can't use just SimpleModule, due to generic types
        protected final ObjectMapper nestedObjectMapper;

        protected final ModelManagerDelegateImpl model;

        public TaskDeserializerModule(ObjectMapper nestedObjectMapper, ModelManagerDelegateImpl model) {
            this.nestedObjectMapper = nestedObjectMapper;
            this.model = model;
        }

        @Override
        public String getModuleName() {
            return "embulk.config.TaskSerDe";
        }

        @Override
        public Version version() {
            return Version.unknownVersion();
        }

        @Override
        public void setupModule(SetupContext context) {
            context.addDeserializers(new Deserializers() {
                @Override
                public ValueDeserializer<?> findBeanDeserializer(
                        JavaType type,
                        DeserializationConfig config,
                        BeanDescription.Supplier beanDesc) throws DatabindException {
                    final Class<?> raw = type.getRawClass();
                    if (Task.class.isAssignableFrom(raw)) {
                        return newTaskDeserializer(raw);
                    }
                    return null;
                }

                @Override
                public boolean hasDeserializerFor(DeserializationConfig config, Class<?> valueType) {
                    return Task.class.isAssignableFrom(valueType);
                }
            });
        }

        @SuppressWarnings("unchecked")
        protected ValueDeserializer<?> newTaskDeserializer(Class<?> raw) {
            return new TaskDeserializer(nestedObjectMapper, model, raw);
        }
    }

    public static class ConfigTaskDeserializerModule extends TaskDeserializerModule {
        public ConfigTaskDeserializerModule(ObjectMapper nestedObjectMapper, ModelManagerDelegateImpl model) {
            super(nestedObjectMapper, model);
        }

        @Override
        public String getModuleName() {
            return "embulk.config.ConfigTaskSerDe";
        }

        @Override
        @SuppressWarnings("unchecked")
        protected ValueDeserializer<?> newTaskDeserializer(Class<?> raw) {
            return new ConfigTaskDeserializer(nestedObjectMapper, model, raw);
        }
    }
}
