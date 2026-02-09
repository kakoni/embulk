package org.embulk.deps.config;

import org.embulk.spi.type.Type;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.module.SimpleModule;

public final class TypeJacksonModule extends SimpleModule {
    public TypeJacksonModule() {
        this.addSerializer(Type.class, new TypeSerializer());
        this.addDeserializer(Type.class, new TypeDeserializer());
    }

    private static class TypeSerializer extends ValueSerializer<Type> {
        @Override
        public void serialize(
                final Type value,
                final JsonGenerator jsonGenerator,
                final SerializationContext provider) {
            jsonGenerator.writeString(value.getName());
        }
    }
}
