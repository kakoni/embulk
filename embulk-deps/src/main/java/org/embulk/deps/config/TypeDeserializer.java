package org.embulk.deps.config;

import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.FromStringDeserializer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.embulk.spi.type.Type;
import org.embulk.spi.type.Types;
import tools.jackson.databind.DatabindException;

class TypeDeserializer extends FromStringDeserializer<Type> {
    private static final Map<String, Type> stringToTypeMap;

    static {
        final LinkedHashMap<String, Type> builder = new LinkedHashMap<>();
        builder.put(Types.BOOLEAN.getName(), Types.BOOLEAN);
        builder.put(Types.LONG.getName(), Types.LONG);
        builder.put(Types.DOUBLE.getName(), Types.DOUBLE);
        builder.put(Types.STRING.getName(), Types.STRING);
        builder.put(Types.TIMESTAMP.getName(), Types.TIMESTAMP);
        builder.put(Types.JSON.getName(), Types.JSON);
        stringToTypeMap = Collections.unmodifiableMap(builder);
    }

    public TypeDeserializer() {
        super(Type.class);
    }

    @Override
    protected Type _deserialize(String value, DeserializationContext context) {
        Type t = stringToTypeMap.get(value);
        if (t == null) {
            throw DatabindException.from(
                    context,
                    String.format("Unknown type name '%s'. Supported types are: %s",
                                  value,
                                  String.join(", ", stringToTypeMap.keySet())));
        }
        return t;
    }
}
