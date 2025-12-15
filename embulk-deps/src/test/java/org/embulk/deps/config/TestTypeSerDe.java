package org.embulk.deps.config;

import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import org.embulk.deps.config.TypeJacksonModule;
import org.embulk.spi.type.Type;
import org.embulk.spi.type.Types;
import org.junit.Test;

public class TestTypeSerDe {

    private static class HasType {
        private Type type;
        // TODO test TimestampType

        @JsonCreator
        public HasType(
                @JsonProperty("type") Type type) {
            this.type = type;
        }

        @JsonProperty("type")
        public Type getType() {
            return type;
        }
    }

    @Test
    public void testGetType() throws IOException {
        HasType type = new HasType(Types.STRING);
        String json = MAPPER.writeValueAsString(type);
        HasType decoded = MAPPER.readValue(json, HasType.class);
        assertTrue(Types.STRING == decoded.getType());
    }

    private static final ObjectMapper MAPPER;

    static {
        MAPPER = JsonMapper.builder()
                .addModule(new TypeJacksonModule())
                .build();
    }
}
