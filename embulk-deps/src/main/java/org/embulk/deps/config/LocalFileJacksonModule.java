package org.embulk.deps.config;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.embulk.spi.unit.LocalFile;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

public final class LocalFileJacksonModule extends SimpleModule {
    public LocalFileJacksonModule() {
        this.addSerializer(LocalFile.class, new LocalFileSerializer());
        this.addDeserializer(LocalFile.class, new LocalFileDeserializer());
    }

    private static class LocalFileSerializer extends ValueSerializer<LocalFile> {
        @Override
        public void serialize(LocalFile value, JsonGenerator jgen, SerializationContext provider) {
            jgen.writeStartObject();
            jgen.writeName("base64");
            jgen.writeBinary(value.getContent());
            jgen.writeEndObject();
        }
    }

    private static class LocalFileDeserializer extends ValueDeserializer<LocalFile> {
        @Override
        public LocalFile deserialize(JsonParser jp, DeserializationContext ctxt) {
            JsonToken t = jp.currentToken();
            if (t == JsonToken.START_OBJECT) {
                t = jp.nextToken();
            }

            switch (t) {
                case VALUE_NULL:
                    return null;

                case PROPERTY_NAME: {
                    LocalFile result = null;

                    final String keyName = jp.currentName();
                    if ("content".equals(keyName)) {
                        jp.nextToken();
                        result = LocalFile.ofContent(jp.getValueAsString());
                    } else if ("base64".equals(keyName)) {
                        jp.nextToken();
                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                        jp.readBinaryValue(ctxt.getBase64Variant(), out);
                        result = LocalFile.ofContent(out.toByteArray());
                    } else {
                        ctxt.handleUnknownProperty(jp, this, LocalFile.class, keyName);
                        throw DatabindException.from(jp, "Unknown property for LocalFile: " + keyName);
                    }

                    t = jp.nextToken();
                    if (t != JsonToken.END_OBJECT) {
                        throw DatabindException.from(jp, "Unexpected extra map keys to LocalFile");
                    }
                    return result;
                }

                case END_OBJECT:
                case START_ARRAY:
                case END_ARRAY:
                    throw DatabindException.from(jp, "Attempted unexpected map or array to LocalFile");

                case VALUE_EMBEDDED_OBJECT: {
                    final Object obj = jp.getEmbeddedObject();
                    if (obj == null) {
                        return null;
                    }
                    if (LocalFile.class.isAssignableFrom(obj.getClass())) {
                        return (LocalFile) obj;
                    }

                    throw DatabindException.from(
                            jp,
                            "Don't know how to convert embedded Object of type " + obj.getClass().getName() + " into LocalFile");
                }

                default:
                    try {
                        return LocalFile.of(jp.getValueAsString());
                    } catch (final IOException ex) {
                        throw DatabindException.from(jp, "Failed to read LocalFile content", ex);
                    }
            }
        }
    }
}
