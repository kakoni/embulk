package org.embulk.deps.config;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.embulk.spi.unit.ToStringMap;
import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.ValueDeserializer;

@Deprecated
public final class ToStringMapJacksonModule extends SimpleModule {
    public ToStringMapJacksonModule() {
        this.addDeserializer(ToStringMap.class, new ToStringMapDeserializer());
    }

    private static class ToStringMapDeserializer extends ValueDeserializer<ToStringMap> {
        @Override
        public ToStringMap deserialize(
                final JsonParser jsonParser,
                final DeserializationContext context)
                throws DatabindException {
            final JsonNode jsonNode;
            try {
                jsonNode = OBJECT_MAPPER.readTree(jsonParser);
            } catch (final StreamReadException ex) {
                throw DatabindException.from(jsonParser, "Failed to parse JSON.", ex);
            } catch (final JacksonException ex) {
                throw DatabindException.from(jsonParser, "Failed to process JSON in parsing.", ex);
            }

            if (jsonNode == null || !jsonNode.isObject()) {
                throw DatabindException.from(jsonParser, "ToStringMap expects a JSON object node.");
            }
            final ObjectNode node = (ObjectNode) jsonNode;

            final HashMap<String, String> built = new HashMap<String, String>();
            for (final Map.Entry<String, JsonNode> entry : node.properties()) {
                final JsonNode value = entry.getValue();
                if (value == null || value.isNull()) {
                    built.put(entry.getKey(), "null");
                } else {
                    built.put(entry.getKey(), ToStringJacksonModule.jsonNodeToString(value, jsonParser));
                }
            }
            return ToStringMap.of(Collections.unmodifiableMap(built));
        }

        private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }
}
