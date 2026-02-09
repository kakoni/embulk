package org.embulk.deps.config;

import org.embulk.config.ConfigDiff;
import org.embulk.config.ConfigException;
import org.embulk.config.ConfigSource;
import org.embulk.config.DataSource;
import org.embulk.config.ModelManagerDelegate;
import org.embulk.config.TaskReport;
import org.embulk.config.TaskSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

public class ModelManagerDelegateImpl extends ModelManagerDelegate {
    private final Logger logger = LoggerFactory.getLogger(ModelManagerDelegateImpl.class);

    private final ObjectMapper objectMapper;
    private final ObjectMapper configObjectMapper;  // configObjectMapper uses different TaskDeserializer

    public ModelManagerDelegateImpl() {
        final JsonMapper baseMapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .addModule(new ColumnConfigJacksonModule(this))
                .addModule(new SchemaConfigJacksonModule(this))
                .addModule(new PluginTypeJacksonModule())
                .addModule(new ProcessTaskJacksonModule(this))
                .addModule(new ResumeStateJacksonModule(this))
                .addModule(new TimestampJacksonModule())  // Deprecated. TBD to remove or not.
                .addModule(new TimestampFormatJacksonModule())
                .addModule(new ByteSizeJacksonModule())
                .addModule(new CharsetJacksonModule())
                .addModule(new LocalFileJacksonModule())
                .addModule(new ToStringJacksonModule())
                .addModule(new ToStringMapJacksonModule())
                .addModule(new TypeJacksonModule())
                .addModule(new ColumnJacksonModule())
                .addModule(new SchemaJacksonModule())  // jackson-datatype-jdk8
                .addModule(new DataSourceSerDe.SerDeModule(this))
                .build();

        this.objectMapper = baseMapper.rebuild()
                .addModule(new TaskSerDe.TaskSerializerModule(baseMapper))
                .addModule(new TaskSerDe.TaskDeserializerModule(baseMapper, this))
                .build();

        this.configObjectMapper = baseMapper.rebuild()
                .addModule(new TaskSerDe.TaskSerializerModule(baseMapper))
                .addModule(new TaskSerDe.ConfigTaskDeserializerModule(baseMapper, this))
                .build();
    }

    @Override
    public <T> T readObject(Class<T> valueType, String json) {
        try {
            return objectMapper.readValue(json, valueType);
        } catch (Exception ex) {
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new RuntimeException(ex);
        }
    }

    <T> T readObject(Class<T> valueType, JsonParser parser) {
        try {
            return objectMapper.readValue(parser, valueType);
        } catch (Exception ex) {
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new RuntimeException(ex);
        }
    }

    @Override
    public <T> T readObjectWithConfigSerDe(Class<T> valueType, String json) {
        T t;
        try {
            t = configObjectMapper.readValue(json, valueType);
        } catch (Exception ex) {
            if (ex instanceof ConfigException) {
                throw (ConfigException) ex;
            }
            throw new ConfigException(ex);
        }
        return t;
    }

    <T> T readObjectWithConfigSerDe(Class<T> valueType, JsonParser parser) {
        T t;
        try {
            t = configObjectMapper.readValue(parser, valueType);
        } catch (Exception ex) {
            if (ex instanceof ConfigException) {
                throw (ConfigException) ex;
            }
            throw new ConfigException(ex);
        }
        return t;
    }

    @Override
    public DataSource readObjectAsDataSource(final String json) {
        try {
            return objectMapper.readValue(json, DataSourceImpl.class);
        } catch (Exception ex) {
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new RuntimeException(ex);
        }
    }

    @Override
    public String writeObject(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception ex) {
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void validate(Object object) {
        logger.warn(
                "ModelManager#validate is no longer available.",
                new UnsupportedOperationException("ModelManager#validate is no longer available."));
    }

    @Override
    public TaskReport newTaskReport() {
        return new DataSourceImpl(this);
    }

    @Override
    public ConfigDiff newConfigDiff() {
        return new DataSourceImpl(this);
    }

    @Override
    public ConfigSource newConfigSource() {
        return new DataSourceImpl(this);
    }

    @Override
    public TaskSource newTaskSource() {
        return new DataSourceImpl(this);
    }

    JsonNode writeObjectAsJsonNode(Object v) {
        String json = writeObject(v);
        try {
            return objectMapper.readValue(json, JsonNode.class);
        } catch (Exception ex) {
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new RuntimeException(ex);
        }
    }

    ObjectNode writeObjectAsObjectNode(Object v) {
        String json = writeObject(v);
        try {
            return objectMapper.readValue(json, ObjectNode.class);
        } catch (Exception ex) {
            if (ex instanceof RuntimeException) {
                throw (RuntimeException) ex;
            }
            throw new RuntimeException(ex);
        }
    }
}
