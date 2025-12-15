package org.embulk.deps.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.embulk.spi.unit.ToStringMap;
import org.junit.Test;
import tools.jackson.databind.DatabindException;

public class TestToStringMap {
    @Test
    public void test() throws IOException {
        assertMappingException("\"foo\"");

        final HashMap<String, String> m1 = new HashMap<>();
        m1.put("foo", "000abc");
        m1.put("bar", "null");
        m1.put("baz", "12");
        m1.put("qux", "true");
        assertToStringMap(m1, "{\"foo\": \"000abc\", \"bar\": null, \"baz\": 12, \"qux\": true}");

        // Handling of null (JSON null) is different between ToStringMap and Map<String, String>.
        // ToStringMap:        maps into "null".
        // Map<String, String: maps into null.
        assertWithMap("{\"foo\": \"000abc\", \"baz\": 12, \"qux\": true}");

        assertMappingException("{\"foo\": [ \"something\" ]}");
        assertMappingException("{\"foo\": { \"some\": \"thing\" }}");
        assertMappingException("\"foo\": 000123");
    }

    @SuppressWarnings("deprecation")
    private static void assertMappingException(final String inputJson) throws IOException {
        try {
            MAPPER.readValue(inputJson, ToStringMap.class);
        } catch (final DatabindException ex) {
            return;
        }
        fail("JsonMappingException is expected.");
    }

    @SuppressWarnings("deprecation")
    private static void assertToStringMap(final Map<String, String> expected, final String inputJson) throws IOException {
        final ToStringMap toStringMap = MAPPER.readValue(inputJson, ToStringMap.class);
        assertEquals(expected, toStringMap);
    }

    @SuppressWarnings("deprecation")
    private static void assertWithMap(final String inputJson) throws IOException {
        final ToStringMap toStringMap = MAPPER.readValue(inputJson, ToStringMap.class);
        final HashMap<String, String> map = MAPPER.readValue(inputJson, new TypeReference<HashMap<String, String>>() {});
        assertEquals(map, toStringMap);
    }

    private static final ObjectMapper MAPPER;

    static {
        MAPPER = JsonMapper.builder()
                .addModule(new ToStringJacksonModule())
                .addModule(new ToStringMapJacksonModule())
                .build();
    }
}
