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
import com.centralbrain.runtime.tools.ToolRegistry.RegistrationException;
import com.centralbrain.runtime.tools.ToolResolver.FailureCode;
import com.centralbrain.runtime.tools.ToolResolver.Query;
import com.centralbrain.runtime.tools.ToolResolver.RegistrationState;
import com.centralbrain.runtime.tools.ToolResolver.Resolution;
import com.centralbrain.runtime.tools.ToolResolver.ResolutionState;
import com.centralbrain.runtime.tools.ToolResolver.UsabilityState;

import java.util.List;

public final class ToolRegistryProbeActivity extends Activity {
    private static final String TAG = "CbToolRegistry";
    private static final String FAMILY = "tool.cockpit.hvac.set";
    private static final String CAPABILITY = "vehicle.hvac.temperature";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            ToolManifest first = manifest(1, RiskClass.MEDIUM);
            ToolManifest second = manifest(2, RiskClass.MEDIUM);
            ToolRegistry forward = new ToolRegistry(List.of(first, second, first));
            ToolRegistry reverse = new ToolRegistry(List.of(second, first));
            ToolResolver resolver = new ToolResolver(forward);

            boolean contractDefined = forward.size() == 2
                    && forward.isRegistered(FAMILY, 1)
                    && forward.isRegistered(FAMILY, 2);
            boolean digestVerified = forward.getRegistryDigest().matches("[0-9a-f]{64}")
                    && forward.getRegistryDigest().equals(reverse.getRegistryDigest());
            boolean conflictRejected = rejectsConflict(
                    first, manifest(1, RiskClass.HIGH));

            ToolHealthSnapshot healthy = new ToolHealthSnapshot(List.of(
                    observation(first, State.HEALTHY, 9_000L, 1L),
                    observation(second, State.HEALTHY, 9_500L, 2L)));
            Resolution selected = resolver.resolve(
                    new Query(FAMILY, 1, 2, CAPABILITY, null),
                    healthy,
                    10_000L);
            boolean highestVersionDeterministic = selected.getRegistrationState()
                    == RegistrationState.REGISTERED
                    && selected.getResolutionState() == ResolutionState.RESOLVED
                    && selected.getUsabilityState() == UsabilityState.USABLE
                    && selected.getManifest().getVersion() == 2
                    && !selected.isExecutionEnabled();

            Resolution absent = resolver.resolve(
                    new Query(
                            "tool.cockpit.seat.set",
                            1,
                            1,
                            "vehicle.seat.position",
                            null),
                    ToolHealthSnapshot.empty(),
                    10_000L);
            Resolution missingHealth = resolver.resolve(
                    new Query(FAMILY, 1, 1, CAPABILITY, null),
                    ToolHealthSnapshot.empty(),
                    10_000L);
            boolean statesSeparated = absent.getRegistrationState()
                    == RegistrationState.NOT_REGISTERED
                    && absent.getResolutionState() == ResolutionState.NOT_RESOLVED
                    && absent.getUsabilityState() == UsabilityState.NOT_USABLE
                    && missingHealth.getRegistrationState() == RegistrationState.REGISTERED
                    && missingHealth.getResolutionState() == ResolutionState.RESOLVED
                    && missingHealth.getUsabilityState() == UsabilityState.NOT_USABLE
                    && missingHealth.getFailureCode() == FailureCode.HEALTH_MISSING;

            ToolHealthSnapshot mixed = new ToolHealthSnapshot(List.of(
                    observation(first, State.HEALTHY, 9_500L, 1L),
                    observation(second, State.UNHEALTHY, 9_500L, 2L)));
            Resolution noFallback = resolver.resolve(
                    new Query(FAMILY, 1, 2, CAPABILITY, null),
                    mixed,
                    10_000L);
            boolean unhealthyNoFallback = noFallback.getManifest().getVersion() == 2
                    && noFallback.getUsabilityState() == UsabilityState.NOT_USABLE
                    && noFallback.getFailureCode() == FailureCode.HEALTH_UNHEALTHY
                    && !noFallback.isExecutionEnabled();

            Resolution stale = resolver.resolve(
                    new Query(FAMILY, 1, 1, CAPABILITY, null),
                    new ToolHealthSnapshot(List.of(
                            observation(first, State.HEALTHY, 1_000L, 1L))),
                    10_000L);
            Resolution future = resolver.resolve(
                    new Query(FAMILY, 1, 1, CAPABILITY, null),
                    new ToolHealthSnapshot(List.of(
                            observation(first, State.HEALTHY, 11_000L, 1L))),
                    10_000L);
            boolean healthFailClosed = stale.getFailureCode() == FailureCode.HEALTH_STALE
                    && future.getFailureCode() == FailureCode.HEALTH_CLOCK_INVALID;
            boolean android13Arm64 = Build.VERSION.SDK_INT == 33
                    && Build.SUPPORTED_ABIS.length > 0
                    && Build.SUPPORTED_ABIS[0].startsWith("arm64");
            boolean complete = contractDefined
                    && digestVerified
                    && conflictRejected
                    && highestVersionDeterministic
                    && statesSeparated
                    && unhealthyNoFallback
                    && healthFailClosed
                    && android13Arm64;

            Log.i(TAG, "nonce=" + nonce
                    + " tool_registry_probe_complete=" + complete
                    + " tool_registry_contract_defined=" + contractDefined
                    + " tool_resolver_contract_defined=" + contractDefined
                    + " tool_health_dynamic_snapshot_defined=" + contractDefined
                    + " tool_registry_probe_registration_count=" + forward.size()
                    + " tool_registry_digest_verified=" + digestVerified
                    + " tool_registry_version_conflict_rejected=" + conflictRejected
                    + " tool_resolver_highest_version_deterministic="
                    + highestVersionDeterministic
                    + " tool_resolver_states_separated=" + statesSeparated
                    + " tool_resolver_unhealthy_no_fallback=" + unhealthyNoFallback
                    + " tool_health_fail_closed=" + healthFailClosed
                    + " tool_registry_android13_arm64_verified=" + android13Arm64
                    + " tool_registry_published=false"
                    + " tool_resolver_published=false"
                    + " tool_registry_runtime_wired=false"
                    + " tool_execution_enabled=false"
                    + " production_tool_registered=false"
                    + " effect_dispatch_enabled=false"
                    + " vehicle_readback_accessed=false"
                    + " npu_accessed=false"
                    + " network_accessed=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " tool_registry_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " tool_registry_published=false"
                    + " tool_execution_enabled=false"
                    + " hardware_accessed=false", exception);
        }
    }

    private static boolean rejectsConflict(ToolManifest first, ToolManifest second) {
        try {
            new ToolRegistry(List.of(first, second));
            return false;
        } catch (RegistrationException exception) {
            return exception.getErrorCode() == ToolRegistry.ErrorCode.CONTRACT_CONFLICT;
        }
    }

    private static Observation observation(
            ToolManifest manifest, State state, long observedAt, long revision) {
        return new Observation(
                manifest.getHealthContract().getCheckId(),
                state,
                observedAt,
                revision);
    }

    private static ToolManifest manifest(int version, RiskClass riskClass) {
        ObjectSchema input = new ObjectSchema(
                "tool.input.hvac-set.v" + version,
                version,
                512,
                List.of(
                        FieldSchema.sha256DigestField("requestDigest", true),
                        FieldSchema.integerField(
                                "temperatureDeciC", true, 160L, 300L)));
        ObjectSchema output = new ObjectSchema(
                "tool.output.hvac-set.v" + version,
                version,
                256,
                List.of(FieldSchema.stringField("status", true, 16)));
        return new ToolManifest(
                ToolManifest.SCHEMA_VERSION,
                FAMILY + ".v" + version,
                version,
                "runtime.builtin",
                input,
                output,
                CAPABILITY,
                riskClass,
                2_500L,
                IdempotencyMode.TOKEN_REQUIRED,
                new HealthContract(
                        "health.vehicle.hvac.v" + version, 5_000L, true));
    }
}
