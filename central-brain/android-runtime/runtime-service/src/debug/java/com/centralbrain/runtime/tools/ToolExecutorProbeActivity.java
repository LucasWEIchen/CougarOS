package com.centralbrain.runtime.tools;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.tools.InProcessBuiltInToolExecutor.AllowlistEntry;
import com.centralbrain.runtime.tools.InProcessBuiltInToolExecutor.ConstructionException;
import com.centralbrain.runtime.tools.InProcessBuiltInToolExecutor.Registration;
import com.centralbrain.runtime.tools.ToolHealthSnapshot.Observation;
import com.centralbrain.runtime.tools.ToolHealthSnapshot.State;
import com.centralbrain.runtime.tools.ToolManifest.FieldSchema;
import com.centralbrain.runtime.tools.ToolManifest.HealthContract;
import com.centralbrain.runtime.tools.ToolManifest.IdempotencyMode;
import com.centralbrain.runtime.tools.ToolManifest.ObjectSchema;
import com.centralbrain.runtime.tools.ToolManifest.RiskClass;

import java.util.List;
import java.util.Map;

public final class ToolExecutorProbeActivity extends Activity {
    private static final String TAG = "CbToolExecutor";
    private static final String FAMILY = "tool.runtime.echo";
    private static final String CAPABILITY = "runtime.tool.echo";
    private static final String SIGNER = "a".repeat(64);
    private static final String ARTIFACT = "b".repeat(64);
    private static final String REQUEST = "c".repeat(64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            ToolManifest manifest = manifest();
            ManualClock clock = new ManualClock(10_000L);
            InProcessBuiltInToolExecutor executor = executor(
                    manifest,
                    clock,
                    (input, control) -> {
                        control.checkpoint();
                        return Map.of("status", "ok");
                    });
            boolean contextDefined = !context(manifest, REQUEST, 256).isProductionAuthority()
                    && context(manifest, REQUEST, 256).getSchemaVersion()
                            == ToolInvocationContext.SCHEMA_VERSION;
            boolean allowlistEnforced = rejectsSignerMismatch(manifest, clock)
                    && !executor.isProductionWired()
                    && !executor.isOsVirtualizationEnabled();

            ToolExecutor.ExecutionResult success = executor.execute(
                    selection(manifest, false),
                    context(manifest, REQUEST, 256),
                    Map.of("requestDigest", REQUEST),
                    () -> false);
            boolean executionSuccess = success.isSuccess()
                    && success.getOutput().equals(Map.of("status", "ok"))
                    && success.getAudit().getAuditDigest().matches("[0-9a-f]{64}");

            ToolExecutor.ExecutionResult approval = executor.execute(
                    selection(manifest, true),
                    context(manifest, "d".repeat(64), 256),
                    Map.of("requestDigest", REQUEST),
                    () -> false);
            ToolExecutor.ExecutionResult cancelled = executor.execute(
                    selection(manifest, false),
                    context(manifest, "e".repeat(64), 256),
                    Map.of("requestDigest", REQUEST),
                    () -> true);
            clock.set(11_000L);
            ToolExecutor.ExecutionResult timedOut = executor.execute(
                    selection(manifest, false),
                    context(manifest, "f".repeat(64), 256),
                    Map.of("requestDigest", REQUEST),
                    () -> false);
            clock.set(10_000L);
            boolean deadlineCancelVerified = approval.getFailureCode()
                    == ToolExecutor.FailureCode.APPROVAL_REQUIRED
                    && cancelled.getOutcome() == ToolExecutor.Outcome.CANCELLED
                    && timedOut.getOutcome() == ToolExecutor.Outcome.TIMED_OUT;

            InProcessBuiltInToolExecutor oversizedExecutor = executor(
                    manifest,
                    clock,
                    (input, control) -> Map.of("status", "0123456789"));
            ToolExecutor.ExecutionResult oversized = oversizedExecutor.execute(
                    selection(manifest, false),
                    context(manifest, "9".repeat(64), 10),
                    Map.of("requestDigest", REQUEST),
                    () -> false);
            boolean outputLimitVerified = oversized.getFailureCode()
                    == ToolExecutor.FailureCode.OUTPUT_TOO_LARGE
                    && oversized.getOutput().isEmpty();

            InProcessBuiltInToolExecutor boundedAudit = executor(
                    manifest, clock, (input, control) -> Map.of("status", "ok"));
            for (int index = 0; index < 129; index++) {
                boundedAudit.execute(
                        selection(manifest, false),
                        context(manifest, String.format("%064x", index + 1), 256),
                        Map.of("requestDigest", REQUEST),
                        () -> false);
            }
            boolean auditBounded = boundedAudit.recentAudits(128).size() == 128
                    && boundedAudit.recentAudits(128).get(0).getSequence() == 2L
                    && boundedAudit.getAuditEvictionCount() == 1L;
            boolean android13Arm64 = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");
            boolean complete = contextDefined
                    && allowlistEnforced
                    && executionSuccess
                    && deadlineCancelVerified
                    && outputLimitVerified
                    && auditBounded
                    && android13Arm64;

            Log.i(TAG, "nonce=" + nonce
                    + " tool_executor_probe_complete=" + complete
                    + " tool_executor_contract_defined=true"
                    + " tool_invocation_context_defined=" + contextDefined
                    + " built_in_allowlist_enforced=" + allowlistEnforced
                    + " built_in_signer_artifact_bound=" + allowlistEnforced
                    + " tool_executor_success_verified=" + executionSuccess
                    + " tool_executor_deadline_cancel_verified=" + deadlineCancelVerified
                    + " tool_executor_output_limit_verified=" + outputLimitVerified
                    + " tool_executor_audit_bounded_verified=" + auditBounded
                    + " tool_executor_android13_arm64_verified=" + android13Arm64
                    + " tool_executor_runtime_wired=false"
                    + " tool_execution_enabled=false"
                    + " production_tool_execution_enabled=false"
                    + " production_tool_registered=false"
                    + " tool_approval_authority_available=false"
                    + " os_virtualization_enabled=false"
                    + " subprocess_started=false"
                    + " dynamic_class_loading_enabled=false"
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
                    + " tool_executor_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " tool_executor_runtime_wired=false"
                    + " tool_execution_enabled=false"
                    + " production_tool_execution_enabled=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static boolean rejectsSignerMismatch(ToolManifest manifest, ManualClock clock) {
        try {
            new InProcessBuiltInToolExecutor(
                    List.of(allowlist(manifest)),
                    List.of(registration(
                            manifest,
                            (input, control) -> Map.of("status", "ok"))),
                    "0".repeat(64),
                    clock);
            return false;
        } catch (ConstructionException exception) {
            return exception.getErrorCode()
                    == InProcessBuiltInToolExecutor.ConstructionError
                            .CURRENT_SIGNER_NOT_ALLOWLISTED;
        }
    }

    private static InProcessBuiltInToolExecutor executor(
            ToolManifest manifest,
            ManualClock clock,
            ToolExecutor.BuiltInTool implementation) {
        return new InProcessBuiltInToolExecutor(
                List.of(allowlist(manifest)),
                List.of(registration(manifest, implementation)),
                SIGNER,
                clock);
    }

    private static AllowlistEntry allowlist(ToolManifest manifest) {
        return new AllowlistEntry(
                manifest.getFamilyId(),
                manifest.getContractDigest(),
                SIGNER,
                ARTIFACT);
    }

    private static Registration registration(
            ToolManifest manifest, ToolExecutor.BuiltInTool implementation) {
        return new Registration(manifest, SIGNER, ARTIFACT, implementation);
    }

    private static ToolInvocationContext context(
            ToolManifest manifest, String invocationDigest, int outputLimit) {
        return new ToolInvocationContext(
                ToolInvocationContext.SCHEMA_VERSION,
                invocationDigest,
                "1".repeat(64),
                "2".repeat(64),
                "3".repeat(64),
                "4".repeat(64),
                manifest.getFamilyId(),
                manifest.getContractDigest(),
                manifest.getCapabilityId(),
                "5".repeat(64),
                9_000L,
                11_000L,
                outputLimit);
    }

    private static ToolRuleSolver.Selection selection(
            ToolManifest manifest, boolean approvalRequired) {
        ToolResolver.Resolution resolution = new ToolResolver(
                new ToolRegistry(List.of(manifest))).resolve(
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
        ToolRuleSet ruleSet = new ToolRuleSet(
                List.of(manifest.getFamilyId()),
                List.of(manifest.getFamilyId()),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                approvalRequired ? List.of(manifest.getFamilyId()) : List.of());
        return new ToolRuleSolver(ruleSet).solve(
                new ToolRuleSolver.Request(
                        null,
                        List.of(manifest.getFamilyId()),
                        List.of(),
                        ToolRuleSolver.ConditionSnapshot.empty()),
                List.of(resolution)).getSelections().get(0);
    }

    private static ToolManifest manifest() {
        ObjectSchema input = new ObjectSchema(
                "tool.input.runtime-echo.v1",
                1,
                256,
                List.of(FieldSchema.sha256DigestField("requestDigest", true)));
        ObjectSchema output = new ObjectSchema(
                "tool.output.runtime-echo.v1",
                1,
                256,
                List.of(FieldSchema.stringField("status", true, 64)));
        return new ToolManifest(
                ToolManifest.SCHEMA_VERSION,
                FAMILY + ".v1",
                1,
                "runtime.builtin",
                input,
                output,
                CAPABILITY,
                RiskClass.LOW,
                2_500L,
                IdempotencyMode.TOKEN_REQUIRED,
                new HealthContract("health.runtime.echo.v1", 5_000L, true));
    }

    private static final class ManualClock
            implements InProcessBuiltInToolExecutor.ElapsedRealtimeClock {
        private long now;

        private ManualClock(long now) {
            this.now = now;
        }

        @Override
        public long nowMs() {
            return now;
        }

        private void set(long now) {
            this.now = now;
        }
    }
}
