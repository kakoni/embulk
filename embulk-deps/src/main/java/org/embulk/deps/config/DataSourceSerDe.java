package org.embulk.deps.config;

import org.embulk.config.ConfigDiff;
import org.embulk.config.ConfigSource;
import org.embulk.config.DataSource;
import org.embulk.config.TaskReport;
import org.embulk.config.TaskSource;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.exc.StreamWriteException;
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

public class DataSourceSerDe {
    public static class SerDeModule extends SimpleModule {
        public SerDeModule(final ModelManagerDelegateImpl model) {
            // DataSourceImpl
            addSerializer(DataSourceImpl.class, new DataSourceSerializer<DataSourceImpl>(model));
            addDeserializer(DataSourceImpl.class, new DataSourceDeserializer<DataSourceImpl>(model));

            // ConfigSource
            addSerializer(ConfigSource.class, new DataSourceSerializer<ConfigSource>(model));
            addDeserializer(ConfigSource.class, new DataSourceDeserializer<ConfigSource>(model));

            // TaskSource
            addSerializer(TaskSource.class, new DataSourceSerializer<TaskSource>(model));
            addDeserializer(TaskSource.class, new DataSourceDeserializer<TaskSource>(model));

            // TaskReport
            addSerializer(TaskReport.class, new DataSourceSerializer<TaskReport>(model));
            addDeserializer(TaskReport.class, new DataSourceDeserializer<TaskReport>(model));

            // ConfigDiff
            addSerializer(ConfigDiff.class, new DataSourceSerializer<ConfigDiff>(model));
            addDeserializer(ConfigDiff.class, new DataSourceDeserializer<ConfigDiff>(model));
        }
    }

    // TODO T extends DataSource super DataSourceImpl
    private static class DataSourceDeserializer<T extends DataSource> extends ValueDeserializer<T> {
        private final ModelManagerDelegateImpl model;

        private final ObjectMapper treeObjectMapper;

        DataSourceDeserializer(ModelManagerDelegateImpl model) {
            this.model = model;
            this.treeObjectMapper = JsonMapper.builder()
                    .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .build();
        }

        @Override
        @SuppressWarnings("unchecked")
        public T deserialize(JsonParser jp, DeserializationContext ctxt) {
            final JsonNode json;
            try {
                json = treeObjectMapper.readTree(jp);
            } catch (final tools.jackson.core.JacksonException ex) {
                throw DatabindException.from(ctxt, "Expected object to deserialize DataSource", ex);
            }
            if (!json.isObject()) {
                throw DatabindException.from(ctxt, "Expected object to deserialize DataSource");
            }
            return (T) new DataSourceImpl(model, (ObjectNode) json);
        }
    }

    private static class DataSourceSerializer<T extends DataSource> extends ValueSerializer<T> {
        private final ModelManagerDelegateImpl model;

        DataSourceSerializer(final ModelManagerDelegateImpl model) {
            this.model = model;
        }

        @Override
        public void serialize(T value, JsonGenerator jgen, SerializationContext provider) {
            if (value == null) {
                throw new StreamWriteException(jgen,
                        "DataSourceSerDe.DataSourceSerializer#serialize accepts only non-null value");
            }
            final String valueJsonStringified = value.toJson();
            if (valueJsonStringified == null) {
                throw new StreamWriteException(jgen,
                        "DataSourceSerDe.DataSourceSerializer#serialize accepts only valid DataSource");
            }
            final JsonNode valueJsonNode = this.model.readObject(JsonNode.class, valueJsonStringified);
            if (!valueJsonNode.isObject()) {
                throw new StreamWriteException(jgen,
                        "DataSourceSerDe.DataSourceSerializer#serialize accepts only valid JSON object");
            }
            ((ObjectNode) valueJsonNode).serialize(jgen, provider);
        }
    }
}
