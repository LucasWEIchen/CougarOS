package com.centralbrain.sdk.plan;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class PlanContractTest {
    private static final long NOW = 1_750_000_000_000L;
    private static final String DIGEST =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Test
    public void acceptsBoundedAcyclicPlan() {
        ScenarioPlan plan = validPlan();
        PlanContract.validatePlan(plan);
        assertEquals(11, PlanContract.allowedNodeTypes().size());
        assertTrue(PlanContract.allowedNodeTypes().contains("effect.execute"));
    }

    @Test
    public void rejectsCycleAndUnknownDependency() {
        ScenarioPlan cycle = validPlan();
        cycle.dependencies = new NodeDependency[] {
                dependency("capture-context", "apply-hvac"),
                dependency("apply-hvac", "verify-hvac"),
                dependency("verify-hvac", "capture-context")
        };
        expectViolation(() -> PlanContract.validatePlan(cycle));

        ScenarioPlan missing = validPlan();
        missing.dependencies = new NodeDependency[] {
                dependency("missing-node", "apply-hvac")
        };
        expectViolation(() -> PlanContract.validatePlan(missing));
    }

    @Test
    public void rejectsUnknownNodeTypeAndVersion() {
        ScenarioPlan unknownType = validPlan();
        unknownType.nodes[1].nodeType = "shell.execute";
        expectViolation(() -> PlanContract.validatePlan(unknownType));

        ScenarioPlan unknownVersion = validPlan();
        unknownVersion.nodes[0].schemaVersion = 2;
        expectViolation(() -> PlanContract.validatePlan(unknownVersion));
    }

    @Test
    public void rejectsUnsafeRetryAndCompensationMetadata() {
        ScenarioPlan retry = validPlan();
        retry.nodes[0].maxAttempts = 2;
        expectViolation(() -> PlanContract.validatePlan(retry));

        ScenarioPlan missingCompensation = validPlan();
        missingCompensation.nodes[1].compensationNodeId = "missing-compensation";
        expectViolation(() -> PlanContract.validatePlan(missingCompensation));

        ScenarioPlan loop = validPlan();
        loop.nodes[1].compensationNodeId = "compensate-hvac";
        loop.nodes[3].compensationNodeId = "compensate-hvac";
        expectViolation(() -> PlanContract.validatePlan(loop));
    }

    @Test
    public void rejectsUnknownPolicyAndOversizeGraph() {
        ScenarioPlan policy = validPlan();
        policy.nodes[0].policy.riskClass = 99;
        expectViolation(() -> PlanContract.validatePlan(policy));

        ScenarioPlan oversize = validPlan();
        oversize.nodes = new PlanNode[PlanContract.MAX_NODES + 1];
        expectViolation(() -> PlanContract.validatePlan(oversize));
    }

    @Test
    public void rejectsDepthParallelismDependencyAndDeadlineBounds() {
        ScenarioPlan deep = validPlan();
        deep.nodes = nodes(PlanContract.MAX_GRAPH_DEPTH + 1);
        deep.dependencies = chain(deep.nodes);
        expectViolation(() -> PlanContract.validatePlan(deep));

        ScenarioPlan wide = validPlan();
        wide.nodes = nodes(PlanContract.MAX_PARALLEL_NODES + 1);
        wide.dependencies = new NodeDependency[0];
        expectViolation(() -> PlanContract.validatePlan(wide));

        ScenarioPlan dependencyCount = validPlan();
        dependencyCount.dependencies = new NodeDependency[PlanContract.MAX_DEPENDENCIES + 1];
        expectViolation(() -> PlanContract.validatePlan(dependencyCount));

        ScenarioPlan deadline = validPlan();
        deadline.deadlineEpochMs =
                deadline.compiledAtEpochMs + PlanContract.MAX_PLAN_DEADLINE_MS + 1;
        expectViolation(() -> PlanContract.validatePlan(deadline));
    }

    static ScenarioPlan validPlan() {
        PlanNode capture = node("capture-context", "context.capture", false);
        PlanNode apply = node("apply-hvac", "effect.execute", true);
        apply.capabilityId = "vehicle.hvac.temperature";
        apply.resourceKey = "vehicle:hvac:row1-driver";
        apply.idempotencyKey = "plan-1:apply-hvac";
        apply.policy.verificationRequired = true;
        apply.compensationNodeId = "compensate-hvac";
        apply.policy.failureMode = PlanContract.FAILURE_COMPENSATE;

        PlanNode verify = node("verify-hvac", "effect.verify", false);
        verify.capabilityId = "vehicle.hvac.temperature";

        PlanNode compensate = node("compensate-hvac", "compensate", false);
        compensate.capabilityId = "vehicle.hvac.temperature";
        compensate.resourceKey = "vehicle:hvac:row1-driver";
        compensate.idempotencyKey = "plan-1:compensate-hvac";

        ScenarioPlan plan = new ScenarioPlan();
        plan.planId = "c9c1ec9f-4d04-4c49-9156-d8e1be2e60a0";
        plan.sessionId = "9bffbb6a-5a0b-41b5-a924-bcfb45be4f26";
        plan.scenarioId = "scene.cold.assist.v1";
        plan.revision = 1;
        plan.contextDigest = DIGEST;
        plan.planDigest = DIGEST;
        plan.compiledAtEpochMs = NOW;
        plan.deadlineEpochMs = NOW + 120_000;
        plan.nodes = new PlanNode[] {capture, apply, verify, compensate};
        plan.dependencies = new NodeDependency[] {
                dependency("capture-context", "apply-hvac"),
                dependency("apply-hvac", "verify-hvac")
        };
        return plan;
    }

    private static PlanNode node(String nodeId, String nodeType, boolean required) {
        PlanNode node = new PlanNode();
        node.nodeId = nodeId;
        node.nodeType = nodeType;
        node.inputDigest = DIGEST;
        node.timeoutMs = 30_000;
        node.maxAttempts = 1;
        node.required = required;
        node.policy = policy();
        return node;
    }

    private static NodePolicy policy() {
        NodePolicy policy = new NodePolicy();
        policy.policyId = "policy.cabin.default";
        policy.policyVersion = 1;
        policy.riskClass = PlanContract.RISK_LOW;
        policy.failureMode = PlanContract.FAILURE_FAIL_PLAN;
        return policy;
    }

    private static NodeDependency dependency(String prerequisite, String dependent) {
        NodeDependency dependency = new NodeDependency();
        dependency.prerequisiteNodeId = prerequisite;
        dependency.dependentNodeId = dependent;
        dependency.condition = PlanContract.DEPENDENCY_ON_SUCCESS;
        return dependency;
    }

    private static PlanNode[] nodes(int count) {
        PlanNode[] nodes = new PlanNode[count];
        for (int index = 0; index < count; index++) {
            nodes[index] = node("node-" + index, "summary.render", false);
        }
        return nodes;
    }

    private static NodeDependency[] chain(PlanNode[] nodes) {
        NodeDependency[] dependencies = new NodeDependency[nodes.length - 1];
        for (int index = 0; index < dependencies.length; index++) {
            dependencies[index] = dependency(nodes[index].nodeId, nodes[index + 1].nodeId);
        }
        return dependencies;
    }

    private static void expectViolation(Runnable operation) {
        try {
            operation.run();
            fail("expected a Plan contract violation");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().startsWith("CB_PLAN_CONTRACT:"));
        }
    }
}
