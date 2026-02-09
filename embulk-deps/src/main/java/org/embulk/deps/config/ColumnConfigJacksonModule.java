package org.embulk.deps.config;

import org.embulk.config.ConfigException;
import org.embulk.config.ConfigSource;
import org.embulk.spi.ColumnConfig;
import org.embulk.spi.type.Type;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ObjectNode;

public final class ColumnConfigJacksonModule extends SimpleModule {
    public ColumnConfigJacksonModule(final ModelManagerDelegateImpl model) {
        this.addSerializer(ColumnConfig.class, new ColumnConfigSerializer(model));
        this.addDeserializer(ColumnConfig.class, new ColumnConfigDeserializer(model));
    }

    private static class ColumnConfigSerializer extends ValueSerializer<ColumnConfig> {
        ColumnConfigSerializer(final ModelManagerDelegateImpl model) {
            this.model = model;
        }

        @Override
        public void serialize(
                final ColumnConfig value,
                final JsonGenerator jsonGenerator,
                final SerializationContext provider) {
            final ConfigSource option = value.getOption();
            final ObjectNode object = model.writeObjectAsObjectNode(option);
            object.put("name", value.getName());
            object.put("type", value.getType().getName());
            jsonGenerator.writeTree(object);
        }

        private final ModelManagerDelegateImpl model;
    }

    private static class ColumnConfigDeserializer extends ValueDeserializer<ColumnConfig> {
        ColumnConfigDeserializer(final ModelManagerDelegateImpl model) {
            this.model = model;
        }

        @Override
        public ColumnConfig deserialize(
                final JsonParser jsonParser,
                final DeserializationContext context)
                throws DatabindException {
            final JsonNode node;
            try {
                node = OBJECT_MAPPER.readTree(jsonParser);
            } catch (final StreamReadException ex) {
                throw DatabindException.from(jsonParser, "Failed to parse JSON.", ex);
            } catch (final JacksonException ex) {
                throw DatabindException.from(jsonParser, "Failed to process JSON in parsing.", ex);
            }

            if (!node.isObject()) {
                throw DatabindException.from(jsonParser, "Expected object to deserialize ColumnConfig");
            }

            try {
                final ConfigSource config = (ConfigSource) new DataSourceImpl(model, (ObjectNode) node);

                final String name = config.get(String.class, "name");
                final Type type = config.get(Type.class, "type");
                final ConfigSource option = config.deepCopy();
                option.remove("name");
                option.remove("type");

                return new ColumnConfig(name, type, option);
            } catch (final ConfigException ex) {
                throw DatabindException.from(jsonParser, "Invalid object to deserialize ColumnConfig", ex);
            }
        }

        private final ModelManagerDelegateImpl model;
    }

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
}
