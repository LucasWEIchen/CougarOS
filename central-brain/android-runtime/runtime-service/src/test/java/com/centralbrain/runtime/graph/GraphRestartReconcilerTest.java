package com.centralbrain.runtime.graph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.graph.GraphRestartReconciler.CheckpointStatus;
import com.centralbrain.runtime.graph.GraphRestartReconciler.DirectiveType;
import com.centralbrain.runtime.graph.GraphRestartReconciler.EffectDeliveryStatus;
import com.centralbrain.runtime.graph.GraphRestartReconciler.Evidence;
import com.centralbrain.runtime.graph.GraphRestartReconciler.PersistentCompensation;
import com.centralbrain.runtime.graph.GraphRestartReconciler.PersistentEffect;
import com.centralbrain.runtime.graph.GraphRestartReconciler.PersistentNode;
import com.centralbrain.runtime.graph.GraphRestartReconciler.PersistentRun;
import com.centralbrain.runtime.graph.GraphRestartReconciler.Result;
import com.centralbrain.sdk.effect.EffectContract;
import com.centralbrain.sdk.plan.PlanContract;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GraphRestartReconcilerTest {
    private static final long NOW = 1_800_000_000_000L;
    private static final String PLAN_ID = "00000000-0000-0000-0000-000000000901";
    private static final String SESSION_ID = "00000000-0000-0000-0000-000000000902";
    private static final String EFFECT_ID = "00000000-0000-0000-0000-000000000903";
    private static final String OBSERVATION_ID = "00000000-0000-0000-0000-000000000904";
    private static final String COMPENSATION_ID = "00000000-0000-0000-0000-000000000905";
    private static final String SHA_A = "a".repeat(64);
    private static final String SHA_B = "b".repeat(64);
    private static final String SHA_C = "c".repeat(64);
    private static final String SHA_D = "d".repeat(64);

    @Test
    public void executingAndWaitingRecoverToReconcileBeforeContinue() {
        PersistentRun run = run(
                GraphRunState.EXECUTING,
                List.of(
                        node("effect_node", "effect.execute", NodeRunState.EXECUTING, SHA_A),
                        node("approval_node", "approval.interrupt", NodeRunState.WAITING, SHA_B)),
                List.of(unknownEffect()),
                List.of());

        Result result = new GraphRestartReconciler().reconcile(
                run,
                evidence(false, Map.of(SHA_A, CheckpointStatus.VALID,
                        SHA_B, CheckpointStatus.VALID), Map.of()),
                NOW);

        assertEquals(GraphRunState.WAITING, result.getTargetGraphState());
        assertEquals(NodeRunState.WAITING, state(result, "effect_node"));
        assertEquals(NodeRunState.WAITING, state(result, "approval_node"));
        assertTrue(hasDirective(result, DirectiveType.RECONCILE_EFFECT_NODE, "effect_node"));
        assertTrue(hasDirective(result, DirectiveType.REVALIDATE_APPROVAL, "approval_node"));
        assertTrue(hasDirective(result, DirectiveType.RECONCILE_EFFECT_STATUS, EFFECT_ID));
        assertFalse(result.isContinuationAllowed());
        assertFalse(result.isExecutorDispatchEnabled());
        assertFalse(result.isProductionAuthorized());
    }

    @Test
    public void validControlCheckpointCanContinueOnlyAfterGovernanceRevalidation() {
        PersistentRun run = run(
                GraphRunState.EXECUTING,
                List.of(node("context_node", "context.capture", NodeRunState.EXECUTING, SHA_A)),
                List.of(),
                List.of());
        GraphRestartReconciler reconciler = new GraphRestartReconciler();

        Result blocked = reconciler.reconcile(
                run,
                evidence(false, Map.of(SHA_A, CheckpointStatus.VALID), Map.of()),
                NOW);
        Result allowed = reconciler.reconcile(
                run,
                evidence(true, Map.of(SHA_A, CheckpointStatus.VALID), Map.of()),
                NOW);

        assertEquals(NodeRunState.WAITING, state(blocked, "context_node"));
        assertTrue(hasDirective(
                blocked, DirectiveType.REVALIDATE_GOVERNANCE, "context_node"));
        assertFalse(blocked.isContinuationAllowed());
        assertEquals(NodeRunState.READY, state(allowed, "context_node"));
        assertTrue(allowed.getDirectives().isEmpty());
        assertTrue(allowed.isContinuationAllowed());
        assertFalse(allowed.isExecutorDispatchEnabled());
    }

    @Test
    public void checkpointMismatchFailsStuck() {
        PersistentRun run = run(
                GraphRunState.WAITING,
                List.of(
                        node("context_node", "context.capture", NodeRunState.WAITING, SHA_A),
                        node("ready_node", "summary.render", NodeRunState.READY, "")),
                List.of(),
                List.of());

        Result result = new GraphRestartReconciler().reconcile(
                run,
                evidence(true, Map.of(SHA_A, CheckpointStatus.MISMATCH), Map.of()),
                NOW);

        assertEquals(GraphRunState.STUCK, result.getTargetGraphState());
        assertEquals(NodeRunState.STUCK, state(result, "context_node"));
        assertEquals(NodeRunState.STUCK, state(result, "ready_node"));
        assertTrue(hasDirective(
                result, DirectiveType.CHECKPOINT_MISMATCH, "context_node"));
        assertFalse(result.isContinuationAllowed());
    }

    @Test
    public void unknownEffectNeverRedispatchesAndRequiresTypedResolution() {
        PersistentRun run = run(
                GraphRunState.WAITING,
                List.of(node("effect_node", "effect.execute", NodeRunState.WAITING, SHA_A)),
                List.of(unknownEffect()),
                List.of());
        GraphRestartReconciler reconciler = new GraphRestartReconciler();

        Result unknown = reconciler.reconcile(
                run,
                evidence(false, Map.of(SHA_A, CheckpointStatus.VALID), Map.of()),
                NOW);
        Result applied = reconciler.reconcile(
                run,
                evidence(false, Map.of(SHA_A, CheckpointStatus.VALID),
                        Map.of(EFFECT_ID, EffectDeliveryStatus.CONFIRMED_APPLIED)),
                NOW);
        Result notApplied = reconciler.reconcile(
                run,
                evidence(false, Map.of(SHA_A, CheckpointStatus.VALID),
                        Map.of(EFFECT_ID, EffectDeliveryStatus.CONFIRMED_NOT_APPLIED)),
                NOW);

        assertTrue(hasDirective(unknown, DirectiveType.RECONCILE_EFFECT_STATUS, EFFECT_ID));
        assertTrue(hasDirective(applied, DirectiveType.VERIFY_EFFECT_READBACK, EFFECT_ID));
        assertTrue(hasDirective(
                notApplied, DirectiveType.RETRY_EFFECT_AFTER_POLICY, EFFECT_ID));
        assertFalse(unknown.isExecutorDispatchEnabled());
        assertFalse(applied.isContinuationAllowed());
        assertFalse(notApplied.isContinuationAllowed());
    }

    @Test
    public void expiredPlanFailsTerminalWithoutReplay() {
        PersistentRun run = new PersistentRun(
                PLAN_ID,
                SESSION_ID,
                1,
                GraphRunState.EXECUTING,
                SHA_A,
                SHA_B,
                SHA_C,
                NOW - 10_000L,
                NOW - 1_000L,
                NOW,
                List.of(node("effect_node", "effect.execute", NodeRunState.EXECUTING, SHA_D)),
                List.of(unknownEffect()),
                List.of());

        Result result = new GraphRestartReconciler().reconcile(
                run,
                evidence(false, Map.of(SHA_D, CheckpointStatus.VALID), Map.of()),
                NOW);

        assertEquals(GraphRunState.FAILED, result.getTargetGraphState());
        assertEquals(NodeRunState.STUCK, state(result, "effect_node"));
        assertTrue(hasDirective(
                result, DirectiveType.PLAN_DEADLINE_EXCEEDED, PLAN_ID));
        assertFalse(result.isExecutorDispatchEnabled());
    }

    @Test
    public void reconciliationDigestIsStableAcrossRecoveredReplay() {
        PersistentRun interrupted = run(
                GraphRunState.EXECUTING,
                List.of(node("effect_node", "effect.execute", NodeRunState.EXECUTING, SHA_A)),
                List.of(unknownEffect()),
                List.of());
        Evidence evidence = evidence(
                false, Map.of(SHA_A, CheckpointStatus.VALID), Map.of());
        Result first = new GraphRestartReconciler().reconcile(interrupted, evidence, NOW);
        PersistentRun reopened = run(
                GraphRunState.WAITING,
                List.of(node("effect_node", "effect.execute", NodeRunState.WAITING, SHA_A)),
                List.of(unknownEffect()),
                List.of());
        Result replay = new GraphRestartReconciler().reconcile(reopened, evidence, NOW + 1L);

        assertEquals(first.getResultDigest(), replay.getResultDigest());
        assertEquals(first.getTargetGraphState(), replay.getTargetGraphState());
        assertEquals(first.getDirectives().size(), replay.getDirectives().size());
    }

    @Test
    public void compensationAndApprovalAlwaysRequireFreshAuthority() {
        PersistentRun run = run(
                GraphRunState.EXECUTING,
                List.of(
                        node("approval_node", "approval.interrupt", NodeRunState.WAITING, SHA_A),
                        node("compensate_node", "compensate", NodeRunState.EXECUTING, SHA_B)),
                List.of(unknownEffect()),
                List.of(compensation(EffectContract.UNDO_REQUESTED, NOW + 5_000L)));

        Result result = new GraphRestartReconciler().reconcile(
                run,
                evidence(true, Map.of(SHA_A, CheckpointStatus.VALID,
                        SHA_B, CheckpointStatus.VALID), Map.of()),
                NOW);

        assertTrue(hasDirective(
                result, DirectiveType.REVALIDATE_APPROVAL, "approval_node"));
        assertTrue(hasDirective(
                result, DirectiveType.REVALIDATE_UNDO, "compensate_node"));
        assertTrue(hasDirective(result, DirectiveType.REVALIDATE_UNDO, COMPENSATION_ID));
        assertFalse(result.isContinuationAllowed());
    }

    @Test
    public void invalidAndOversizeDurableMaterialFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> new PersistentNode(
                "effect_node",
                "effect.execute",
                NodeRunState.WAITING,
                1,
                NOW + 5_000L,
                "effect:key",
                "bad",
                SHA_A,
                NOW));

        List<PersistentNode> nodes = new ArrayList<>();
        for (int index = 0; index <= PlanContract.MAX_NODES; index++) {
            nodes.add(node(
                    "node_" + index,
                    "context.capture",
                    NodeRunState.READY,
                    ""));
        }
        assertThrows(IllegalArgumentException.class, () -> run(
                GraphRunState.WAITING, nodes, List.of(), List.of()));
    }

    private static PersistentRun run(
            GraphRunState state,
            List<PersistentNode> nodes,
            List<PersistentEffect> effects,
            List<PersistentCompensation> compensations) {
        return new PersistentRun(
                PLAN_ID,
                SESSION_ID,
                1,
                state,
                SHA_A,
                SHA_B,
                SHA_C,
                NOW - 10_000L,
                NOW - 1_000L,
                NOW + 30_000L,
                nodes,
                effects,
                compensations);
    }

    private static PersistentNode node(
            String nodeId,
            String nodeType,
            NodeRunState state,
            String checkpointRef) {
        return new PersistentNode(
                nodeId,
                nodeType,
                state,
                state == NodeRunState.PENDING || state == NodeRunState.READY ? 0 : 1,
                NOW + 20_000L,
                "recover:" + nodeId,
                checkpointRef,
                SHA_D,
                NOW - 1_000L);
    }

    private static PersistentEffect unknownEffect() {
        return new PersistentEffect(
                EFFECT_ID,
                1,
                OBSERVATION_ID,
                SESSION_ID,
                EffectContract.STATE_UNKNOWN,
                EffectContract.SOURCE_ADAPTER,
                1,
                SHA_A,
                SHA_B,
                SHA_C,
                SHA_D,
                false,
                NOW - 2_000L);
    }

    private static PersistentCompensation compensation(int state, long expiry) {
        return new PersistentCompensation(
                COMPENSATION_ID,
                SESSION_ID,
                EFFECT_ID,
                "undo:recovery",
                SHA_A,
                SHA_B,
                state,
                expiry,
                NOW - 10_000L,
                NOW - 1_000L);
    }

    private static Evidence evidence(
            boolean governanceRevalidated,
            Map<String, CheckpointStatus> checkpoints,
            Map<String, EffectDeliveryStatus> effects) {
        return new Evidence(governanceRevalidated, checkpoints, effects);
    }

    private static NodeRunState state(Result result, String nodeId) {
        return result.getNodes().stream()
                .filter(node -> nodeId.equals(node.getNodeId()))
                .findFirst()
                .orElseThrow()
                .getTargetState();
    }

    private static boolean hasDirective(
            Result result,
            DirectiveType type,
            String subjectId) {
        return result.getDirectives().stream().anyMatch(
                directive -> directive.getType() == type
                        && subjectId.equals(directive.getSubjectId()));
    }
}
