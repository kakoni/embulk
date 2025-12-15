package org.embulk.deps.config;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;

final class ByteSizeJacksonModule extends SimpleModule {
    @SuppressWarnings("deprecation")  // For use of org.embulk.spi.unit.ByteSize
    public ByteSizeJacksonModule() {
        this.addSerializer(org.embulk.spi.unit.ByteSize.class, new ByteSizeSerializer());
        this.addDeserializer(org.embulk.spi.unit.ByteSize.class, new ByteSizeDeserializer());
    }

    @SuppressWarnings("deprecation")  // For use of org.embulk.spi.unit.ByteSize
    private static class ByteSizeSerializer extends ValueSerializer<org.embulk.spi.unit.ByteSize> {
        @Override
        public void serialize(
                final org.embulk.spi.unit.ByteSize value,
                final JsonGenerator jsonGenerator,
                final SerializationContext provider) {
            jsonGenerator.writeString(value.toString());
        }
    }

    @SuppressWarnings("deprecation")  // For use of org.embulk.spi.unit.ByteSize
    private static class ByteSizeDeserializer extends ValueDeserializer<org.embulk.spi.unit.ByteSize> {
        @Override
        public org.embulk.spi.unit.ByteSize deserialize(
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

            return getByteSize(node, jsonParser);
        }

        private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    @SuppressWarnings("deprecation")  // For use of org.embulk.spi.unit.ByteSize
    private static org.embulk.spi.unit.ByteSize getByteSize(final JsonNode node, final JsonParser jsonParser) throws DatabindException {
        if (node.isString()) {
            return org.embulk.spi.unit.ByteSize.parseByteSize(node.asString());
        } else if (node.isIntegralNumber()) {
            return new org.embulk.spi.unit.ByteSize(node.asLong());
        }
        throw DatabindException.from(jsonParser, "ByteSize must be a string or an integer.");
    }
}
