package com.centralbrain.runtime.scenario;

import com.centralbrain.runtime.vehicle.capability.VehicleCapability;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;
import com.centralbrain.sdk.plan.PlanContract;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable build-owned scenario metadata. It is a template, not an executable plan. */
public final class ScenarioManifest {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_NODES = 64;
    public static final int MAX_DEPENDENCIES = 256;
    public static final int MAX_CONTEXT_FIELDS = 16;
    public static final int MAX_CAPABILITIES = 16;

    private static final Pattern SCENARIO_ID =
            Pattern.compile("[a-z][a-z0-9]*(?:[.][a-z0-9][a-z0-9_-]*){2,7}");
    private static final Pattern LOCAL_ID =
            Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");
    private static final Pattern QUALIFIED_ID =
            Pattern.compile("[a-z][a-z0-9_-]*(?:[.][a-z0-9][a-z0-9_-]*){1,7}");
    private static final Pattern RESOURCE_KEY =
            Pattern.compile("[a-z][a-z0-9_.-]{2,95}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> CONTEXT_POLICIES = Set.of(
            "context.general.v1",
            "context.seat-comfort.v1",
            "context.seat-recline.v1");

    public enum Source {
        HMI_BUTTON,
        VOICE,
        TRIGGER,
        API
    }

    public enum Zone {
        ROW1_DRIVER,
        ROW1_PASSENGER,
        ROW2_LEFT,
        ROW2_RIGHT,
        CABIN
    }

    public enum RiskClass {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum DrivingPolicy {
        ANY,
        PARKED_ONLY
    }

    public enum FailureMode {
        FAIL_SCENARIO,
        SKIP_OPTIONAL,
        COMPENSATE
    }

    public enum DependencyCondition {
        ON_SUCCESS,
        ON_TERMINAL
    }

    public enum FallbackMode {
        FAIL_CLOSED,
        DEGRADED_OPTIONAL_ONLY
    }

    public static final class PolicyTemplate {
        private final String policyId;
        private final int policyVersion;
        private final RiskClass riskClass;
        private final DrivingPolicy drivingPolicy;
        private final boolean approvalRequired;
        private final FailureMode failureMode;

        PolicyTemplate(
                String policyId,
                int policyVersion,
                RiskClass riskClass,
                DrivingPolicy drivingPolicy,
                boolean approvalRequired,
                FailureMode failureMode) {
            this.policyId = requireIdentifier(
                    policyId, 96, QUALIFIED_ID, "node.policy.policyId");
            if (policyVersion < 1) {
                throw violation("node.policy.policyVersion must be positive");
            }
            this.policyVersion = policyVersion;
            this.riskClass = Objects.requireNonNull(riskClass, "riskClass");
            this.drivingPolicy = Objects.requireNonNull(drivingPolicy, "drivingPolicy");
            this.approvalRequired = approvalRequired;
            this.failureMode = Objects.requireNonNull(failureMode, "failureMode");
            if (riskClass == RiskClass.HIGH && !approvalRequired) {
                throw violation("HIGH node policy requires approval metadata");
            }
        }

        public String getPolicyId() {
            return policyId;
        }

        public int getPolicyVersion() {
            return policyVersion;
        }

        public RiskClass getRiskClass() {
            return riskClass;
        }

        public DrivingPolicy getDrivingPolicy() {
            return drivingPolicy;
        }

        public boolean isApprovalRequired() {
            return approvalRequired;
        }

        public FailureMode getFailureMode() {
            return failureMode;
        }
    }

    public static final class NodeTemplate {
        private final String nodeId;
        private final String nodeType;
        private final VehicleCapability.CapabilityId capabilityId;
        private final boolean required;
        private final long timeoutMs;
        private final int maxAttempts;
        private final String idempotencyKeyTemplate;
        private final String compensationNodeId;
        private final PolicyTemplate policy;

        NodeTemplate(
                String nodeId,
                String nodeType,
                VehicleCapability.CapabilityId capabilityId,
                boolean required,
                long timeoutMs,
                int maxAttempts,
                String idempotencyKeyTemplate,
                String compensationNodeId,
                PolicyTemplate policy) {
            this.nodeId = requireIdentifier(nodeId, 64, LOCAL_ID, "node.nodeId");
            this.nodeType = requireIdentifier(
                    nodeType, 48, QUALIFIED_ID, "node.nodeType");
            if (!PlanContract.allowedNodeTypes().contains(nodeType)) {
                throw violation("node.nodeType is not allowlisted");
            }
            this.capabilityId = capabilityId;
            this.required = required;
            if (timeoutMs < 1 || timeoutMs > PlanContract.MAX_NODE_TIMEOUT_MS) {
                throw violation("node.timeoutMs is outside the Plan contract");
            }
            this.timeoutMs = timeoutMs;
            if (maxAttempts < 1 || maxAttempts > PlanContract.MAX_ATTEMPTS) {
                throw violation("node.maxAttempts is outside the Plan contract");
            }
            this.maxAttempts = maxAttempts;
            this.idempotencyKeyTemplate = requireOptionalIdentifier(
                    idempotencyKeyTemplate,
                    128,
                    RESOURCE_KEY,
                    "node.idempotencyKeyTemplate");
            this.compensationNodeId = requireOptionalIdentifier(
                    compensationNodeId,
                    64,
                    LOCAL_ID,
                    "node.compensationNodeId");
            this.policy = Objects.requireNonNull(policy, "policy");
            boolean capabilityNode = "effect.execute".equals(nodeType)
                    || "effect.verify".equals(nodeType)
                    || "compensate".equals(nodeType);
            if (capabilityNode != (capabilityId != null)) {
                throw violation("effect/verify/compensate nodes require exactly one capability");
            }
            if ((maxAttempts > 1 || capabilityNode) && this.idempotencyKeyTemplate.isEmpty()) {
                throw violation("retryable or capability node requires idempotency template");
            }
            if (policy.failureMode == FailureMode.SKIP_OPTIONAL && required) {
                throw violation("required node cannot use SKIP_OPTIONAL");
            }
            if (policy.failureMode == FailureMode.COMPENSATE
                    && this.compensationNodeId.isEmpty()) {
                throw violation("COMPENSATE requires compensationNodeId");
            }
        }

        public String getNodeId() {
            return nodeId;
        }

        public String getNodeType() {
            return nodeType;
        }

        public VehicleCapability.CapabilityId getCapabilityId() {
            return capabilityId;
        }

        public boolean isRequired() {
            return required;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public String getIdempotencyKeyTemplate() {
            return idempotencyKeyTemplate;
        }

        public String getCompensationNodeId() {
            return compensationNodeId;
        }

        public PolicyTemplate getPolicy() {
            return policy;
        }
    }

    public static final class DependencyTemplate {
        private final String prerequisiteNodeId;
        private final String dependentNodeId;
        private final DependencyCondition condition;

        DependencyTemplate(
                String prerequisiteNodeId,
                String dependentNodeId,
                DependencyCondition condition) {
            this.prerequisiteNodeId = requireIdentifier(
                    prerequisiteNodeId, 64, LOCAL_ID, "dependency.prerequisiteNodeId");
            this.dependentNodeId = requireIdentifier(
                    dependentNodeId, 64, LOCAL_ID, "dependency.dependentNodeId");
            this.condition = Objects.requireNonNull(condition, "condition");
        }

        public String getPrerequisiteNodeId() {
            return prerequisiteNodeId;
        }

        public String getDependentNodeId() {
            return dependentNodeId;
        }

        public DependencyCondition getCondition() {
            return condition;
        }
    }

    public static final class PlanTemplate {
        private final List<NodeTemplate> nodes;
        private final List<DependencyTemplate> dependencies;

        PlanTemplate(List<NodeTemplate> nodes, List<DependencyTemplate> dependencies) {
            this.nodes = immutableBounded(nodes, 1, MAX_NODES, "planTemplate.nodes");
            this.dependencies = immutableBounded(
                    dependencies, 0, MAX_DEPENDENCIES, "planTemplate.dependencies");
        }

        public List<NodeTemplate> getNodes() {
            return nodes;
        }

        public List<DependencyTemplate> getDependencies() {
            return dependencies;
        }
    }

    public static final class FallbackPolicy {
        private final FallbackMode mode;
        private final String messageKey;
        private final List<String> optionalNodeIds;

        FallbackPolicy(FallbackMode mode, String messageKey, List<String> optionalNodeIds) {
            this.mode = Objects.requireNonNull(mode, "mode");
            this.messageKey = requireIdentifier(
                    messageKey, 96, RESOURCE_KEY, "fallback.messageKey");
            this.optionalNodeIds = immutableStrings(
                    optionalNodeIds, 16, 64, LOCAL_ID, "fallback.optionalNodeIds");
        }

        public FallbackMode getMode() {
            return mode;
        }

        public String getMessageKey() {
            return messageKey;
        }

        public List<String> getOptionalNodeIds() {
            return optionalNodeIds;
        }
    }

    public static final class UiMetadata {
        private final String displayKey;
        private final String descriptionKey;
        private final String iconKey;
        private final int sortOrder;

        UiMetadata(String displayKey, String descriptionKey, String iconKey, int sortOrder) {
            this.displayKey = requireIdentifier(
                    displayKey, 96, RESOURCE_KEY, "ui.displayKey");
            this.descriptionKey = requireIdentifier(
                    descriptionKey, 96, RESOURCE_KEY, "ui.descriptionKey");
            this.iconKey = requireIdentifier(iconKey, 64, RESOURCE_KEY, "ui.iconKey");
            if (sortOrder < 0 || sortOrder > 10_000) {
                throw violation("ui.sortOrder is invalid");
            }
            this.sortOrder = sortOrder;
        }

        public String getDisplayKey() {
            return displayKey;
        }

        public String getDescriptionKey() {
            return descriptionKey;
        }

        public String getIconKey() {
            return iconKey;
        }

        public int getSortOrder() {
            return sortOrder;
        }
    }

    private final int schemaVersion;
    private final String scenarioId;
    private final int version;
    private final String artifactDigest;
    private final Set<Source> supportedSources;
    private final Set<Zone> supportedZones;
    private final String contextPolicyId;
    private final List<VehicleSignalPath> requiredContext;
    private final List<VehicleSignalPath> optionalContext;
    private final List<VehicleCapability.CapabilityId> requiredCapabilities;
    private final List<VehicleCapability.CapabilityId> optionalCapabilities;
    private final RiskClass riskClass;
    private final PlanTemplate planTemplate;
    private final FallbackPolicy fallback;
    private final UiMetadata ui;

    ScenarioManifest(
            int schemaVersion,
            String scenarioId,
            int version,
            String artifactDigest,
            Set<Source> supportedSources,
            Set<Zone> supportedZones,
            String contextPolicyId,
            List<VehicleSignalPath> requiredContext,
            List<VehicleSignalPath> optionalContext,
            List<VehicleCapability.CapabilityId> requiredCapabilities,
            List<VehicleCapability.CapabilityId> optionalCapabilities,
            RiskClass riskClass,
            PlanTemplate planTemplate,
            FallbackPolicy fallback,
            UiMetadata ui) {
        if (schemaVersion != SCHEMA_VERSION) {
            throw violation("schemaVersion is unsupported");
        }
        this.schemaVersion = schemaVersion;
        this.scenarioId = requireIdentifier(scenarioId, 96, SCENARIO_ID, "scenarioId");
        if (version < 1) {
            throw violation("version must be positive");
        }
        this.version = version;
        if (artifactDigest == null || !SHA_256.matcher(artifactDigest).matches()) {
            throw violation("artifactDigest is invalid");
        }
        this.artifactDigest = artifactDigest;
        this.supportedSources = immutableSet(supportedSources, "supportedSources");
        this.supportedZones = immutableSet(supportedZones, "supportedZones");
        if (!CONTEXT_POLICIES.contains(contextPolicyId)) {
            throw violation("contextPolicyId is unknown");
        }
        this.contextPolicyId = contextPolicyId;
        this.requiredContext = immutableBounded(
                requiredContext, 1, MAX_CONTEXT_FIELDS, "requiredContext");
        this.optionalContext = immutableBounded(
                optionalContext, 0, MAX_CONTEXT_FIELDS, "optionalContext");
        ensureDisjoint(this.requiredContext, this.optionalContext, "context");
        this.requiredCapabilities = immutableBounded(
                requiredCapabilities, 1, MAX_CAPABILITIES, "requiredCapabilities");
        this.optionalCapabilities = immutableBounded(
                optionalCapabilities, 0, MAX_CAPABILITIES, "optionalCapabilities");
        ensureDisjoint(this.requiredCapabilities, this.optionalCapabilities, "capability");
        this.riskClass = Objects.requireNonNull(riskClass, "riskClass");
        this.planTemplate = Objects.requireNonNull(planTemplate, "planTemplate");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.ui = Objects.requireNonNull(ui, "ui");
        validatePlanTemplate();
    }

    private void validatePlanTemplate() {
        Map<String, NodeTemplate> byId = new HashMap<>();
        Set<VehicleCapability.CapabilityId> declared = new HashSet<>(requiredCapabilities);
        declared.addAll(optionalCapabilities);
        RiskClass maximumRisk = RiskClass.LOW;
        boolean hasContext = false;
        boolean hasSummary = false;
        boolean hasApproval = false;
        for (NodeTemplate node : planTemplate.nodes) {
            if (byId.put(node.nodeId, node) != null) {
                throw violation("planTemplate contains duplicate nodeId");
            }
            if (node.capabilityId != null && !declared.contains(node.capabilityId)) {
                throw violation("planTemplate node uses an undeclared capability");
            }
            maximumRisk = node.policy.riskClass.ordinal() > maximumRisk.ordinal()
                    ? node.policy.riskClass : maximumRisk;
            hasContext |= "context.capture".equals(node.nodeType);
            hasSummary |= "summary.render".equals(node.nodeType);
            hasApproval |= "approval.interrupt".equals(node.nodeType);
        }
        if (!hasContext || !hasSummary) {
            throw violation("planTemplate requires context.capture and summary.render");
        }
        if (maximumRisk != riskClass) {
            throw violation("scenario riskClass must equal maximum node risk");
        }
        if (riskClass == RiskClass.HIGH && !hasApproval) {
            throw violation("HIGH scenario requires an approval node");
        }

        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        for (String nodeId : byId.keySet()) {
            adjacency.put(nodeId, new ArrayList<>());
            indegree.put(nodeId, 0);
        }
        Set<String> edges = new HashSet<>();
        for (DependencyTemplate dependency : planTemplate.dependencies) {
            if (!byId.containsKey(dependency.prerequisiteNodeId)
                    || !byId.containsKey(dependency.dependentNodeId)) {
                throw violation("dependency references an unknown node");
            }
            if (dependency.prerequisiteNodeId.equals(dependency.dependentNodeId)) {
                throw violation("dependency cannot be a self edge");
            }
            String edge = dependency.prerequisiteNodeId + '\u0000'
                    + dependency.dependentNodeId;
            if (!edges.add(edge)) {
                throw violation("planTemplate contains a duplicate dependency");
            }
            adjacency.get(dependency.prerequisiteNodeId).add(dependency.dependentNodeId);
            indegree.put(
                    dependency.dependentNodeId,
                    indegree.get(dependency.dependentNodeId) + 1);
        }
        validateDag(adjacency, indegree);
        validateCompensation(byId);
        for (String optionalNodeId : fallback.optionalNodeIds) {
            NodeTemplate node = byId.get(optionalNodeId);
            if (node == null || node.required) {
                throw violation("fallback optional node must reference an optional node");
            }
        }
        if (fallback.mode == FallbackMode.FAIL_CLOSED
                && !fallback.optionalNodeIds.isEmpty()) {
            throw violation("FAIL_CLOSED fallback cannot list optional nodes");
        }
    }

    private static void validateDag(
            Map<String, List<String>> adjacency,
            Map<String, Integer> indegree) {
        ArrayDeque<String> ready = new ArrayDeque<>();
        Map<String, Integer> depth = new HashMap<>();
        for (Map.Entry<String, Integer> entry : indegree.entrySet()) {
            if (entry.getValue() == 0) {
                ready.add(entry.getKey());
                depth.put(entry.getKey(), 1);
            }
        }
        int visited = 0;
        while (!ready.isEmpty()) {
            String node = ready.removeFirst();
            visited++;
            int nodeDepth = depth.get(node);
            if (nodeDepth > PlanContract.MAX_GRAPH_DEPTH) {
                throw violation("planTemplate exceeds maximum graph depth");
            }
            for (String dependent : adjacency.get(node)) {
                depth.put(dependent, Math.max(
                        depth.getOrDefault(dependent, 1), nodeDepth + 1));
                int next = indegree.get(dependent) - 1;
                indegree.put(dependent, next);
                if (next == 0) {
                    ready.addLast(dependent);
                }
            }
        }
        if (visited != adjacency.size()) {
            throw violation("planTemplate graph contains a cycle");
        }
        Map<Integer, Integer> widths = new HashMap<>();
        for (int value : depth.values()) {
            int width = widths.getOrDefault(value, 0) + 1;
            if (width > PlanContract.MAX_PARALLEL_NODES) {
                throw violation("planTemplate exceeds maximum graph parallelism");
            }
            widths.put(value, width);
        }
    }

    private static void validateCompensation(Map<String, NodeTemplate> byId) {
        for (NodeTemplate node : byId.values()) {
            if (node.compensationNodeId.isEmpty()) {
                continue;
            }
            NodeTemplate compensation = byId.get(node.compensationNodeId);
            if (compensation == null || !"compensate".equals(compensation.nodeType)) {
                throw violation("compensation must reference a compensate node");
            }
            Set<String> path = new HashSet<>();
            NodeTemplate current = node;
            while (!current.compensationNodeId.isEmpty()) {
                if (!path.add(current.nodeId)) {
                    throw violation("compensation graph contains a loop");
                }
                current = byId.get(current.compensationNodeId);
                if (current == null) {
                    break;
                }
            }
        }
    }

    public int getSchemaVersion() {
        return schemaVersion;
    }

    public String getScenarioId() {
        return scenarioId;
    }

    public int getVersion() {
        return version;
    }

    public String getArtifactDigest() {
        return artifactDigest;
    }

    public Set<Source> getSupportedSources() {
        return supportedSources;
    }

    public Set<Zone> getSupportedZones() {
        return supportedZones;
    }

    public String getContextPolicyId() {
        return contextPolicyId;
    }

    public List<VehicleSignalPath> getRequiredContext() {
        return requiredContext;
    }

    public List<VehicleSignalPath> getOptionalContext() {
        return optionalContext;
    }

    public List<VehicleCapability.CapabilityId> getRequiredCapabilities() {
        return requiredCapabilities;
    }

    public List<VehicleCapability.CapabilityId> getOptionalCapabilities() {
        return optionalCapabilities;
    }

    public RiskClass getRiskClass() {
        return riskClass;
    }

    public PlanTemplate getPlanTemplate() {
        return planTemplate;
    }

    public FallbackPolicy getFallback() {
        return fallback;
    }

    public UiMetadata getUi() {
        return ui;
    }

    private static <T> Set<T> immutableSet(Set<T> values, String field) {
        Objects.requireNonNull(values, field);
        if (values.isEmpty() || values.size() > 8 || values.contains(null)) {
            throw violation(field + " must contain 1..8 unique values");
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(values));
    }

    private static <T> List<T> immutableBounded(
            List<T> values, int minimum, int maximum, String field) {
        Objects.requireNonNull(values, field);
        if (values.size() < minimum || values.size() > maximum) {
            throw violation(field + " is outside its bounded size");
        }
        List<T> copy = new ArrayList<>(values.size());
        Set<T> unique = new HashSet<>();
        for (T value : values) {
            T required = Objects.requireNonNull(value, field);
            if (!unique.add(required)) {
                throw violation(field + " contains a duplicate value");
            }
            copy.add(required);
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<String> immutableStrings(
            List<String> values,
            int maximumItems,
            int maximumChars,
            Pattern pattern,
            String field) {
        Objects.requireNonNull(values, field);
        if (values.size() > maximumItems) {
            throw violation(field + " contains too many items");
        }
        List<String> copy = new ArrayList<>(values.size());
        Set<String> unique = new HashSet<>();
        for (String value : values) {
            String checked = requireIdentifier(value, maximumChars, pattern, field);
            if (!unique.add(checked)) {
                throw violation(field + " contains a duplicate value");
            }
            copy.add(checked);
        }
        return Collections.unmodifiableList(copy);
    }

    private static <T> void ensureDisjoint(List<T> first, List<T> second, String field) {
        Set<T> overlap = new HashSet<>(first);
        overlap.retainAll(second);
        if (!overlap.isEmpty()) {
            throw violation(field + " required/optional sets overlap");
        }
    }

    private static String requireIdentifier(
            String value, int maximumChars, Pattern pattern, String field) {
        if (value == null || value.isEmpty() || value.length() > maximumChars
                || !pattern.matcher(value).matches()) {
            throw violation(field + " has an invalid identifier shape");
        }
        return value;
    }

    private static String requireOptionalIdentifier(
            String value, int maximumChars, Pattern pattern, String field) {
        if (value == null || value.length() > maximumChars
                || (!value.isEmpty() && !pattern.matcher(value).matches())) {
            throw violation(field + " has an invalid optional identifier shape");
        }
        return value;
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_SCENARIO_MANIFEST: " + message);
    }
}
