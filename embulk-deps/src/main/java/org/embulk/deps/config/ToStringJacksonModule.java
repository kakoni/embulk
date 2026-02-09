package org.embulk.deps.config;

import org.embulk.spi.unit.ToString;
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
import tools.jackson.databind.node.NullNode;

@Deprecated
public final class ToStringJacksonModule extends SimpleModule {
    @SuppressWarnings("deprecation")
    public ToStringJacksonModule() {
        this.addSerializer(ToString.class, new ToStringSerializer());
        this.addDeserializer(ToString.class, new ToStringDeserializer());
    }

    @SuppressWarnings("deprecation")
    private static class ToStringSerializer extends ValueSerializer<ToString> {
        @Override
        public void serialize(
                final ToString value,
                final JsonGenerator jsonGenerator,
                final SerializationContext provider) {
            jsonGenerator.writeString(value.toString());
        }
    }

    @SuppressWarnings("deprecation")
    private static class ToStringDeserializer extends ValueDeserializer<ToString> {
        @Override
        public ToString deserialize(
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

            return new ToString(jsonNodeToString(node != null ? node : NullNode.getInstance(), jsonParser));
        }

        private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    static String jsonNodeToString(final JsonNode node, final JsonParser jsonParser) throws DatabindException {
        if (node.isString()) {
            return node.asText();
        } else if (node.isValueNode()) {
            return node.toString();
        }
        throw DatabindException.from(jsonParser, String.format("Arrays and objects are invalid: '%s'", node));
    }
}
