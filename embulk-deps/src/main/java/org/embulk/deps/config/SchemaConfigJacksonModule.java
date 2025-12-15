package org.embulk.deps.config;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Collections;
import org.embulk.config.ConfigSource;
import org.embulk.spi.ColumnConfig;
import org.embulk.spi.SchemaConfig;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;

public final class SchemaConfigJacksonModule extends SimpleModule {
    public SchemaConfigJacksonModule(final ModelManagerDelegateImpl model) {
        this.addSerializer(SchemaConfig.class, new SchemaConfigSerializer(model));
        this.addDeserializer(SchemaConfig.class, new SchemaConfigDeserializer(model));
    }

    private static class SchemaConfigSerializer extends ValueSerializer<SchemaConfig> {
        SchemaConfigSerializer(final ModelManagerDelegateImpl model) {
            this.model = model;
        }

        @Override
        public void serialize(
                final SchemaConfig value,
                final JsonGenerator jsonGenerator,
                final SerializationContext provider) {
            final ArrayNode array = OBJECT_MAPPER.createArrayNode();

            for (final ColumnConfig columnConfig : value.getColumns()) {
                final ConfigSource option = columnConfig.getOption();
                final ObjectNode object = this.model.writeObjectAsObjectNode(option);
                object.put("name", columnConfig.getName());
                object.put("type", columnConfig.getType().getName());
                array.add(object);
            }
            jsonGenerator.writeTree(array);
        }

        private final ModelManagerDelegateImpl model;
    }

    private static class SchemaConfigDeserializer extends ValueDeserializer<SchemaConfig> {
        SchemaConfigDeserializer(final ModelManagerDelegateImpl model) {
            this.model = model;
        }

        @Override
        public SchemaConfig deserialize(
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

            if (!node.isArray()) {
                throw DatabindException.from(jsonParser, "Expected array to deserialize SchemaConfig");
            }
            final ArrayList<ColumnConfig> columnConfigs = new ArrayList<>();
            for (final JsonNode columnConfigNode : (ArrayNode) node) {
                columnConfigs.add(model.readObject(ColumnConfig.class, columnConfigNode.traverse(tools.jackson.core.ObjectReadContext.empty())));
            }
            return new SchemaConfig(Collections.unmodifiableList(columnConfigs));
        }

        private final ModelManagerDelegateImpl model;
    }

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
}
