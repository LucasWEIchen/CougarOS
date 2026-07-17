package com.centralbrain.runtime.scenario;

import com.centralbrain.runtime.context.ContextSnapshot;
import com.centralbrain.runtime.context.ContextSnapshot.DrivingState;
import com.centralbrain.runtime.scenario.ScenarioManifest.DependencyTemplate;
import com.centralbrain.runtime.scenario.ScenarioManifest.DrivingPolicy;
import com.centralbrain.runtime.scenario.ScenarioManifest.FailureMode;
import com.centralbrain.runtime.scenario.ScenarioManifest.FallbackMode;
import com.centralbrain.runtime.scenario.ScenarioManifest.NodeTemplate;
import com.centralbrain.runtime.scenario.ScenarioManifest.RiskClass;
import com.centralbrain.runtime.scenario.ScenarioResolution.Decision;
import com.centralbrain.runtime.scenario.ScenarioResolver.CapabilitySnapshot;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability.CapabilityId;
import com.centralbrain.sdk.plan.NodeDependency;
import com.centralbrain.sdk.plan.NodePolicy;
import com.centralbrain.sdk.plan.PlanContract;
import com.centralbrain.sdk.plan.PlanNode;
import com.centralbrain.sdk.plan.ScenarioPlan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Compiles one digest-bound resolution into a non-executable typed DAG. */
public final class ScenarioPlanCompiler {
    public static final int SCHEMA_VERSION = 1;

    public static final class CompileRequest {
        private final String planId;
        private final String sessionId;
        private final int revision;
        private final long compiledAtEpochMs;
        private final long deadlineEpochMs;

        public CompileRequest(
                String planId,
                String sessionId,
                int revision,
                long compiledAtEpochMs,
                long deadlineEpochMs) {
            this.planId = canonicalUuid(planId, "planId");
            this.sessionId = canonicalUuid(sessionId, "sessionId");
            if (revision < 1) {
                throw violation("revision must be positive");
            }
            if (compiledAtEpochMs <= 0
                    || deadlineEpochMs <= compiledAtEpochMs
                    || deadlineEpochMs - compiledAtEpochMs
                            > PlanContract.MAX_PLAN_DEADLINE_MS) {
                throw violation("deadline is outside the Plan contract");
            }
            this.revision = revision;
            this.compiledAtEpochMs = compiledAtEpochMs;
            this.deadlineEpochMs = deadlineEpochMs;
        }
    }

    /** Immutable owner object; every AIDL transport view is returned as a deep copy. */
    public static final class CompiledPlan {
        private final ScenarioPlan plan;
        private final String resolutionDigest;
        private final int manifestVersion;
        private final String manifestArtifactDigest;
        private final String capabilityDigest;
        private final List<String> excludedOptionalNodeIds;
        private final ScenarioManifest manifest;

        private CompiledPlan(
                ScenarioPlan plan,
                ScenarioResolution resolution,
                ScenarioManifest manifest,
                List<String> excludedOptionalNodeIds) {
            this.plan = copyPlan(plan);
            this.resolutionDigest = resolution.getResolutionDigest();
            this.manifestVersion = manifest.getVersion();
            this.manifestArtifactDigest = manifest.getArtifactDigest();
            this.capabilityDigest = resolution.getCapabilityDigest();
            this.excludedOptionalNodeIds = Collections.unmodifiableList(
                    new ArrayList<>(excludedOptionalNodeIds));
            this.manifest = manifest;
        }

        public ScenarioPlan toScenarioPlan() {
            return copyPlan(plan);
        }

        public String getPlanDigest() {
            return plan.planDigest;
        }

        public String getResolutionDigest() {
            return resolutionDigest;
        }

        public int getManifestVersion() {
            return manifestVersion;
        }

        public String getManifestArtifactDigest() {
            return manifestArtifactDigest;
        }

        public String getCapabilityDigest() {
            return capabilityDigest;
        }

        public List<String> getExcludedOptionalNodeIds() {
            return excludedOptionalNodeIds;
        }

        public boolean isExecutable() {
            return false;
        }

        public boolean isProductionTrusted() {
            return false;
        }

        ScenarioManifest getManifest() {
            return manifest;
        }
    }

    public CompiledPlan compile(
            CompileRequest request,
            ScenarioResolution resolution,
            ContextSnapshot context,
            CapabilitySnapshot capabilities) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(resolution, "resolution");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(capabilities, "capabilities");
        validateBindings(resolution, context, capabilities);

        ScenarioManifest manifest = resolution.getSelectedManifest().orElseThrow(
                () -> violation("selected manifest is missing"));
        Set<String> excluded = selectExcludedNodes(
                resolution, manifest, context, capabilities);
        List<PlanNode> nodes = compileNodes(request, resolution, manifest, context, excluded);
        List<NodeDependency> dependencies = compileDependencies(manifest, excluded);

        ScenarioPlan plan = new ScenarioPlan();
        plan.planId = request.planId;
        plan.sessionId = request.sessionId;
        plan.scenarioId = manifest.getScenarioId();
        plan.revision = request.revision;
        plan.contextDigest = context.getDigest();
        plan.compiledAtEpochMs = request.compiledAtEpochMs;
        plan.deadlineEpochMs = request.deadlineEpochMs;
        plan.nodes = nodes.toArray(new PlanNode[0]);
        plan.dependencies = dependencies.toArray(new NodeDependency[0]);
        List<String> excludedOrdered = new ArrayList<>(excluded);
        excludedOrdered.sort(String::compareTo);
        plan.planDigest = PlanDigest.planDigest(
                plan,
                resolution.getResolutionDigest(),
                manifest.getVersion(),
                manifest.getArtifactDigest(),
                capabilities.getDigest(),
                excludedOrdered);

        CompiledPlan compiled = new CompiledPlan(
                plan, resolution, manifest, excludedOrdered);
        PlanGraphValidator.validate(compiled, context);
        return compiled;
    }

    private static void validateBindings(
            ScenarioResolution resolution,
            ContextSnapshot context,
            CapabilitySnapshot capabilities) {
        if (resolution.getDecision() == Decision.REJECTED) {
            throw violation("rejected resolution cannot be compiled");
        }
        if (!resolution.getResolutionDigest().equals(
                PlanDigest.resolutionDigest(resolution))) {
            throw violation("resolution digest drift detected");
        }
        if (!resolution.getContextDigest().equals(context.getDigest())) {
            throw violation("Context digest drift detected");
        }
        if (!resolution.getCapabilityDigest().equals(capabilities.getDigest())) {
            throw violation("capability digest drift detected");
        }
        if (!resolution.getUnavailableRequiredContext().isEmpty()
                || !resolution.getUnavailableRequiredCapabilities().isEmpty()
                || context.isRestricted()
                || !context.isRuntimeStateFresh()
                || !context.isRequiredFreshnessComplete()) {
            throw violation("required Context or capability gate is not satisfied");
        }
        ScenarioManifest manifest = resolution.getSelectedManifest().orElseThrow(
                () -> violation("selected manifest is missing"));
        if (!manifest.getScenarioId().equals(resolution.getScenarioId())
                || manifest.getSchemaVersion() != ScenarioManifest.SCHEMA_VERSION
                || !manifest.getContextPolicyId().equals(context.getPolicyId())) {
            throw violation("manifest version, scenario, or Context policy drift detected");
        }
        for (CapabilityId capability : manifest.getRequiredCapabilities()) {
            if (!capabilities.isAvailable(capability)) {
                throw violation("required capability is unavailable");
            }
        }
    }

    private static Set<String> selectExcludedNodes(
            ScenarioResolution resolution,
            ScenarioManifest manifest,
            ContextSnapshot context,
            CapabilitySnapshot capabilities) {
        Set<String> allowedFallback = new HashSet<>(
                manifest.getFallback().getOptionalNodeIds());
        Set<String> excluded = new LinkedHashSet<>();
        Set<CapabilityId> excludedCapabilities = new LinkedHashSet<>();
        for (NodeTemplate node : manifest.getPlanTemplate().getNodes()) {
            boolean capabilityBlocked = node.getCapabilityId() != null
                    && !capabilities.isAvailable(node.getCapabilityId());
            boolean policyBlocked = node.getPolicy().getDrivingPolicy()
                    == DrivingPolicy.PARKED_ONLY
                    && context.getDrivingState() != DrivingState.PARKED;
            if (!capabilityBlocked && !policyBlocked) {
                continue;
            }
            if (node.isRequired()) {
                throw violation("required node is blocked at compile time");
            }
            requireFallbackNode(manifest, allowedFallback, node);
            excluded.add(node.getNodeId());
            if (node.getCapabilityId() != null) {
                excludedCapabilities.add(node.getCapabilityId());
            }
        }

        boolean changed;
        do {
            changed = false;
            for (NodeTemplate node : manifest.getPlanTemplate().getNodes()) {
                if (node.isRequired() || excluded.contains(node.getNodeId())
                        || !allowedFallback.contains(node.getNodeId())) {
                    continue;
                }
                boolean hasOutgoing = false;
                boolean allOutgoingExcluded = true;
                for (DependencyTemplate dependency
                        : manifest.getPlanTemplate().getDependencies()) {
                    if (!dependency.getPrerequisiteNodeId().equals(node.getNodeId())) {
                        continue;
                    }
                    hasOutgoing = true;
                    if (!excluded.contains(dependency.getDependentNodeId())) {
                        allOutgoingExcluded = false;
                    }
                }
                if (hasOutgoing && allOutgoingExcluded) {
                    excluded.add(node.getNodeId());
                    changed = true;
                }
            }
        } while (changed);

        Set<CapabilityId> declaredUnavailable = new LinkedHashSet<>(
                resolution.getUnavailableOptionalCapabilities());
        if (!excludedCapabilities.equals(declaredUnavailable)) {
            throw violation("optional capability branch does not match the resolution");
        }
        if (resolution.getDecision() == Decision.ACCEPTED && !excluded.isEmpty()) {
            throw violation("accepted resolution cannot remove a branch");
        }
        if (resolution.getDecision() == Decision.DEGRADED && excluded.isEmpty()) {
            throw violation("degraded resolution must remove an explicit fallback branch");
        }
        return excluded;
    }

    private static void requireFallbackNode(
            ScenarioManifest manifest,
            Set<String> allowedFallback,
            NodeTemplate node) {
        if (manifest.getFallback().getMode() != FallbackMode.DEGRADED_OPTIONAL_ONLY
                || !allowedFallback.contains(node.getNodeId())) {
            throw violation("blocked optional node is outside the declared fallback");
        }
    }

    private static List<PlanNode> compileNodes(
            CompileRequest request,
            ScenarioResolution resolution,
            ScenarioManifest manifest,
            ContextSnapshot context,
            Set<String> excluded) {
        Set<CapabilityId> verifiedCapabilities = new HashSet<>();
        for (NodeTemplate node : manifest.getPlanTemplate().getNodes()) {
            if (!excluded.contains(node.getNodeId())
                    && "effect.verify".equals(node.getNodeType())) {
                verifiedCapabilities.add(node.getCapabilityId());
            }
        }
        List<PlanNode> result = new ArrayList<>();
        for (NodeTemplate template : manifest.getPlanTemplate().getNodes()) {
            if (excluded.contains(template.getNodeId())) {
                continue;
            }
            if (!template.getCompensationNodeId().isEmpty()
                    && excluded.contains(template.getCompensationNodeId())) {
                throw violation("included node references an excluded compensation");
            }
            PlanNode node = new PlanNode();
            node.nodeId = template.getNodeId();
            node.nodeType = template.getNodeType();
            node.capabilityId = template.getCapabilityId() == null
                    ? "" : template.getCapabilityId().getCanonicalId();
            node.inputDigest = PlanDigest.nodeInputDigest(
                    resolution,
                    manifest,
                    template,
                    context.getDigest(),
                    resolution.getCapabilityDigest());
            node.resourceKey = resourceKey(template, context);
            node.timeoutMs = template.getTimeoutMs();
            node.maxAttempts = template.getMaxAttempts();
            node.idempotencyKey = template.getIdempotencyKeyTemplate().isEmpty()
                    ? "" : request.planId + ":" + template.getNodeId();
            node.required = template.isRequired();
            node.compensationNodeId = template.getCompensationNodeId();
            node.policy = compilePolicy(
                    template,
                    verifiedCapabilities.contains(template.getCapabilityId()));
            result.add(node);
        }
        return result;
    }

    private static NodePolicy compilePolicy(NodeTemplate node, boolean hasVerification) {
        NodePolicy policy = new NodePolicy();
        policy.policyId = node.getPolicy().getPolicyId();
        policy.policyVersion = node.getPolicy().getPolicyVersion();
        policy.riskClass = risk(node.getPolicy().getRiskClass());
        policy.approvalRequired = node.getPolicy().isApprovalRequired();
        policy.verificationRequired = "effect.execute".equals(node.getNodeType())
                && hasVerification;
        policy.failureMode = failure(node.getPolicy().getFailureMode());
        return policy;
    }

    private static List<NodeDependency> compileDependencies(
            ScenarioManifest manifest, Set<String> excluded) {
        List<NodeDependency> result = new ArrayList<>();
        for (DependencyTemplate template : manifest.getPlanTemplate().getDependencies()) {
            if (excluded.contains(template.getPrerequisiteNodeId())
                    || excluded.contains(template.getDependentNodeId())) {
                continue;
            }
            NodeDependency dependency = new NodeDependency();
            dependency.prerequisiteNodeId = template.getPrerequisiteNodeId();
            dependency.dependentNodeId = template.getDependentNodeId();
            dependency.condition = template.getCondition()
                    == ScenarioManifest.DependencyCondition.ON_SUCCESS
                    ? PlanContract.DEPENDENCY_ON_SUCCESS
                    : PlanContract.DEPENDENCY_ON_TERMINAL;
            result.add(dependency);
        }
        return result;
    }

    private static String resourceKey(NodeTemplate node, ContextSnapshot context) {
        if (node.getCapabilityId() == null) {
            return "";
        }
        String capability = node.getCapabilityId().getCanonicalId()
                .replace('.', ':')
                .replace('_', '-');
        String area = context.getSeatZone().name().toLowerCase().replace('_', '-');
        return capability + ":" + area;
    }

    private static int risk(RiskClass riskClass) {
        switch (riskClass) {
            case LOW:
                return PlanContract.RISK_LOW;
            case MEDIUM:
                return PlanContract.RISK_MEDIUM;
            case HIGH:
                return PlanContract.RISK_HIGH;
            default:
                throw violation("unknown risk class");
        }
    }

    private static int failure(FailureMode failureMode) {
        switch (failureMode) {
            case FAIL_SCENARIO:
                return PlanContract.FAILURE_FAIL_PLAN;
            case SKIP_OPTIONAL:
                return PlanContract.FAILURE_SKIP_OPTIONAL;
            case COMPENSATE:
                return PlanContract.FAILURE_COMPENSATE;
            default:
                throw violation("unknown failure mode");
        }
    }

    private static String canonicalUuid(String value, String field) {
        try {
            UUID parsed = UUID.fromString(value);
            if (!parsed.toString().equals(value)) {
                throw violation(field + " must be a canonical UUID");
            }
            return value;
        } catch (NullPointerException | IllegalArgumentException exception) {
            if (exception instanceof IllegalArgumentException
                    && exception.getMessage() != null
                    && exception.getMessage().startsWith("CB_SCENARIO_COMPILE:")) {
                throw (IllegalArgumentException) exception;
            }
            throw violation(field + " must be a canonical UUID");
        }
    }

    private static ScenarioPlan copyPlan(ScenarioPlan source) {
        ScenarioPlan copy = new ScenarioPlan();
        copy.schemaVersion = source.schemaVersion;
        copy.planId = source.planId;
        copy.sessionId = source.sessionId;
        copy.scenarioId = source.scenarioId;
        copy.revision = source.revision;
        copy.contextDigest = source.contextDigest;
        copy.planDigest = source.planDigest;
        copy.compiledAtEpochMs = source.compiledAtEpochMs;
        copy.deadlineEpochMs = source.deadlineEpochMs;
        copy.nodes = new PlanNode[source.nodes.length];
        for (int index = 0; index < source.nodes.length; index++) {
            copy.nodes[index] = copyNode(source.nodes[index]);
        }
        copy.dependencies = new NodeDependency[source.dependencies.length];
        for (int index = 0; index < source.dependencies.length; index++) {
            NodeDependency original = source.dependencies[index];
            NodeDependency dependency = new NodeDependency();
            dependency.schemaVersion = original.schemaVersion;
            dependency.prerequisiteNodeId = original.prerequisiteNodeId;
            dependency.dependentNodeId = original.dependentNodeId;
            dependency.condition = original.condition;
            copy.dependencies[index] = dependency;
        }
        return copy;
    }

    private static PlanNode copyNode(PlanNode source) {
        PlanNode copy = new PlanNode();
        copy.schemaVersion = source.schemaVersion;
        copy.nodeId = source.nodeId;
        copy.nodeType = source.nodeType;
        copy.capabilityId = source.capabilityId;
        copy.inputDigest = source.inputDigest;
        copy.resourceKey = source.resourceKey;
        copy.timeoutMs = source.timeoutMs;
        copy.maxAttempts = source.maxAttempts;
        copy.idempotencyKey = source.idempotencyKey;
        copy.required = source.required;
        copy.compensationNodeId = source.compensationNodeId;
        NodePolicy policy = new NodePolicy();
        policy.schemaVersion = source.policy.schemaVersion;
        policy.policyId = source.policy.policyId;
        policy.policyVersion = source.policy.policyVersion;
        policy.riskClass = source.policy.riskClass;
        policy.approvalRequired = source.policy.approvalRequired;
        policy.verificationRequired = source.policy.verificationRequired;
        policy.failureMode = source.policy.failureMode;
        copy.policy = policy;
        return copy;
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_SCENARIO_COMPILE: " + message);
    }
}
