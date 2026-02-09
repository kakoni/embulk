package org.embulk.deps.config;

import java.nio.charset.Charset;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.std.FromStringDeserializer;
import tools.jackson.databind.module.SimpleModule;

public final class CharsetJacksonModule extends SimpleModule {
    public CharsetJacksonModule() {
        this.addSerializer(Charset.class, new CharsetSerializer());
        this.addDeserializer(Charset.class, new CharsetDeserializer());
    }

    private static class CharsetSerializer extends ValueSerializer<Charset> {
        @Override
        public void serialize(Charset value, JsonGenerator jgen, SerializationContext provider) {
            jgen.writeString(value.name());
        }
    }

    private static class CharsetDeserializer extends FromStringDeserializer<Charset> {
        public CharsetDeserializer() {
            super(Charset.class);
        }

        @Override
        protected Charset _deserialize(String value, DeserializationContext context) {
            try {
                return Charset.forName(value);
            } catch (UnsupportedOperationException ex) {
                // TODO include link to a document to the message for the list of supported time zones
                throw DatabindException.from(context, String.format("Unknown charset '%s'", value));
            }
        }
    }
}
