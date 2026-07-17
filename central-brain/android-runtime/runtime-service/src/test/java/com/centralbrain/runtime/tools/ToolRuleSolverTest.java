package com.centralbrain.runtime.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.tools.ToolHealthSnapshot.Observation;
import com.centralbrain.runtime.tools.ToolHealthSnapshot.State;
import com.centralbrain.runtime.tools.ToolManifest.FieldSchema;
import com.centralbrain.runtime.tools.ToolManifest.HealthContract;
import com.centralbrain.runtime.tools.ToolManifest.IdempotencyMode;
import com.centralbrain.runtime.tools.ToolManifest.ObjectSchema;
import com.centralbrain.runtime.tools.ToolManifest.RiskClass;
import com.centralbrain.runtime.tools.ToolRuleSet.ChildRule;
import com.centralbrain.runtime.tools.ToolRuleSet.ConditionalRule;
import com.centralbrain.runtime.tools.ToolRuleSet.ErrorCode;
import com.centralbrain.runtime.tools.ToolRuleSet.RuleException;
import com.centralbrain.runtime.tools.ToolRuleSet.RuleType;
import com.centralbrain.runtime.tools.ToolRuleSolver.ConditionObservation;
import com.centralbrain.runtime.tools.ToolRuleSolver.ConditionSnapshot;
import com.centralbrain.runtime.tools.ToolRuleSolver.ConditionState;
import com.centralbrain.runtime.tools.ToolRuleSolver.FailureCode;
import com.centralbrain.runtime.tools.ToolRuleSolver.Request;
import com.centralbrain.runtime.tools.ToolRuleSolver.Result;
import com.centralbrain.runtime.tools.ToolRuleSolver.Selection;

import org.junit.Test;

import java.util.Collections;
import java.util.List;

public final class ToolRuleSolverTest {
    private static final String CONTEXT = "tool.cockpit.context.read";
    private static final String HVAC = "tool.cockpit.hvac.set";
    private static final String SEAT = "tool.cockpit.seat.set";
    private static final String FINISH = "tool.cockpit.workflow.finish";
    private static final String PARKED = "condition.vehicle.parked";

    @Test
    public void ruleSetIsBoundedImmutableAndDeterministic() {
        ToolRuleSet first = rules();
        ToolRuleSet reordered = new ToolRuleSet(
                List.of(FINISH, SEAT, HVAC, CONTEXT),
                List.of(CONTEXT),
                List.of(
                        new ChildRule(SEAT, FINISH),
                        new ChildRule(HVAC, SEAT),
                        new ChildRule(CONTEXT, SEAT),
                        new ChildRule(CONTEXT, HVAC)),
                List.of(new ConditionalRule(SEAT, PARKED, true)),
                List.of(FINISH),
                List.of(SEAT, HVAC),
                List.of(SEAT));

        assertEquals(6, first.getDefinedRuleTypes().size());
        assertTrue(first.getDefinedRuleTypes().containsAll(List.of(RuleType.values())));
        assertEquals(first.getRuleSetDigest(), reordered.getRuleSetDigest());
        assertTrue(first.getRuleSetDigest().matches("[0-9a-f]{64}"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> first.getCatalogFamilies().clear());
        assertRuleCode(
                ErrorCode.FAMILY_LIMIT_EXCEEDED,
                () -> new ToolRuleSet(
                        Collections.nCopies(129, CONTEXT),
                        List.of(CONTEXT),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()));
        assertRuleCode(
                ErrorCode.TERMINAL_HAS_CHILD,
                () -> new ToolRuleSet(
                        List.of(CONTEXT, FINISH),
                        List.of(CONTEXT),
                        List.of(new ChildRule(FINISH, CONTEXT)),
                        List.of(),
                        List.of(FINISH),
                        List.of(),
                        List.of()));
    }

    @Test
    public void initChildAndConditionalRulesIntersectModelSelection() {
        ToolRuleSolver solver = new ToolRuleSolver(rules());
        List<ToolResolver.Resolution> usable = usableResolutions(CONTEXT, HVAC, SEAT, FINISH);

        Result initial = solver.solve(
                new Request(
                        null,
                        List.of(SEAT, CONTEXT),
                        List.of(),
                        ConditionSnapshot.empty()),
                usable);
        assertAllowedFamilies(initial, CONTEXT);

        Result child = solver.solve(
                new Request(
                        CONTEXT,
                        List.of(SEAT, HVAC),
                        List.of(CONTEXT),
                        new ConditionSnapshot(List.of(
                                new ConditionObservation(PARKED, ConditionState.TRUE)))),
                usable);
        assertAllowedFamilies(child, HVAC, SEAT);

        Result unknownCondition = new ToolRuleSolver(conditionalOnlyRules()).solve(
                new Request(
                        CONTEXT,
                        List.of(SEAT),
                        List.of(CONTEXT),
                        ConditionSnapshot.empty()),
                usable);
        assertEquals(FailureCode.CONDITION_UNSATISFIED, unknownCondition.getFailureCode());
        assertFalse(unknownCondition.isAllowed());
    }

    @Test
    public void emptyModelIntersectionFailsClosedWithoutFallback() {
        ToolRuleSolver solver = new ToolRuleSolver(rules());
        Result result = solver.solve(
                new Request(
                        CONTEXT,
                        List.of("tool.cockpit.media.play"),
                        List.of(CONTEXT),
                        ConditionSnapshot.empty()),
                usableResolutions(HVAC));

        assertEquals(FailureCode.MODEL_INTERSECTION_EMPTY, result.getFailureCode());
        assertTrue(result.getSelections().isEmpty());
        assertFalse(result.isExecutionEnabled());
    }

    @Test
    public void terminalRequiresCompletedToolsAndStopsChildren() {
        ToolRuleSolver solver = new ToolRuleSolver(rules());
        List<ToolResolver.Resolution> usable = usableResolutions(FINISH);

        Result incomplete = solver.solve(
                new Request(
                        SEAT,
                        List.of(FINISH),
                        List.of(CONTEXT, SEAT),
                        ConditionSnapshot.empty()),
                usable);
        assertEquals(
                FailureCode.REQUIRED_BEFORE_EXIT_INCOMPLETE,
                incomplete.getFailureCode());

        Result complete = solver.solve(
                new Request(
                        SEAT,
                        List.of(FINISH),
                        List.of(CONTEXT, HVAC, SEAT),
                        ConditionSnapshot.empty()),
                usable);
        assertAllowedFamilies(complete, FINISH);

        Result afterTerminal = solver.solve(
                new Request(
                        FINISH,
                        List.of(HVAC),
                        List.of(CONTEXT, HVAC, SEAT, FINISH),
                        ConditionSnapshot.empty()),
                usableResolutions(HVAC));
        assertEquals(FailureCode.TERMINAL_REACHED, afterTerminal.getFailureCode());
    }

    @Test
    public void approvalIsAnnotatedButNeverGrantedAndUnusableIsExcluded() {
        ToolRuleSolver solver = new ToolRuleSolver(rules());
        ToolManifest hvac = manifest(HVAC);
        ToolManifest seat = manifest(SEAT);
        List<ToolResolver.Resolution> resolutions = List.of(
                usable(hvac),
                unusable(seat));

        Result mixed = solver.solve(
                new Request(
                        CONTEXT,
                        List.of(HVAC, SEAT),
                        List.of(CONTEXT),
                        new ConditionSnapshot(List.of(
                                new ConditionObservation(PARKED, ConditionState.TRUE)))),
                resolutions);
        assertAllowedFamilies(mixed, HVAC);

        Result approval = solver.solve(
                new Request(
                        CONTEXT,
                        List.of(SEAT),
                        List.of(CONTEXT),
                        new ConditionSnapshot(List.of(
                                new ConditionObservation(PARKED, ConditionState.TRUE)))),
                List.of(usable(seat)));
        Selection selection = approval.getSelections().get(0);
        assertTrue(selection.isApprovalRequired());
        assertFalse(selection.isApprovalGranted());
        assertFalse(selection.isExecutionEnabled());
        assertFalse(approval.isExecutionEnabled());

        Result noUsable = solver.solve(
                new Request(
                        CONTEXT,
                        List.of(SEAT),
                        List.of(CONTEXT),
                        new ConditionSnapshot(List.of(
                                new ConditionObservation(PARKED, ConditionState.TRUE)))),
                List.of(unusable(seat)));
        assertEquals(FailureCode.NO_USABLE_TOOL, noUsable.getFailureCode());
    }

    private static ToolRuleSet rules() {
        return new ToolRuleSet(
                List.of(CONTEXT, HVAC, SEAT, FINISH),
                List.of(CONTEXT),
                List.of(
                        new ChildRule(CONTEXT, HVAC),
                        new ChildRule(CONTEXT, SEAT),
                        new ChildRule(HVAC, SEAT),
                        new ChildRule(SEAT, FINISH)),
                List.of(new ConditionalRule(SEAT, PARKED, true)),
                List.of(FINISH),
                List.of(HVAC, SEAT),
                List.of(SEAT));
    }

    private static ToolRuleSet conditionalOnlyRules() {
        return new ToolRuleSet(
                List.of(CONTEXT, SEAT),
                List.of(CONTEXT),
                List.of(new ChildRule(CONTEXT, SEAT)),
                List.of(new ConditionalRule(SEAT, PARKED, true)),
                List.of(),
                List.of(),
                List.of(SEAT));
    }

    private static List<ToolResolver.Resolution> usableResolutions(String... families) {
        return java.util.Arrays.stream(families)
                .map(ToolRuleSolverTest::manifest)
                .map(ToolRuleSolverTest::usable)
                .toList();
    }

    private static ToolResolver.Resolution usable(ToolManifest manifest) {
        ToolResolver resolver = new ToolResolver(new ToolRegistry(List.of(manifest)));
        return resolver.resolve(
                new ToolResolver.Query(
                        manifest.getFamilyId(),
                        1,
                        1,
                        manifest.getCapabilityId(),
                        manifest.getContractDigest()),
                new ToolHealthSnapshot(List.of(new Observation(
                        manifest.getHealthContract().getCheckId(),
                        State.HEALTHY,
                        9_000L,
                        1L))),
                10_000L);
    }

    private static ToolResolver.Resolution unusable(ToolManifest manifest) {
        ToolResolver resolver = new ToolResolver(new ToolRegistry(List.of(manifest)));
        return resolver.resolve(
                new ToolResolver.Query(
                        manifest.getFamilyId(),
                        1,
                        1,
                        manifest.getCapabilityId(),
                        manifest.getContractDigest()),
                ToolHealthSnapshot.empty(),
                10_000L);
    }

    private static ToolManifest manifest(String family) {
        String suffix = family.substring("tool.cockpit.".length()).replace('.', '-');
        ObjectSchema input = new ObjectSchema(
                "tool.input." + suffix + ".v1",
                1,
                256,
                List.of(FieldSchema.sha256DigestField("requestDigest", true)));
        ObjectSchema output = new ObjectSchema(
                "tool.output." + suffix + ".v1",
                1,
                256,
                List.of(FieldSchema.stringField("status", true, 16)));
        return new ToolManifest(
                ToolManifest.SCHEMA_VERSION,
                family + ".v1",
                1,
                "runtime.builtin",
                input,
                output,
                "runtime." + suffix.replace('-', '.'),
                RiskClass.MEDIUM,
                2_500L,
                IdempotencyMode.TOKEN_REQUIRED,
                new HealthContract("health." + suffix + ".v1", 5_000L, true));
    }

    private static void assertAllowedFamilies(Result result, String... expected) {
        assertEquals(FailureCode.NONE, result.getFailureCode());
        assertTrue(result.isAllowed());
        assertEquals(
                List.of(expected),
                result.getSelections().stream()
                        .map(selection -> selection.getManifest().getFamilyId())
                        .toList());
        assertFalse(result.isExecutionEnabled());
    }

    private static void assertRuleCode(
            ErrorCode expected, org.junit.function.ThrowingRunnable runnable) {
        RuleException exception = assertThrows(RuleException.class, runnable);
        assertEquals(expected, exception.getErrorCode());
    }
}
