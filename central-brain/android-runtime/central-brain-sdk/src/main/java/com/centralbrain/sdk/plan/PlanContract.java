package com.centralbrain.sdk.plan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** Bounded structural validation for the Stage 2 Plan/Node AIDL V1 contract. */
public final class PlanContract {
    public static final int SCHEMA_VERSION = 1;
    public static final String AIDL_CONTRACT_HASH =
            "8dbf27424a09ecac969aff444e7fc9e3c939c7bc5c6687de2d8a5a627d60dabd";

    public static final int MAX_PLAN_ID_CHARS = 36;
    public static final int MAX_SESSION_ID_CHARS = 36;
    public static final int MAX_SCENARIO_ID_CHARS = 96;
    public static final int MAX_NODE_ID_CHARS = 64;
    public static final int MAX_NODE_TYPE_CHARS = 48;
    public static final int MAX_CAPABILITY_ID_CHARS = 96;
    public static final int MAX_RESOURCE_KEY_CHARS = 128;
    public static final int MAX_IDEMPOTENCY_KEY_CHARS = 128;
    public static final int MAX_POLICY_ID_CHARS = 96;
    public static final int MAX_NODES = 64;
    public static final int MAX_DEPENDENCIES = 256;
    public static final int MAX_GRAPH_DEPTH = 16;
    public static final int MAX_PARALLEL_NODES = 8;
    public static final long MAX_NODE_TIMEOUT_MS = 120_000L;
    public static final int MAX_ATTEMPTS = 3;
    public static final long MAX_PLAN_DEADLINE_MS = 15 * 60 * 1000L;

    public static final int DEPENDENCY_ON_SUCCESS = 1;
    public static final int DEPENDENCY_ON_TERMINAL = 2;

    public static final int RISK_LOW = 1;
    public static final int RISK_MEDIUM = 2;
    public static final int RISK_HIGH = 3;
    public static final int RISK_CRITICAL = 4;

    public static final int FAILURE_FAIL_PLAN = 1;
    public static final int FAILURE_SKIP_OPTIONAL = 2;
    public static final int FAILURE_COMPENSATE = 3;

    private static final Pattern SCENARIO_ID =
            Pattern.compile("[a-z][a-z0-9]*(?:[.][a-z0-9][a-z0-9_-]*){2,7}");
    private static final Pattern LOCAL_ID =
            Pattern.compile("[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*");
    private static final Pattern QUALIFIED_ID =
            Pattern.compile("[a-z][a-z0-9_-]*(?:[.][a-z0-9][a-z0-9_-]*){1,7}");
    private static final Pattern RESOURCE_KEY =
            Pattern.compile("[a-z][a-z0-9_-]*(?::[a-z0-9][a-z0-9_-]*){1,7}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private static final Set<String> ALLOWED_NODE_TYPES = Set.of(
            "context.capture",
            "policy.evaluate",
            "approval.interrupt",
            "effect.execute",
            "effect.verify",
            "tool.invoke",
            "model.invoke",
            "memory.query",
            "memory.write",
            "summary.render",
            "compensate");

    private static final Set<String> SIDE_EFFECT_NODE_TYPES = Set.of(
            "effect.execute", "tool.invoke", "memory.write", "compensate");

    private PlanContract() {}

    public static Set<String> allowedNodeTypes() {
        return new LinkedHashSet<>(ALLOWED_NODE_TYPES);
    }

    public static void validatePlan(ScenarioPlan plan) {
        requireNotNull(plan, "plan");
        requireVersion(plan.schemaVersion, "plan.schemaVersion");
        requireUuid(plan.planId, MAX_PLAN_ID_CHARS, "plan.planId");
        requireUuid(plan.sessionId, MAX_SESSION_ID_CHARS, "plan.sessionId");
        requireIdentifier(
                plan.scenarioId,
                MAX_SCENARIO_ID_CHARS,
                SCENARIO_ID,
                "plan.scenarioId");
        if (plan.revision < 1) {
            throw violation("plan.revision must be positive");
        }
        requireDigest(plan.contextDigest, "plan.contextDigest");
        requireDigest(plan.planDigest, "plan.planDigest");
        if (plan.compiledAtEpochMs <= 0 || plan.deadlineEpochMs <= plan.compiledAtEpochMs
                || plan.deadlineEpochMs - plan.compiledAtEpochMs > MAX_PLAN_DEADLINE_MS) {
            throw violation("plan deadline is outside the bounded execution window");
        }
        if (plan.nodes == null || plan.nodes.length == 0 || plan.nodes.length > MAX_NODES) {
            throw violation("plan.nodes must contain 1.." + MAX_NODES + " nodes");
        }
        if (plan.dependencies == null || plan.dependencies.length > MAX_DEPENDENCIES) {
            throw violation("plan.dependencies exceeds " + MAX_DEPENDENCIES);
        }

        Map<String, PlanNode> nodesById = new HashMap<>();
        Set<String> idempotencyKeys = new HashSet<>();
        for (PlanNode node : plan.nodes) {
            validateNode(node);
            if (nodesById.put(node.nodeId, node) != null) {
                throw violation("plan contains duplicate nodeId " + node.nodeId);
            }
            if (!node.idempotencyKey.isEmpty() && !idempotencyKeys.add(node.idempotencyKey)) {
                throw violation("plan contains duplicate idempotencyKey");
            }
        }

        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, Integer> indegree = new HashMap<>();
        for (String nodeId : nodesById.keySet()) {
            adjacency.put(nodeId, new ArrayList<>());
            indegree.put(nodeId, 0);
        }
        Set<String> edges = new HashSet<>();
        for (NodeDependency dependency : plan.dependencies) {
            validateDependency(dependency);
            if (!nodesById.containsKey(dependency.prerequisiteNodeId)
                    || !nodesById.containsKey(dependency.dependentNodeId)) {
                throw violation("dependency references an unknown node");
            }
            if (dependency.prerequisiteNodeId.equals(dependency.dependentNodeId)) {
                throw violation("dependency cannot reference the same node twice");
            }
            String edge = dependency.prerequisiteNodeId + "\u0000"
                    + dependency.dependentNodeId;
            if (!edges.add(edge)) {
                throw violation("plan contains a duplicate dependency");
            }
            adjacency.get(dependency.prerequisiteNodeId).add(dependency.dependentNodeId);
            indegree.put(
                    dependency.dependentNodeId,
                    indegree.get(dependency.dependentNodeId) + 1);
        }

        validateCompensationReferences(nodesById);
        validateDag(adjacency, indegree);
    }

    public static void validateNode(PlanNode node) {
        requireNotNull(node, "node");
        requireVersion(node.schemaVersion, "node.schemaVersion");
        requireIdentifier(node.nodeId, MAX_NODE_ID_CHARS, LOCAL_ID, "node.nodeId");
        requireBounded(node.nodeType, MAX_NODE_TYPE_CHARS, "node.nodeType");
        if (!ALLOWED_NODE_TYPES.contains(node.nodeType)) {
            throw violation("node.nodeType is not allowlisted");
        }
        requireOptionalIdentifier(
                node.capabilityId,
                MAX_CAPABILITY_ID_CHARS,
                QUALIFIED_ID,
                "node.capabilityId");
        requireDigest(node.inputDigest, "node.inputDigest");
        requireOptionalIdentifier(
                node.resourceKey,
                MAX_RESOURCE_KEY_CHARS,
                RESOURCE_KEY,
                "node.resourceKey");
        if (node.timeoutMs < 1 || node.timeoutMs > MAX_NODE_TIMEOUT_MS) {
            throw violation("node.timeoutMs is outside 1.." + MAX_NODE_TIMEOUT_MS);
        }
        if (node.maxAttempts < 1 || node.maxAttempts > MAX_ATTEMPTS) {
            throw violation("node.maxAttempts is outside 1.." + MAX_ATTEMPTS);
        }
        requireBounded(
                node.idempotencyKey,
                MAX_IDEMPOTENCY_KEY_CHARS,
                "node.idempotencyKey");
        if ((node.maxAttempts > 1 || SIDE_EFFECT_NODE_TYPES.contains(node.nodeType))
                && node.idempotencyKey.isEmpty()) {
            throw violation("retryable or side-effect node requires idempotencyKey");
        }
        requireOptionalIdentifier(
                node.compensationNodeId,
                MAX_NODE_ID_CHARS,
                LOCAL_ID,
                "node.compensationNodeId");
        validatePolicy(node.policy);
        if (node.policy.failureMode == FAILURE_SKIP_OPTIONAL && node.required) {
            throw violation("required node cannot use skip-optional failure mode");
        }
        if (node.policy.failureMode == FAILURE_COMPENSATE
                && node.compensationNodeId.isEmpty()) {
            throw violation("compensating failure mode requires compensationNodeId");
        }
    }

    public static void validateDependency(NodeDependency dependency) {
        requireNotNull(dependency, "dependency");
        requireVersion(dependency.schemaVersion, "dependency.schemaVersion");
        requireIdentifier(
                dependency.prerequisiteNodeId,
                MAX_NODE_ID_CHARS,
                LOCAL_ID,
                "dependency.prerequisiteNodeId");
        requireIdentifier(
                dependency.dependentNodeId,
                MAX_NODE_ID_CHARS,
                LOCAL_ID,
                "dependency.dependentNodeId");
        if (dependency.condition != DEPENDENCY_ON_SUCCESS
                && dependency.condition != DEPENDENCY_ON_TERMINAL) {
            throw violation("dependency.condition is unknown");
        }
    }

    public static void validatePolicy(NodePolicy policy) {
        requireNotNull(policy, "policy");
        requireVersion(policy.schemaVersion, "policy.schemaVersion");
        requireIdentifier(
                policy.policyId,
                MAX_POLICY_ID_CHARS,
                QUALIFIED_ID,
                "policy.policyId");
        if (policy.policyVersion < 1) {
            throw violation("policy.policyVersion must be positive");
        }
        if (policy.riskClass < RISK_LOW || policy.riskClass > RISK_CRITICAL) {
            throw violation("policy.riskClass is unknown");
        }
        if (policy.failureMode < FAILURE_FAIL_PLAN
                || policy.failureMode > FAILURE_COMPENSATE) {
            throw violation("policy.failureMode is unknown");
        }
        if (policy.riskClass >= RISK_HIGH && !policy.approvalRequired) {
            throw violation("HIGH or CRITICAL policy requires approval metadata");
        }
    }

    private static void validateCompensationReferences(Map<String, PlanNode> nodesById) {
        for (PlanNode node : nodesById.values()) {
            if (node.compensationNodeId.isEmpty()) {
                continue;
            }
            PlanNode compensation = nodesById.get(node.compensationNodeId);
            if (compensation == null) {
                throw violation("compensation references an unknown node");
            }
            if (node.nodeId.equals(compensation.nodeId)) {
                throw violation("node cannot compensate itself");
            }
            if (!"compensate".equals(compensation.nodeType)) {
                throw violation("compensation reference must target a compensate node");
            }

            Set<String> path = new HashSet<>();
            PlanNode current = node;
            while (!current.compensationNodeId.isEmpty()) {
                if (!path.add(current.nodeId)) {
                    throw violation("compensation graph contains a loop");
                }
                current = nodesById.get(current.compensationNodeId);
                if (current == null) {
                    break;
                }
            }
        }
    }

    private static void validateDag(
            Map<String, List<String>> adjacency, Map<String, Integer> indegree) {
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
            String nodeId = ready.removeFirst();
            visited++;
            int nodeDepth = depth.get(nodeId);
            if (nodeDepth > MAX_GRAPH_DEPTH) {
                throw violation("plan graph exceeds maximum depth " + MAX_GRAPH_DEPTH);
            }
            for (String dependent : adjacency.get(nodeId)) {
                depth.put(dependent, Math.max(depth.getOrDefault(dependent, 1), nodeDepth + 1));
                int nextIndegree = indegree.get(dependent) - 1;
                indegree.put(dependent, nextIndegree);
                if (nextIndegree == 0) {
                    ready.addLast(dependent);
                }
            }
        }
        if (visited != adjacency.size()) {
            throw violation("plan graph contains a cycle");
        }

        Map<Integer, Integer> widthByDepth = new HashMap<>();
        for (int nodeDepth : depth.values()) {
            int width = widthByDepth.getOrDefault(nodeDepth, 0) + 1;
            if (width > MAX_PARALLEL_NODES) {
                throw violation("plan graph exceeds maximum parallelism " + MAX_PARALLEL_NODES);
            }
            widthByDepth.put(nodeDepth, width);
        }
    }

    private static void requireVersion(int version, String field) {
        if (version != SCHEMA_VERSION) {
            throw violation(field + " is unsupported");
        }
    }

    private static void requireUuid(String value, int maxChars, String field) {
        requireBounded(value, maxChars, field);
        if (value.length() != 36) {
            throw violation(field + " is not a canonical UUID");
        }
        try {
            if (!UUID.fromString(value).toString().equals(value.toLowerCase(Locale.ROOT))) {
                throw violation(field + " is not a canonical UUID");
            }
        } catch (IllegalArgumentException invalid) {
            throw violation(field + " is not a canonical UUID");
        }
    }

    private static void requireDigest(String value, String field) {
        requireBounded(value, 64, field);
        if (!SHA_256.matcher(value).matches()) {
            throw violation(field + " is not a lowercase SHA-256 digest");
        }
    }

    private static void requireOptionalIdentifier(
            String value, int maxChars, Pattern pattern, String field) {
        requireBounded(value, maxChars, field);
        if (!value.isEmpty() && !pattern.matcher(value).matches()) {
            throw violation(field + " has an invalid identifier shape");
        }
    }

    private static void requireIdentifier(
            String value, int maxChars, Pattern pattern, String field) {
        requireBounded(value, maxChars, field);
        if (value.isEmpty() || !pattern.matcher(value).matches()) {
            throw violation(field + " has an invalid identifier shape");
        }
    }

    private static void requireBounded(String value, int maxChars, String field) {
        if (value == null) {
            throw violation(field + " is null");
        }
        if (value.length() > maxChars) {
            throw violation(field + " exceeds " + maxChars + " characters");
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) && character != '\n' && character != '\t') {
                throw violation(field + " contains a forbidden control character");
            }
        }
    }

    private static void requireNotNull(Object value, String field) {
        if (value == null) {
            throw violation(field + " is null");
        }
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_PLAN_CONTRACT: " + message);
    }
}
