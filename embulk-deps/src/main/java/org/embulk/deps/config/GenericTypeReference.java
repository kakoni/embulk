package org.embulk.deps.config;

import java.lang.reflect.Type;
import tools.jackson.core.type.TypeReference;

class GenericTypeReference extends TypeReference<Object> {
    private final Type type;

    public GenericTypeReference(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }
}
