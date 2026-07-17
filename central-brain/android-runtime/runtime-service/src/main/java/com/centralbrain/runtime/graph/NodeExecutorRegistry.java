package com.centralbrain.runtime.graph;

import com.centralbrain.sdk.plan.PlanContract;
import com.centralbrain.sdk.plan.PlanNode;
import com.centralbrain.sdk.plan.ScenarioPlan;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable node-type/schema registry. It validates contracts and never invokes an executor. */
public final class NodeExecutorRegistry {
    public static final class Registration {
        private final String nodeType;
        private final NodeExecutionSchemas.Schema schema;
        private final boolean dispatchEnabled;
        private final boolean productionAuthorized;

        private Registration(
                String nodeType,
                NodeExecutionSchemas.Schema schema,
                boolean dispatchEnabled,
                boolean productionAuthorized) {
            this.nodeType = nodeType;
            this.schema = schema;
            this.dispatchEnabled = dispatchEnabled;
            this.productionAuthorized = productionAuthorized;
        }

        public String getNodeType() {
            return nodeType;
        }

        public String getInputSchemaId() {
            return schema.getInputSchemaId();
        }

        public String getOutputSchemaId() {
            return schema.getOutputSchemaId();
        }

        public Class<? extends NodeExecutionInput> getInputType() {
            return schema.getInputType();
        }

        public Class<? extends NodeExecutionOutput> getOutputType() {
            return schema.getOutputType();
        }

        public boolean isDebugExecutorAvailable() {
            return schema.isDebugExecutorAvailable();
        }

        public boolean isDispatchEnabled() {
            return dispatchEnabled;
        }

        public boolean isProductionAuthorized() {
            return productionAuthorized;
        }
    }

    private final Map<String, Registration> registrations;
    private final String digest;

    private NodeExecutorRegistry(Map<String, Registration> registrations) {
        this.registrations = Collections.unmodifiableMap(
                new LinkedHashMap<>(registrations));
        this.digest = digest(registrations.values());
    }

    public static NodeExecutorRegistry controlOnlyContractRegistry() {
        return controlOnly(PlanContract.allowedNodeTypes());
    }

    public static NodeExecutorRegistry controlOnly(Set<String> nodeTypes) {
        Objects.requireNonNull(nodeTypes, "nodeTypes");
        List<String> ordered = new ArrayList<>(new LinkedHashSet<>(nodeTypes));
        ordered.sort(String::compareTo);
        Map<String, Registration> registrations = new LinkedHashMap<>();
        Set<String> allowed = PlanContract.allowedNodeTypes();
        for (String nodeType : ordered) {
            if (nodeType == null || !allowed.contains(nodeType)) {
                throw violation("node type is not in the Plan contract");
            }
            registrations.put(
                    nodeType,
                    new Registration(
                            nodeType,
                            NodeExecutionSchemas.schemaFor(nodeType),
                            false,
                            false));
        }
        if (registrations.isEmpty()) {
            throw violation("at least one node type is required");
        }
        return new NodeExecutorRegistry(registrations);
    }

    public void validatePlan(ScenarioPlan plan) {
        Objects.requireNonNull(plan, "plan");
        for (PlanNode node : plan.nodes) {
            Registration registration = registrations.get(node.nodeType);
            if (registration == null) {
                throw violation("no registration for node type " + node.nodeType);
            }
            if (registration.dispatchEnabled || registration.productionAuthorized) {
                throw violation("registry must remain control-only");
            }
        }
    }

    public boolean contains(String nodeType) {
        return registrations.containsKey(nodeType);
    }

    public Registration registrationFor(String nodeType) {
        Registration registration = registrations.get(nodeType);
        if (registration == null) {
            throw violation("no registration for node type " + nodeType);
        }
        return registration;
    }

    public void validateInput(String nodeType, NodeExecutionInput input) {
        registrationFor(nodeType);
        NodeExecutionSchemas.validateInput(nodeType, input);
    }

    public void validateResult(String nodeType, NodeExecutionResult<?> result) {
        registrationFor(nodeType);
        NodeExecutionSchemas.validateResult(nodeType, result);
    }

    public void validateExecutor(TypedNodeExecutor<?, ?> executor) {
        Objects.requireNonNull(executor, "executor");
        Registration registration = registrationFor(executor.nodeType());
        if (executor.inputType() != registration.getInputType()
                || executor.outputType() != registration.getOutputType()) {
            throw violation("executor exact input/output type does not match registration");
        }
        if (!registration.isDebugExecutorAvailable()) {
            throw violation("executor implementation is unavailable for this work package");
        }
        if (executor.isProductionAuthorized()
                || executor.mayDispatchEffect()
                || executor.mayInvokeModel()
                || executor.mayAccessNetwork()
                || executor.mayAccessHardware()
                || executor.mayPersistRawData()) {
            throw violation("executor requests a forbidden P3-W02 capability");
        }
    }

    public int size() {
        return registrations.size();
    }

    public Set<String> nodeTypes() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(registrations.keySet()));
    }

    public String getDigest() {
        return digest;
    }

    public boolean isDispatchEnabled() {
        return false;
    }

    public boolean isProductionAuthorized() {
        return false;
    }

    private static String digest(Iterable<Registration> registrations) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Registration registration : registrations) {
                update(digest, registration.nodeType);
                update(digest, registration.getInputSchemaId());
                update(digest, registration.getOutputSchemaId());
                update(digest, Boolean.toString(registration.isDebugExecutorAvailable()));
                update(digest, Boolean.toString(registration.dispatchEnabled));
                update(digest, Boolean.toString(registration.productionAuthorized));
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 0xf, 16));
            result.append(Character.forDigit(value & 0xf, 16));
        }
        return result.toString();
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_GRAPH_REGISTRY: " + message);
    }
}
