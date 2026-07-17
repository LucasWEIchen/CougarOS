package com.centralbrain.runtime.scenario;

import com.centralbrain.runtime.scenario.ScenarioManifest.NodeTemplate;
import com.centralbrain.sdk.plan.NodeDependency;
import com.centralbrain.sdk.plan.NodePolicy;
import com.centralbrain.sdk.plan.PlanNode;
import com.centralbrain.sdk.plan.ScenarioPlan;

import java.security.MessageDigest;
import java.util.List;

/** Canonical SHA-256 bindings for a compiled scenario plan. */
public final class PlanDigest {
    private PlanDigest() {}

    static String resolutionDigest(ScenarioResolution resolution) {
        MessageDigest digest = ScenarioResolver.sha256();
        ScenarioResolver.update(digest, "central-brain-scenario-resolution-v1");
        ScenarioResolver.update(digest, resolution.getDecision().name());
        ScenarioResolver.update(digest, resolution.getMatchType().name());
        ScenarioResolver.update(digest, resolution.getScenarioId());
        ScenarioResolver.update(digest, resolution.getMatchedRuleId());
        ScenarioResolver.update(digest, resolution.getRequestDigest());
        ScenarioResolver.update(digest, resolution.getContextDigest());
        ScenarioResolver.update(digest, resolution.getCapabilityDigest());
        ScenarioResolver.update(
                digest,
                resolution.getSelectedManifest()
                        .map(ScenarioManifest::getArtifactDigest)
                        .orElse(""));
        updateList(digest, resolution.getReasons());
        updateList(digest, resolution.getCandidateScenarioIds());
        updateList(digest, resolution.getUnavailableRequiredContext());
        updateList(digest, resolution.getUnavailableRequiredCapabilities());
        updateList(digest, resolution.getUnavailableOptionalCapabilities());
        return ScenarioResolver.toHex(digest.digest());
    }

    static String nodeInputDigest(
            ScenarioResolution resolution,
            ScenarioManifest manifest,
            NodeTemplate node,
            String contextDigest,
            String capabilityDigest) {
        MessageDigest digest = ScenarioResolver.sha256();
        ScenarioResolver.update(digest, "central-brain-scenario-node-input-v1");
        ScenarioResolver.update(digest, resolution.getResolutionDigest());
        ScenarioResolver.update(digest, manifest.getArtifactDigest());
        ScenarioResolver.update(digest, Integer.toString(manifest.getVersion()));
        ScenarioResolver.update(digest, contextDigest);
        ScenarioResolver.update(digest, capabilityDigest);
        ScenarioResolver.update(digest, node.getNodeId());
        ScenarioResolver.update(digest, node.getNodeType());
        ScenarioResolver.update(
                digest,
                node.getCapabilityId() == null
                        ? "" : node.getCapabilityId().getCanonicalId());
        ScenarioResolver.update(digest, Boolean.toString(node.isRequired()));
        ScenarioResolver.update(digest, Long.toString(node.getTimeoutMs()));
        ScenarioResolver.update(digest, Integer.toString(node.getMaxAttempts()));
        ScenarioResolver.update(digest, node.getIdempotencyKeyTemplate());
        ScenarioResolver.update(digest, node.getCompensationNodeId());
        ScenarioResolver.update(digest, node.getPolicy().getPolicyId());
        ScenarioResolver.update(digest, Integer.toString(node.getPolicy().getPolicyVersion()));
        ScenarioResolver.update(digest, node.getPolicy().getRiskClass().name());
        ScenarioResolver.update(digest, node.getPolicy().getDrivingPolicy().name());
        ScenarioResolver.update(
                digest, Boolean.toString(node.getPolicy().isApprovalRequired()));
        ScenarioResolver.update(digest, node.getPolicy().getFailureMode().name());
        return ScenarioResolver.toHex(digest.digest());
    }

    static String planDigest(
            ScenarioPlan plan,
            String resolutionDigest,
            int manifestVersion,
            String manifestArtifactDigest,
            String capabilityDigest,
            List<String> excludedOptionalNodeIds) {
        MessageDigest digest = ScenarioResolver.sha256();
        ScenarioResolver.update(digest, "central-brain-compiled-scenario-plan-v1");
        ScenarioResolver.update(digest, Integer.toString(plan.schemaVersion));
        ScenarioResolver.update(digest, plan.planId);
        ScenarioResolver.update(digest, plan.sessionId);
        ScenarioResolver.update(digest, plan.scenarioId);
        ScenarioResolver.update(digest, Integer.toString(plan.revision));
        ScenarioResolver.update(digest, plan.contextDigest);
        ScenarioResolver.update(digest, Long.toString(plan.compiledAtEpochMs));
        ScenarioResolver.update(digest, Long.toString(plan.deadlineEpochMs));
        ScenarioResolver.update(digest, resolutionDigest);
        ScenarioResolver.update(digest, Integer.toString(manifestVersion));
        ScenarioResolver.update(digest, manifestArtifactDigest);
        ScenarioResolver.update(digest, capabilityDigest);
        ScenarioResolver.update(digest, Integer.toString(plan.nodes.length));
        for (PlanNode node : plan.nodes) {
            updateNode(digest, node);
        }
        ScenarioResolver.update(digest, Integer.toString(plan.dependencies.length));
        for (NodeDependency dependency : plan.dependencies) {
            ScenarioResolver.update(digest, dependency.prerequisiteNodeId);
            ScenarioResolver.update(digest, dependency.dependentNodeId);
            ScenarioResolver.update(digest, Integer.toString(dependency.condition));
        }
        updateList(digest, excludedOptionalNodeIds);
        return ScenarioResolver.toHex(digest.digest());
    }

    private static void updateNode(MessageDigest digest, PlanNode node) {
        ScenarioResolver.update(digest, node.nodeId);
        ScenarioResolver.update(digest, node.nodeType);
        ScenarioResolver.update(digest, node.capabilityId);
        ScenarioResolver.update(digest, node.inputDigest);
        ScenarioResolver.update(digest, node.resourceKey);
        ScenarioResolver.update(digest, Long.toString(node.timeoutMs));
        ScenarioResolver.update(digest, Integer.toString(node.maxAttempts));
        ScenarioResolver.update(digest, node.idempotencyKey);
        ScenarioResolver.update(digest, Boolean.toString(node.required));
        ScenarioResolver.update(digest, node.compensationNodeId);
        NodePolicy policy = node.policy;
        ScenarioResolver.update(digest, policy.policyId);
        ScenarioResolver.update(digest, Integer.toString(policy.policyVersion));
        ScenarioResolver.update(digest, Integer.toString(policy.riskClass));
        ScenarioResolver.update(digest, Boolean.toString(policy.approvalRequired));
        ScenarioResolver.update(digest, Boolean.toString(policy.verificationRequired));
        ScenarioResolver.update(digest, Integer.toString(policy.failureMode));
    }

    private static void updateList(MessageDigest digest, List<?> values) {
        ScenarioResolver.update(digest, Integer.toString(values.size()));
        for (Object value : values) {
            ScenarioResolver.update(digest, value.toString());
        }
    }
}
