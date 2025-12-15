package org.embulk.deps.config;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.deser.std.StdNodeBasedDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.embulk.spi.Column;
import org.embulk.spi.type.Type;
import org.embulk.spi.type.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public final class ColumnJacksonModule extends SimpleModule {
    public ColumnJacksonModule() {
        this.addSerializer(Column.class, new ColumnSerializer());
        this.addDeserializer(Column.class, new ColumnDeserializer());
    }

    private static class ColumnSerializer extends ValueSerializer<Column> {
        @Override
        public void serialize(
                final Column value,
                final JsonGenerator jsonGenerator,
                final SerializationContext provider) {
            final ObjectNode object = OBJECT_MAPPER.createObjectNode();
            object.put("index", value.getIndex());
            object.put("name", value.getName());
            object.put("type", value.getType().getName());
            jsonGenerator.writeTree(object);
        }
    }

    private static class ColumnDeserializer extends StdNodeBasedDeserializer<Column> {
        protected ColumnDeserializer() {
            super(Column.class);
        }

        @Override
        public Column convert(
                final JsonNode root,
                final DeserializationContext context)
                throws JacksonException {
            return convertJsonNodeToColumn(root, context.getParser());
        }
    }

    static final Column convertJsonNodeToColumn(final JsonNode root, final JsonParser jsonParser) throws JacksonException {
        if (root == null || !root.isObject()) {
            throw DatabindException.from(jsonParser, "Column expects a JSON Object node.");
        }
        final ObjectNode object = (ObjectNode) root;

        final JsonNode indexNode = object.get("index");
        final int index;
        if (indexNode == null) {
            logger.warn("Building Column from JSON without \"index\".",
                        DatabindException.from(jsonParser, "Building Column from JSON without \"index\"."));
            index = 0;
        } else {
            index = OBJECT_MAPPER.treeToValue(indexNode, int.class);
        }

        final JsonNode nameNode = object.get("name");
        if (nameNode == null) {
            throw DatabindException.from(jsonParser, "Building Column from JSON without \"name\".");
        }
        final String name = OBJECT_MAPPER.treeToValue(nameNode, String.class);

        final JsonNode typeNode = object.get("type");
        if (typeNode == null) {
            throw DatabindException.from(jsonParser, "Building Column from JSON without \"type\".");
        }
        final String typeString = OBJECT_MAPPER.treeToValue(typeNode, String.class);

        if (!STRING_TO_TYPE.containsKey(typeString)) {
            throw DatabindException.from(jsonParser, "Building Column from JSON with unexpected type: " + typeString);
        }
        final Type type = STRING_TO_TYPE.get(typeString);

        return new Column(index, name, type);
    }

    static {
        final HashMap<String, Type> builder = new HashMap<>();
        builder.put(Types.BOOLEAN.getName(), Types.BOOLEAN);
        builder.put(Types.LONG.getName(), Types.LONG);
        builder.put(Types.DOUBLE.getName(), Types.DOUBLE);
        builder.put(Types.STRING.getName(), Types.STRING);
        builder.put(Types.TIMESTAMP.getName(), Types.TIMESTAMP);
        builder.put(Types.JSON.getName(), Types.JSON);
        STRING_TO_TYPE = Collections.unmodifiableMap(builder);
    }

    private static final Logger logger = LoggerFactory.getLogger(ColumnJacksonModule.class);

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    private static final Map<String, Type> STRING_TO_TYPE;
}
