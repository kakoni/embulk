package org.embulk.deps.config;

import java.util.ArrayList;
import java.util.Collections;
import org.embulk.config.ConfigException;
import org.embulk.config.TaskSource;
import org.embulk.plugin.PluginType;
import org.embulk.spi.ProcessTask;
import org.embulk.spi.Schema;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

final class ProcessTaskJacksonModule extends SimpleModule {
    public ProcessTaskJacksonModule(final ModelManagerDelegateImpl model) {
        this.addSerializer(ProcessTask.class, new ProcessTaskSerializer(model));
        this.addDeserializer(ProcessTask.class, new ProcessTaskDeserializer(model));
    }

    private static class ProcessTaskSerializer extends ValueSerializer<ProcessTask> {
        ProcessTaskSerializer(final ModelManagerDelegateImpl model) {
            this.model = model;
        }

        @Override
        public void serialize(
                final ProcessTask value,
                final JsonGenerator jsonGenerator,
                final SerializationContext provider) {
            final ObjectNode object = OBJECT_MAPPER.createObjectNode();
            object.set("inputType", this.model.writeObjectAsObjectNode(value.getInputPluginType()));
            object.set("outputType", this.model.writeObjectAsObjectNode(value.getOutputPluginType()));
            object.set("filterTypes", this.model.writeObjectAsObjectNode(value.getFilterPluginTypes()));
            object.set("inputTask", this.model.writeObjectAsObjectNode(value.getInputTaskSource()));
            object.set("outputTask", this.model.writeObjectAsObjectNode(value.getOutputTaskSource()));
            object.set("filterTasks", this.model.writeObjectAsObjectNode(value.getFilterTaskSources()));
            object.set("schemas", this.model.writeObjectAsObjectNode(value.getFilterSchemas()));
            object.set("executorSchema", this.model.writeObjectAsObjectNode(value.getExecutorSchema()));
            object.set("executorTask", this.model.writeObjectAsObjectNode(value.getExecutorTaskSource()));
            jsonGenerator.writeTree(object);
        }

        private final ModelManagerDelegateImpl model;
    }

    private static class ProcessTaskDeserializer extends ValueDeserializer<ProcessTask> {
        ProcessTaskDeserializer(final ModelManagerDelegateImpl model) {
            this.model = model;
        }

        @Override
        public ProcessTask deserialize(
                final JsonParser jsonParser,
                final DeserializationContext context)
                throws DatabindException {
            final JsonNode node;
            try {
                node = OBJECT_MAPPER.readTree(jsonParser);
            } catch (final StreamReadException ex) {
                throw DatabindException.from(jsonParser, "Failed to parse JSON.", ex);
            } catch (final JacksonException ex) {
                throw DatabindException.from(jsonParser, "Failed to process JSON in parsing.", ex);
            }

            if (!node.isObject()) {
                throw DatabindException.from(jsonParser, "Expected object to deserialize ProcessTask");
            }

            final ObjectNode object = (ObjectNode) node;
            final ObjectReadContext readContext = ObjectReadContext.empty();

            try {
                final PluginType inputPluginType =
                        this.model.readObject(PluginType.class, object.get("inputType").traverse(readContext));
                final PluginType outputPluginType =
                        this.model.readObject(PluginType.class, object.get("outputType").traverse(readContext));

                final JsonNode filterPluginTypesNode = object.get("filterTypes");
                if (!filterPluginTypesNode.isArray()) {
                    throw DatabindException.from(jsonParser, "An array is expected for ProcessTask's filterTypes");
                }
                final ArrayList<PluginType> filterPluginTypes = new ArrayList<>();
                for (final JsonNode filterPluginTypeNode : (ArrayNode) filterPluginTypesNode) {
                    if (filterPluginTypeNode == null || filterPluginTypeNode.isNull()) {
                        filterPluginTypes.add(null);
                    } else {
                        filterPluginTypes.add(this.model.readObject(PluginType.class, filterPluginTypeNode.traverse(readContext)));
                    }
                }

                final TaskSource inputTaskSource =
                        this.model.readObject(TaskSource.class, object.get("inputTask").traverse(readContext));
                final TaskSource outputTaskSource =
                        this.model.readObject(TaskSource.class, object.get("outputTask").traverse(readContext));

                final JsonNode filterTaskSourcesNode = object.get("filterTasks");
                if (!filterTaskSourcesNode.isArray()) {
                    throw DatabindException.from(jsonParser, "An array is expected for ProcessTask's filterTasks");
                }
                final ArrayList<TaskSource> filterTaskSources = new ArrayList<>();
                for (final JsonNode filterTaskSourceNode : (ArrayNode) filterTaskSourcesNode) {
                    if (filterTaskSourceNode == null || filterTaskSourceNode.isNull()) {
                        filterTaskSources.add(null);
                    } else {
                        filterTaskSources.add(this.model.readObject(TaskSource.class, filterTaskSourceNode.traverse(readContext)));
                    }
                }

                final JsonNode schemasNode = object.get("schemas");
                if (!schemasNode.isArray()) {
                    throw DatabindException.from(jsonParser, "An array is expected for ProcessTask's schemas");
                }
                final ArrayList<Schema> schemas = new ArrayList<>();
                for (final JsonNode schemaNode : (ArrayNode) schemasNode) {
                    if (schemaNode == null || schemaNode.isNull()) {
                        schemas.add(null);
                    } else {
                        schemas.add(this.model.readObject(Schema.class, schemaNode.traverse(readContext)));
                    }
                }

                final Schema executorSchema =
                        this.model.readObject(Schema.class, object.get("executorSchema").traverse(readContext));
                final TaskSource executorTaskSource =
                        this.model.readObject(TaskSource.class, object.get("executorTask").traverse(readContext));

                return new ProcessTask(
                        inputPluginType,
                        outputPluginType,
                        Collections.unmodifiableList(filterPluginTypes),
                        inputTaskSource,
                        outputTaskSource,
                        Collections.unmodifiableList(filterTaskSources),
                        Collections.unmodifiableList(schemas),
                        executorSchema,
                        executorTaskSource);
            } catch (final ConfigException ex) {
                throw DatabindException.from(jsonParser, "Invalid object to deserialize ProcessTask", ex);
            }
        }

        private final ModelManagerDelegateImpl model;
    }

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
}
