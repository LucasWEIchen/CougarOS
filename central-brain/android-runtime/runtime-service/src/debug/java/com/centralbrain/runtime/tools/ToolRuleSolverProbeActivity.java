package com.centralbrain.runtime.tools;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.tools.ToolHealthSnapshot.Observation;
import com.centralbrain.runtime.tools.ToolHealthSnapshot.State;
import com.centralbrain.runtime.tools.ToolManifest.FieldSchema;
import com.centralbrain.runtime.tools.ToolManifest.HealthContract;
import com.centralbrain.runtime.tools.ToolManifest.IdempotencyMode;
import com.centralbrain.runtime.tools.ToolManifest.ObjectSchema;
import com.centralbrain.runtime.tools.ToolManifest.RiskClass;
import com.centralbrain.runtime.tools.ToolRuleSet.ChildRule;
import com.centralbrain.runtime.tools.ToolRuleSet.ConditionalRule;
import com.centralbrain.runtime.tools.ToolRuleSolver.ConditionObservation;
import com.centralbrain.runtime.tools.ToolRuleSolver.ConditionSnapshot;
import com.centralbrain.runtime.tools.ToolRuleSolver.ConditionState;
import com.centralbrain.runtime.tools.ToolRuleSolver.FailureCode;
import com.centralbrain.runtime.tools.ToolRuleSolver.Request;
import com.centralbrain.runtime.tools.ToolRuleSolver.Result;

import java.util.List;

public final class ToolRuleSolverProbeActivity extends Activity {
    private static final String TAG = "CbToolRuleSolver";
    private static final String CONTEXT = "tool.cockpit.context.read";
    private static final String HVAC = "tool.cockpit.hvac.set";
    private static final String SEAT = "tool.cockpit.seat.set";
    private static final String FINISH = "tool.cockpit.workflow.finish";
    private static final String PARKED = "condition.vehicle.parked";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            ToolRuleSet rules = ruleSet();
            ToolRuleSet reorderedRules = reorderedRuleSet();
            ToolRuleSolver solver = new ToolRuleSolver(rules);
            List<ToolResolver.Resolution> usable = List.of(
                    usable(manifest(CONTEXT)),
                    usable(manifest(HVAC)),
                    usable(manifest(SEAT)),
                    usable(manifest(FINISH)));

            boolean ruleSetDefined = rules.getDefinedRuleTypes().size() == 6;
            boolean ruleSetDigestVerified = rules.getRuleSetDigest().matches("[0-9a-f]{64}")
                    && rules.getRuleSetDigest().equals(reorderedRules.getRuleSetDigest());
            Result initial = solver.solve(
                    new Request(
                            null,
                            List.of(SEAT, CONTEXT),
                            List.of(),
                            ConditionSnapshot.empty()),
                    usable);
            Result conditionalChild = solver.solve(
                    new Request(
                            CONTEXT,
                            List.of(SEAT, HVAC),
                            List.of(CONTEXT),
                            new ConditionSnapshot(List.of(
                                    new ConditionObservation(PARKED, ConditionState.TRUE)))),
                    usable);
            Result missingCondition = new ToolRuleSolver(conditionalOnlyRuleSet()).solve(
                    new Request(
                            CONTEXT,
                            List.of(SEAT),
                            List.of(CONTEXT),
                            ConditionSnapshot.empty()),
                    usable);
            boolean initChildConditional = initial.isAllowed()
                    && initial.getSelections().size() == 1
                    && initial.getSelections().get(0).getManifest().getFamilyId()
                            .equals(CONTEXT)
                    && conditionalChild.isAllowed()
                    && conditionalChild.getSelections().size() == 2
                    && !missingCondition.isAllowed()
                    && missingCondition.getFailureCode() == FailureCode.CONDITION_UNSATISFIED
                    && missingCondition.getSelections().isEmpty();

            Result modelEmpty = solver.solve(
                    new Request(
                            CONTEXT,
                            List.of("tool.cockpit.media.play"),
                            List.of(CONTEXT),
                            ConditionSnapshot.empty()),
                    usable);
            boolean modelIntersectionFailClosed = !modelEmpty.isAllowed()
                    && modelEmpty.getFailureCode() == FailureCode.MODEL_INTERSECTION_EMPTY
                    && modelEmpty.getSelections().isEmpty();

            Result exitBlocked = solver.solve(
                    new Request(
                            SEAT,
                            List.of(FINISH),
                            List.of(CONTEXT, SEAT),
                            ConditionSnapshot.empty()),
                    usable);
            Result exitAllowed = solver.solve(
                    new Request(
                            SEAT,
                            List.of(FINISH),
                            List.of(CONTEXT, HVAC, SEAT),
                            ConditionSnapshot.empty()),
                    usable);
            Result afterTerminal = solver.solve(
                    new Request(
                            FINISH,
                            List.of(HVAC),
                            List.of(CONTEXT, HVAC, SEAT, FINISH),
                            ConditionSnapshot.empty()),
                    usable);
            boolean terminalVerified = exitBlocked.getFailureCode()
                    == FailureCode.REQUIRED_BEFORE_EXIT_INCOMPLETE
                    && exitAllowed.isAllowed()
                    && afterTerminal.getFailureCode() == FailureCode.TERMINAL_REACHED;

            Result approval = solver.solve(
                    new Request(
                            CONTEXT,
                            List.of(SEAT),
                            List.of(CONTEXT),
                            new ConditionSnapshot(List.of(
                                    new ConditionObservation(PARKED, ConditionState.TRUE)))),
                    usable);
            boolean approvalFailClosed = approval.isAllowed()
                    && approval.getSelections().get(0).isApprovalRequired()
                    && !approval.getSelections().get(0).isApprovalGranted()
                    && !approval.getSelections().get(0).isExecutionEnabled()
                    && !approval.isExecutionEnabled();
            boolean android13Arm64 = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");
            boolean complete = ruleSetDefined
                    && ruleSetDigestVerified
                    && initChildConditional
                    && modelIntersectionFailClosed
                    && terminalVerified
                    && approvalFailClosed
                    && android13Arm64;

            Log.i(TAG, "nonce=" + nonce
                    + " tool_rule_solver_probe_complete=" + complete
                    + " tool_rule_set_contract_defined=" + ruleSetDefined
                    + " tool_rule_type_count=" + rules.getDefinedRuleTypes().size()
                    + " tool_rule_set_digest_verified=" + ruleSetDigestVerified
                    + " tool_rule_init_child_conditional_verified=" + initChildConditional
                    + " tool_rule_model_intersection_fail_closed="
                    + modelIntersectionFailClosed
                    + " tool_rule_terminal_requirements_verified=" + terminalVerified
                    + " tool_rule_approval_annotation_fail_closed=" + approvalFailClosed
                    + " tool_rule_solver_android13_arm64_verified=" + android13Arm64
                    + " tool_rule_solver_published=false"
                    + " tool_rule_solver_runtime_wired=false"
                    + " tool_approval_authority_available=false"
                    + " tool_execution_enabled=false"
                    + " production_tool_registered=false"
                    + " effect_dispatch_enabled=false"
                    + " vehicle_readback_accessed=false"
                    + " model_invoked=false"
                    + " npu_accessed=false"
                    + " network_accessed=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " tool_rule_solver_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " tool_rule_solver_published=false"
                    + " tool_execution_enabled=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static ToolRuleSet ruleSet() {
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

    private static ToolRuleSet reorderedRuleSet() {
        return new ToolRuleSet(
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
    }

    private static ToolRuleSet conditionalOnlyRuleSet() {
        return new ToolRuleSet(
                List.of(CONTEXT, SEAT),
                List.of(CONTEXT),
                List.of(new ChildRule(CONTEXT, SEAT)),
                List.of(new ConditionalRule(SEAT, PARKED, true)),
                List.of(),
                List.of(),
                List.of(SEAT));
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
}
