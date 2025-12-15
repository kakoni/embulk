package org.embulk.deps.config;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.FromStringDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

@Deprecated
public final class TimestampJacksonModule extends SimpleModule {
    @SuppressWarnings("deprecation")  // For use of org.embulk.spi.time.Timestamp
    public TimestampJacksonModule() {
        this.addSerializer(org.embulk.spi.time.Timestamp.class, new TimestampSerializer());
        this.addDeserializer(org.embulk.spi.time.Timestamp.class, new TimestampDeserializer());
    }

    private static class TimestampSerializer extends ValueSerializer<org.embulk.spi.time.Timestamp> {
        @Override
        public void serialize(org.embulk.spi.time.Timestamp value, JsonGenerator jgen, SerializationContext provider) {
            jgen.writeString(value.toString());
        }
    }

    private static class TimestampDeserializer extends FromStringDeserializer<org.embulk.spi.time.Timestamp> {
        public TimestampDeserializer() {
            super(org.embulk.spi.time.Timestamp.class);
        }

        @Override
        protected org.embulk.spi.time.Timestamp _deserialize(String value, DeserializationContext context) {
            if (value == null) {
                throw DatabindException.from(context, "TimestampDeserializer#_deserialize received null unexpectedly.");
            }
            try {
                return org.embulk.spi.time.Timestamp.ofString(value);
            } catch (final NumberFormatException ex) {
                throw DatabindException.from(context, "Invalid format as a Timestamp value: '" + value + "'", ex);
            } catch (final IllegalStateException ex) {
                throw DatabindException.from(context, "Unexpected failure in parsing: '" + value + "'", ex);
            }
        }
    }
}
