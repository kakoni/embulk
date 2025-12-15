package org.embulk.deps.config;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;
import org.embulk.config.ConfigException;
import org.embulk.config.ConfigSource;
import org.embulk.config.TaskReport;
import org.embulk.config.TaskSource;
import org.embulk.exec.ResumeState;
import org.embulk.spi.Schema;
import tools.jackson.core.JacksonException;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;

final class ResumeStateJacksonModule extends SimpleModule {
    public ResumeStateJacksonModule(final ModelManagerDelegateImpl model) {
        this.addSerializer(ResumeState.class, new ResumeStateSerializer(model));
        this.addDeserializer(ResumeState.class, new ResumeStateDeserializer(model));
    }

    private static class ResumeStateSerializer extends ValueSerializer<ResumeState> {
        ResumeStateSerializer(final ModelManagerDelegateImpl model) {
            this.model = model;
        }

        @Override
        public void serialize(
                final ResumeState value,
                final JsonGenerator jsonGenerator,
                final SerializationContext provider) {
            final ObjectNode object = OBJECT_MAPPER.createObjectNode();
            object.set("exec_task", this.model.writeObjectAsObjectNode(value.getExecSessionConfigSource()));
            object.set("in_task", this.model.writeObjectAsObjectNode(value.getInputTaskSource()));
            object.set("out_task", this.model.writeObjectAsObjectNode(value.getOutputTaskSource()));
            object.set("in_schema", this.model.writeObjectAsObjectNode(value.getInputSchema()));
            object.set("out_schema", this.model.writeObjectAsObjectNode(value.getOutputSchema()));
            object.set("in_reports", this.model.writeObjectAsObjectNode(value.getInputTaskReports()));
            object.set("out_reports", this.model.writeObjectAsObjectNode(value.getOutputTaskReports()));
            jsonGenerator.writeTree(object);
        }

        private final ModelManagerDelegateImpl model;
    }

    private static class ResumeStateDeserializer extends ValueDeserializer<ResumeState> {
        ResumeStateDeserializer(final ModelManagerDelegateImpl model) {
            this.model = model;
        }

        @Override
        public ResumeState deserialize(
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
                throw DatabindException.from(jsonParser, "Expected object to deserialize ResumeState");
            }

            final ObjectNode object = (ObjectNode) node;
            final ObjectReadContext readContext = ObjectReadContext.empty();

            try {
                final ConfigSource execSessionConfigSource =
                        this.model.readObject(ConfigSource.class, object.get("exec_task").traverse(readContext));
                final TaskSource inputTaskSource =
                        this.model.readObject(TaskSource.class, object.get("in_task").traverse(readContext));
                final TaskSource outputTaskSource =
                        this.model.readObject(TaskSource.class, object.get("out_task").traverse(readContext));
                final Schema inputSchema =
                        this.model.readObject(Schema.class, object.get("in_schema").traverse(readContext));
                final Schema outputSchema =
                        this.model.readObject(Schema.class, object.get("out_schema").traverse(readContext));

                final JsonNode inputTaskReportsNode = object.get("in_reports");
                if (!inputTaskReportsNode.isArray()) {
                    throw DatabindException.from(jsonParser, "An array is expected for ResumeState's in_reports");
                }
                final ArrayList<Optional<TaskReport>> inputTaskReports = new ArrayList<>();
                for (final JsonNode inputTaskReportNode : (ArrayNode) inputTaskReportsNode) {
                    if (inputTaskReportNode == null || inputTaskReportNode.isNull()) {
                        inputTaskReports.add(Optional.<TaskReport>empty());
                    } else {
                        inputTaskReports.add(Optional.of(this.model.readObject(TaskReport.class, inputTaskReportNode.traverse(readContext))));
                    }
                }

                final JsonNode outputTaskReportsNode = object.get("out_reports");
                if (!outputTaskReportsNode.isArray()) {
                    throw DatabindException.from(jsonParser, "An array is expected for ResumeState's out_reports");
                }
                final ArrayList<Optional<TaskReport>> outputTaskReports = new ArrayList<>();
                for (final JsonNode outputTaskReportNode : (ArrayNode) outputTaskReportsNode) {
                    if (outputTaskReportNode == null || outputTaskReportNode.isNull()) {
                        outputTaskReports.add(Optional.<TaskReport>empty());
                    } else {
                        outputTaskReports.add(Optional.of(this.model.readObject(TaskReport.class, outputTaskReportNode.traverse(readContext))));
                    }
                }

                return new ResumeState(
                        execSessionConfigSource,
                        inputTaskSource,
                        outputTaskSource,
                        inputSchema,
                        outputSchema,
                        Collections.unmodifiableList(inputTaskReports),
                        Collections.unmodifiableList(outputTaskReports));
            } catch (final ConfigException ex) {
                throw DatabindException.from(jsonParser, "Invalid object to deserialize ResumeState", ex);
            }
        }

        private final ModelManagerDelegateImpl model;
    }

    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();
}
