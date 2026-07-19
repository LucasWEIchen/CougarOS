package com.centralbrain.runtime.orchestration;

import com.centralbrain.runtime.persistence.DurableDigest;
import com.centralbrain.sdk.orchestration.ICentralBrainOrchestration;
import com.centralbrain.sdk.orchestration.OrchestrationContract;
import com.centralbrain.sdk.orchestration.OrchestrationEffect;
import com.centralbrain.sdk.orchestration.OrchestrationNode;
import com.centralbrain.sdk.orchestration.OrchestrationSnapshot;
import com.centralbrain.sdk.plan.NodeDependency;
import com.centralbrain.sdk.plan.NodePolicy;
import com.centralbrain.sdk.plan.PlanNode;
import com.centralbrain.sdk.plan.ScenarioPlan;

/** Shared immutable copy and fail-closed projection helpers. */
public final class OrchestrationProjectionFactory {
    private OrchestrationProjectionFactory() {}

    public static OrchestrationBackend.Result blocked(
            OrchestrationBackend.SessionDescriptor session,
            String detailCode,
            long nowEpochMs) {
        OrchestrationSnapshot snapshot = new OrchestrationSnapshot();
        snapshot.schemaVersion = OrchestrationContract.SCHEMA_VERSION;
        snapshot.sessionId = session.getSessionId();
        snapshot.scenarioId = session.getScenarioId();
        snapshot.planId = "";
        snapshot.planRevision = 0;
        snapshot.planDigest = "";
        snapshot.state = ICentralBrainOrchestration.STATE_BLOCKED;
        snapshot.graphRevision = 0L;
        snapshot.nodes = new OrchestrationNode[0];
        snapshot.effects = new OrchestrationEffect[0];
        snapshot.pendingStage = ICentralBrainOrchestration.PENDING_NONE;
        snapshot.pendingNodeId = "";
        snapshot.pendingCapabilityId = "";
        snapshot.approvalId = "";
        snapshot.undoId = "";
        snapshot.detailCode = detailCode;
        snapshot.simulated = false;
        snapshot.effectDispatchEnabled = false;
        snapshot.readbackAvailable = false;
        snapshot.approvalResponseAvailable = false;
        snapshot.approvalAuthorityTrusted = false;
        snapshot.undoAvailable = false;
        snapshot.hardwareAccessed = false;
        snapshot.productionReady = false;
        snapshot.targetHardwareValidated = false;
        snapshot.updatedAtEpochMs = nowEpochMs;
        seal(snapshot);
        return new OrchestrationBackend.Result(snapshot, null, "");
    }

    public static void seal(OrchestrationSnapshot snapshot) {
        snapshot.projectionDigest = OrchestrationContract.calculateProjectionDigest(snapshot);
        OrchestrationContract.validateSnapshot(snapshot);
    }

    public static OrchestrationSnapshot copySnapshot(OrchestrationSnapshot source) {
        OrchestrationSnapshot copy = new OrchestrationSnapshot();
        copy.schemaVersion = source.schemaVersion;
        copy.sessionId = source.sessionId;
        copy.scenarioId = source.scenarioId;
        copy.planId = source.planId;
        copy.planRevision = source.planRevision;
        copy.planDigest = source.planDigest;
        copy.state = source.state;
        copy.graphRevision = source.graphRevision;
        OrchestrationNode[] sourceNodes = source.nodes == null
                ? new OrchestrationNode[0] : source.nodes;
        copy.nodes = new OrchestrationNode[sourceNodes.length];
        for (int index = 0; index < sourceNodes.length; index++) {
            OrchestrationNode original = sourceNodes[index];
            OrchestrationNode node = new OrchestrationNode();
            node.schemaVersion = original.schemaVersion;
            node.nodeId = original.nodeId;
            node.nodeType = original.nodeType;
            node.capabilityId = original.capabilityId;
            node.state = original.state;
            node.attemptCount = original.attemptCount;
            node.required = original.required;
            node.evidenceDigest = original.evidenceDigest;
            copy.nodes[index] = node;
        }
        OrchestrationEffect[] sourceEffects = source.effects == null
                ? new OrchestrationEffect[0] : source.effects;
        copy.effects = new OrchestrationEffect[sourceEffects.length];
        for (int index = 0; index < sourceEffects.length; index++) {
            OrchestrationEffect original = sourceEffects[index];
            OrchestrationEffect effect = new OrchestrationEffect();
            effect.schemaVersion = original.schemaVersion;
            effect.effectId = original.effectId;
            effect.nodeId = original.nodeId;
            effect.capabilityId = original.capabilityId;
            effect.state = original.state;
            effect.attemptCount = original.attemptCount;
            effect.simulated = original.simulated;
            effect.sourceId = original.sourceId;
            effect.evidenceDigest = original.evidenceDigest;
            copy.effects[index] = effect;
        }
        copy.pendingStage = source.pendingStage;
        copy.pendingNodeId = source.pendingNodeId;
        copy.pendingCapabilityId = source.pendingCapabilityId;
        copy.approvalId = source.approvalId;
        copy.undoId = source.undoId;
        copy.detailCode = source.detailCode;
        copy.projectionDigest = source.projectionDigest;
        copy.simulated = source.simulated;
        copy.effectDispatchEnabled = source.effectDispatchEnabled;
        copy.readbackAvailable = source.readbackAvailable;
        copy.approvalResponseAvailable = source.approvalResponseAvailable;
        copy.approvalAuthorityTrusted = source.approvalAuthorityTrusted;
        copy.undoAvailable = source.undoAvailable;
        copy.hardwareAccessed = source.hardwareAccessed;
        copy.productionReady = source.productionReady;
        copy.targetHardwareValidated = source.targetHardwareValidated;
        copy.updatedAtEpochMs = source.updatedAtEpochMs;
        return copy;
    }

    public static ScenarioPlan copyPlan(ScenarioPlan source) {
        if (source == null) {
            return null;
        }
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
            PlanNode original = source.nodes[index];
            PlanNode node = new PlanNode();
            node.schemaVersion = original.schemaVersion;
            node.nodeId = original.nodeId;
            node.nodeType = original.nodeType;
            node.capabilityId = original.capabilityId;
            node.inputDigest = original.inputDigest;
            node.resourceKey = original.resourceKey;
            node.timeoutMs = original.timeoutMs;
            node.maxAttempts = original.maxAttempts;
            node.idempotencyKey = original.idempotencyKey;
            node.required = original.required;
            node.compensationNodeId = original.compensationNodeId;
            NodePolicy policy = new NodePolicy();
            policy.schemaVersion = original.policy.schemaVersion;
            policy.policyId = original.policy.policyId;
            policy.policyVersion = original.policy.policyVersion;
            policy.riskClass = original.policy.riskClass;
            policy.approvalRequired = original.policy.approvalRequired;
            policy.verificationRequired = original.policy.verificationRequired;
            policy.failureMode = original.policy.failureMode;
            node.policy = policy;
            copy.nodes[index] = node;
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

    public static String digest(String domain, String... parts) {
        return DurableDigest.sha256(domain, parts);
    }
}
