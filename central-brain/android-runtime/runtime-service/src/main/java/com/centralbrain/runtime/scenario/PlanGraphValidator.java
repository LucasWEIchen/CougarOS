package com.centralbrain.runtime.scenario;

import com.centralbrain.runtime.context.ContextSnapshot;
import com.centralbrain.runtime.context.ContextSnapshot.DrivingState;
import com.centralbrain.runtime.context.ContextSnapshot.SeatZone;
import com.centralbrain.runtime.scenario.ScenarioManifest.DrivingPolicy;
import com.centralbrain.runtime.scenario.ScenarioManifest.NodeTemplate;
import com.centralbrain.runtime.scenario.ScenarioPlanCompiler.CompiledPlan;
import com.centralbrain.sdk.plan.NodeDependency;
import com.centralbrain.sdk.plan.PlanContract;
import com.centralbrain.sdk.plan.PlanNode;
import com.centralbrain.sdk.plan.ScenarioPlan;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Structural and scenario-semantic validation for compiled non-executable DAGs. */
public final class PlanGraphValidator {
    private static final String DRIVER_RECLINE = "vehicle.seat.recline";

    private PlanGraphValidator() {}

    public static void validate(CompiledPlan compiled, ContextSnapshot context) {
        if (compiled == null || context == null) {
            throw violation("compiled plan and Context are required");
        }
        ScenarioPlan plan = compiled.toScenarioPlan();
        if (!plan.contextDigest.equals(context.getDigest())) {
            throw violation("compiled Context digest drift detected");
        }
        String expected = PlanDigest.planDigest(
                plan,
                compiled.getResolutionDigest(),
                compiled.getManifestVersion(),
                compiled.getManifestArtifactDigest(),
                compiled.getCapabilityDigest(),
                compiled.getExcludedOptionalNodeIds());
        if (!plan.planDigest.equals(expected)) {
            throw violation("compiled Plan digest drift detected");
        }
        validateTransport(plan);
        validateManifestBranches(compiled, plan, context);
    }

    public static void validateTransport(ScenarioPlan plan) {
        try {
            PlanContract.validatePlan(plan);
        } catch (IllegalArgumentException exception) {
            throw violation("Plan contract rejected the graph", exception);
        }
        Map<String, PlanNode> byId = new HashMap<>();
        Map<String, List<String>> forward = new HashMap<>();
        Map<String, List<String>> reverse = new HashMap<>();
        for (PlanNode node : plan.nodes) {
            byId.put(node.nodeId, node);
            forward.put(node.nodeId, new ArrayList<>());
            reverse.put(node.nodeId, new ArrayList<>());
        }
        for (NodeDependency dependency : plan.dependencies) {
            forward.get(dependency.prerequisiteNodeId).add(dependency.dependentNodeId);
            reverse.get(dependency.dependentNodeId).add(dependency.prerequisiteNodeId);
        }
        for (PlanNode node : plan.nodes) {
            if (!"effect.execute".equals(node.nodeType)) {
                continue;
            }
            if (node.required && !hasReachableVerification(node, byId, forward)) {
                throw violation("required effect has no reachable verification");
            }
            if (node.policy.riskClass >= PlanContract.RISK_HIGH
                    && !hasApprovalPredecessor(node.nodeId, byId, reverse)) {
                throw violation("HIGH effect has no approval predecessor");
            }
        }
    }

    private static void validateManifestBranches(
            CompiledPlan compiled,
            ScenarioPlan plan,
            ContextSnapshot context) {
        Set<String> included = new HashSet<>();
        for (PlanNode node : plan.nodes) {
            included.add(node.nodeId);
            if (context.getSeatZone() == SeatZone.ROW1_DRIVER
                    && context.getDrivingState() != DrivingState.PARKED
                    && "effect.execute".equals(node.nodeType)
                    && DRIVER_RECLINE.equals(node.capabilityId)) {
                throw violation("moving or unknown driver branch contains recline dispatch");
            }
        }
        Set<String> excluded = new HashSet<>(compiled.getExcludedOptionalNodeIds());
        for (NodeTemplate template : compiled.getManifest().getPlanTemplate().getNodes()) {
            boolean isIncluded = included.contains(template.getNodeId());
            boolean isExcluded = excluded.contains(template.getNodeId());
            if (isIncluded == isExcluded) {
                throw violation("manifest node is duplicated or missing from compilation");
            }
            if (isExcluded && template.isRequired()) {
                throw violation("required manifest node was excluded");
            }
            if (isIncluded
                    && template.getPolicy().getDrivingPolicy() == DrivingPolicy.PARKED_ONLY
                    && context.getDrivingState() != DrivingState.PARKED) {
                throw violation("non-parked graph contains a PARKED_ONLY node");
            }
        }
    }

    private static boolean hasReachableVerification(
            PlanNode effect,
            Map<String, PlanNode> byId,
            Map<String, List<String>> forward) {
        ArrayDeque<String> pending = new ArrayDeque<>(forward.get(effect.nodeId));
        Set<String> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            String nodeId = pending.removeFirst();
            if (!visited.add(nodeId)) {
                continue;
            }
            PlanNode candidate = byId.get(nodeId);
            if ("effect.verify".equals(candidate.nodeType)
                    && effect.capabilityId.equals(candidate.capabilityId)) {
                return true;
            }
            pending.addAll(forward.get(nodeId));
        }
        return false;
    }

    private static boolean hasApprovalPredecessor(
            String nodeId,
            Map<String, PlanNode> byId,
            Map<String, List<String>> reverse) {
        ArrayDeque<String> pending = new ArrayDeque<>(reverse.get(nodeId));
        Set<String> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            String predecessor = pending.removeFirst();
            if (!visited.add(predecessor)) {
                continue;
            }
            if ("approval.interrupt".equals(byId.get(predecessor).nodeType)) {
                return true;
            }
            pending.addAll(reverse.get(predecessor));
        }
        return false;
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_PLAN_GRAPH: " + message);
    }

    private static IllegalArgumentException violation(String message, Throwable cause) {
        return new IllegalArgumentException("CB_PLAN_GRAPH: " + message, cause);
    }
}
