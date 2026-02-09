package org.embulk.deps.config;

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

final class TimestampFormatJacksonModule extends SimpleModule {
    @SuppressWarnings("deprecation")  // For use of org.embulk.spi.time.TimestampFormat
    public TimestampFormatJacksonModule() {
        this.addSerializer(org.embulk.spi.time.TimestampFormat.class, new TimestampFormatSerializer());
        this.addDeserializer(org.embulk.spi.time.TimestampFormat.class, new TimestampFormatDeserializer());
    }

    @SuppressWarnings("deprecation")  // For use of org.embulk.spi.time.TimestampFormat
    private static class TimestampFormatSerializer extends ValueSerializer<org.embulk.spi.time.TimestampFormat> {
        @Override
        public void serialize(
                final org.embulk.spi.time.TimestampFormat value,
                final JsonGenerator jsonGenerator,
                final SerializationContext provider) {
            jsonGenerator.writeString(value.getFormat());
        }
    }

    @SuppressWarnings("deprecation")  // For use of org.embulk.spi.time.TimestampFormat
    private static class TimestampFormatDeserializer extends ValueDeserializer<org.embulk.spi.time.TimestampFormat> {
        @Override
        public org.embulk.spi.time.TimestampFormat deserialize(
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

            return new org.embulk.spi.time.TimestampFormat(getString(node, jsonParser));
        }

        private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    private static String getString(final JsonNode node, final JsonParser jsonParser) throws DatabindException {
        if (node.isString()) {
            return node.asText();
        }
        throw DatabindException.from(jsonParser, "TimestampFormat must be a string.");
    }
}
