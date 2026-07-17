package com.centralbrain.runtime.graph;

import com.centralbrain.runtime.graph.NodeExecutionInput.ApprovalInput;
import com.centralbrain.runtime.graph.NodeExecutionInput.CompensationInput;
import com.centralbrain.runtime.graph.NodeExecutionInput.ContextInput;
import com.centralbrain.runtime.graph.NodeExecutionInput.DigestOnlyInput;
import com.centralbrain.runtime.graph.NodeExecutionInput.EffectInput;
import com.centralbrain.runtime.graph.NodeExecutionInput.PolicyInput;
import com.centralbrain.runtime.graph.NodeExecutionInput.SummaryInput;
import com.centralbrain.runtime.graph.NodeExecutionInput.VerificationInput;
import com.centralbrain.runtime.graph.NodeExecutionOutput.ApprovalOutput;
import com.centralbrain.runtime.graph.NodeExecutionOutput.CompensationOutput;
import com.centralbrain.runtime.graph.NodeExecutionOutput.ContextOutput;
import com.centralbrain.runtime.graph.NodeExecutionOutput.DigestOnlyOutput;
import com.centralbrain.runtime.graph.NodeExecutionOutput.EffectOutput;
import com.centralbrain.runtime.graph.NodeExecutionOutput.PolicyOutput;
import com.centralbrain.runtime.graph.NodeExecutionOutput.SummaryOutput;
import com.centralbrain.runtime.graph.NodeExecutionOutput.VerificationOutput;
import com.centralbrain.sdk.plan.PlanContract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Exact input/output class allowlist for every PlanContract node type. */
public final class NodeExecutionSchemas {
    public static final class Schema {
        private final String nodeType;
        private final String inputSchemaId;
        private final String outputSchemaId;
        private final Class<? extends NodeExecutionInput> inputType;
        private final Class<? extends NodeExecutionOutput> outputType;
        private final boolean debugExecutorAvailable;

        private Schema(
                String nodeType,
                String inputSchemaId,
                String outputSchemaId,
                Class<? extends NodeExecutionInput> inputType,
                Class<? extends NodeExecutionOutput> outputType,
                boolean debugExecutorAvailable) {
            this.nodeType = NodeExecutionContract.requireNodeType(nodeType);
            this.inputSchemaId = NodeExecutionContract.requireSchemaId(
                    inputSchemaId, "inputSchemaId");
            this.outputSchemaId = NodeExecutionContract.requireSchemaId(
                    outputSchemaId, "outputSchemaId");
            this.inputType = inputType;
            this.outputType = outputType;
            this.debugExecutorAvailable = debugExecutorAvailable;
        }

        public String getNodeType() {
            return nodeType;
        }

        public String getInputSchemaId() {
            return inputSchemaId;
        }

        public String getOutputSchemaId() {
            return outputSchemaId;
        }

        public Class<? extends NodeExecutionInput> getInputType() {
            return inputType;
        }

        public Class<? extends NodeExecutionOutput> getOutputType() {
            return outputType;
        }

        public boolean isDebugExecutorAvailable() {
            return debugExecutorAvailable;
        }
    }

    private static final Map<String, Schema> SCHEMAS;

    static {
        Map<String, Schema> schemas = new LinkedHashMap<>();
        register(schemas, "approval.interrupt", "node.input.approval.v1",
                "node.output.approval.v1", ApprovalInput.class, ApprovalOutput.class, true);
        register(schemas, "compensate", "node.input.compensation.v1",
                "node.output.compensation.v1", CompensationInput.class,
                CompensationOutput.class, true);
        register(schemas, "context.capture", "node.input.context.v1",
                "node.output.context.v1", ContextInput.class, ContextOutput.class, true);
        register(schemas, "effect.execute", "node.input.effect.v1",
                "node.output.effect.v1", EffectInput.class, EffectOutput.class, true);
        register(schemas, "effect.verify", "node.input.verification.v1",
                "node.output.verification.v1", VerificationInput.class,
                VerificationOutput.class, true);
        register(schemas, "memory.query", "node.input.digest-only.v1",
                "node.output.digest-only.v1", DigestOnlyInput.class,
                DigestOnlyOutput.class, false);
        register(schemas, "memory.write", "node.input.digest-only.v1",
                "node.output.digest-only.v1", DigestOnlyInput.class,
                DigestOnlyOutput.class, false);
        register(schemas, "model.invoke", "node.input.digest-only.v1",
                "node.output.digest-only.v1", DigestOnlyInput.class,
                DigestOnlyOutput.class, false);
        register(schemas, "policy.evaluate", "node.input.policy.v1",
                "node.output.policy.v1", PolicyInput.class, PolicyOutput.class, true);
        register(schemas, "summary.render", "node.input.summary.v1",
                "node.output.summary.v1", SummaryInput.class, SummaryOutput.class, true);
        register(schemas, "tool.invoke", "node.input.digest-only.v1",
                "node.output.digest-only.v1", DigestOnlyInput.class,
                DigestOnlyOutput.class, false);
        if (!schemas.keySet().equals(new LinkedHashSet<>(PlanContract.allowedNodeTypes()))) {
            throw new IllegalStateException("Node execution schemas do not cover the Plan contract");
        }
        SCHEMAS = Collections.unmodifiableMap(schemas);
    }

    private NodeExecutionSchemas() {}

    public static Schema schemaFor(String nodeType) {
        Schema schema = SCHEMAS.get(nodeType);
        if (schema == null) {
            throw NodeExecutionContract.violation("no schema for nodeType");
        }
        return schema;
    }

    public static Set<String> nodeTypes() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(SCHEMAS.keySet()));
    }

    public static Collection<Schema> all() {
        return Collections.unmodifiableList(new ArrayList<>(SCHEMAS.values()));
    }

    public static void validateInput(String nodeType, NodeExecutionInput input) {
        Schema schema = schemaFor(nodeType);
        if (input == null
                || input.getClass() != schema.inputType
                || !input.getNodeType().equals(nodeType)
                || !input.getSchemaId().equals(schema.inputSchemaId)) {
            throw NodeExecutionContract.violation("input does not match the exact node schema");
        }
    }

    public static void validateResult(String nodeType, NodeExecutionResult<?> result) {
        Schema schema = schemaFor(nodeType);
        if (result == null
                || result.getOutput().getClass() != schema.outputType
                || !result.getOutput().getNodeType().equals(nodeType)
                || !result.getOutput().getSchemaId().equals(schema.outputSchemaId)) {
            throw NodeExecutionContract.violation("result does not match the exact node schema");
        }
    }

    private static void register(
            Map<String, Schema> schemas,
            String nodeType,
            String inputSchemaId,
            String outputSchemaId,
            Class<? extends NodeExecutionInput> inputType,
            Class<? extends NodeExecutionOutput> outputType,
            boolean debugExecutorAvailable) {
        Schema previous = schemas.put(
                nodeType,
                new Schema(
                        nodeType,
                        inputSchemaId,
                        outputSchemaId,
                        inputType,
                        outputType,
                        debugExecutorAvailable));
        if (previous != null) {
            throw new IllegalStateException("duplicate node execution schema");
        }
    }
}
