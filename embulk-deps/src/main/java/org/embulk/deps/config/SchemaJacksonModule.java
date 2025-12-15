package org.embulk.deps.config;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.deser.std.StdNodeBasedDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Collections;
import org.embulk.spi.Column;
import org.embulk.spi.Schema;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public final class SchemaJacksonModule extends SimpleModule {
    public SchemaJacksonModule() {
        this.addSerializer(Schema.class, new SchemaSerializer());
        this.addDeserializer(Schema.class, new SchemaDeserializer());
    }

    private static class SchemaSerializer extends ValueSerializer<Schema> {
        @Override
        public void serialize(
                final Schema value,
                final JsonGenerator jsonGenerator,
                final SerializationContext provider) {
            final ArrayNode array = OBJECT_MAPPER.createArrayNode();
            for (final Column column : value.getColumns()) {
                final ObjectNode object = OBJECT_MAPPER.createObjectNode();
                object.put("index", column.getIndex());
                object.put("name", column.getName());
                object.put("type", column.getType().getName());
                array.add(object);
            }
            jsonGenerator.writeTree(array);
        }
    }

    private static class SchemaDeserializer extends StdNodeBasedDeserializer<Schema> {
        protected SchemaDeserializer() {
            super(Schema.class);
        }

        @Override
        public Schema convert(
                final JsonNode root,
                final DeserializationContext context)
                throws JacksonException {
            if (root == null || !root.isArray()) {
                throw DatabindException.from(context.getParser(), "Schema expects a JSON Array node.");
            }
            final ArrayNode array = (ArrayNode) root;

            final ArrayList<Column> builder = new ArrayList<>();
            for (final JsonNode element : array.elements()) {
                builder.add(ColumnJacksonModule.convertJsonNodeToColumn(element, context.getParser()));
            }

            return new Schema(Collections.unmodifiableList(builder));
        }
    }

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
}
