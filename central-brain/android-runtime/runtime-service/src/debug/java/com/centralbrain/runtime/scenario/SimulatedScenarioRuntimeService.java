package com.centralbrain.runtime.scenario;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import com.centralbrain.runtime.BuildConfig;
import com.centralbrain.runtime.R;
import com.centralbrain.runtime.graph.AgentGraphRuntime;
import com.centralbrain.runtime.identity.AndroidCallerIdentityResolver;
import com.centralbrain.runtime.identity.CallerIdentitySnapshot;
import com.centralbrain.runtime.policy.AndroidCapabilityPolicyLoader;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy.Capability;
import com.centralbrain.runtime.scenario.SimulatedScenarioInputFactory.DrivingProfile;
import com.centralbrain.runtime.scenario.SimulatedScenarioInputFactory.Input;
import com.centralbrain.runtime.scenario.SimulatedScenarioInputFactory.ScenarioKind;
import com.centralbrain.runtime.simulation.SimulationClock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Signature- and capability-protected debug Binder for fixed simulated scenarios. */
public final class SimulatedScenarioRuntimeService extends Service {
    public static final String ACTION =
            "com.centralbrain.runtime.action.BIND_SIMULATED_SCENARIO_RUNTIME";
    public static final String CONTROL_PERMISSION =
            "com.centralbrain.permission.CONTROL_DEBUG_SIMULATION";

    private static final String TAG = "CbSimScenarioSvc";
    private static final int MAX_ASSET_BYTES = 64 * 1024;

    private AndroidCallerIdentityResolver identityResolver;
    private CallerCapabilityPolicy capabilityPolicy;
    private SimulatedScenarioEffectComposition composition;
    private SimulatedScenarioInputFactory inputs;

    private final AgentGraphRuntime.Clock clock = new AgentGraphRuntime.Clock() {
        @Override
        public long epochTimeMs() {
            return System.currentTimeMillis();
        }

        @Override
        public long elapsedRealtimeMs() {
            return SystemClock.elapsedRealtime();
        }
    };

    private final ISimulatedScenarioRuntime.Stub binder =
            new ISimulatedScenarioRuntime.Stub() {
                @Override
                public int getProtocolVersion() {
                    authorize();
                    return ISimulatedScenarioRuntime.INTERFACE_VERSION;
                }

                @Override
                public String getProtocolHash() {
                    authorize();
                    return ISimulatedScenarioRuntime.INTERFACE_HASH;
                }

                @Override
                public SimulatedScenarioBinderSnapshot startScenario(
                        int scenario,
                        int drivingState) {
                    authorize();
                    return audited("startScenario", () -> {
                        Input input = inputs.create(
                                scenario(scenario), drivingProfile(drivingState));
                        return composition.start(
                                input, scenario(scenario), drivingProfile(drivingState));
                    });
                }

                @Override
                public SimulatedScenarioBinderSnapshot getSnapshot(String runId) {
                    authorize();
                    return audited("getSnapshot", () -> composition.get(runId));
                }

                @Override
                public SimulatedScenarioBinderSnapshot supplyPendingOutcome(
                        String runId,
                        int outcome) {
                    authorize();
                    return audited("supplyPendingOutcome", () ->
                            composition.supplyApprovalOutcome(runId, outcome(outcome)));
                }

                @Override
                public SimulatedScenarioBinderSnapshot cancel(String runId) {
                    authorize();
                    return audited("cancel", () -> composition.cancel(runId));
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        if (!BuildConfig.DEBUG) {
            throw new IllegalStateException("simulated scenario Service in non-debug build");
        }
        identityResolver = new AndroidCallerIdentityResolver(this);
        capabilityPolicy = AndroidCapabilityPolicyLoader.load(
                this,
                R.xml.central_brain_capability_policy,
                identityResolver.resolveOwnIdentity());
        composition = new SimulatedScenarioEffectComposition(
                clock, new SimulationClock(SystemClock.elapsedRealtime()));
        inputs = new SimulatedScenarioInputFactory(
                loadCatalog(),
                clock::epochTimeMs,
                clock::elapsedRealtimeMs,
                () -> UUID.randomUUID().toString());
        Log.i(TAG, "created debug_only=true fixed_scenario_count=2"
                + " session_event_metadata_only=true"
                + " simulated_effect_dispatch_enabled=true"
                + " simulated_readback_enabled=true"
                + " approval_authority_available=false"
                + " hardware_accessed=false production_ready=false");
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) {
            Log.w(TAG, "bind rejected reason=ACTION_MISMATCH hardware_accessed=false");
            return null;
        }
        return binder;
    }

    private void authorize() {
        enforceCallingOrSelfPermission(
                CONTROL_PERMISSION,
                "debug simulation signature permission required");
        CallerIdentitySnapshot caller = identityResolver.resolveCallingIdentity();
        CallerCapabilityPolicy.Decision decision = capabilityPolicy.evaluate(
                caller, Capability.SIMULATION_CONTROL);
        if (!decision.isAllowed()) {
            Log.w(TAG, "capability denied capability=" + Capability.SIMULATION_CONTROL.getId()
                    + " reason=" + decision.getReason()
                    + " matchedPackage=" + decision.getMatchedPackage()
                    + " " + caller.auditSummary()
                    + " hardware_accessed=false");
            throw new SecurityException("Central Brain capability denied: "
                    + Capability.SIMULATION_CONTROL.getId());
        }
    }

    private SimulatedScenarioBinderSnapshot audited(
            String operation,
            SnapshotOperation command) {
        try {
            SimulatedScenarioEffectComposition.Snapshot snapshot = command.run();
            Log.i(TAG, "simulated_scenario_binder_audit=true operation=" + operation
                    + " outcome=APPLIED"
                    + " session_state=" + snapshot.getSessionState().name()
                    + " effect_dispatch_count=" + snapshot.getEffectDispatchCount()
                    + " readback_match_count=" + snapshot.getReadbackMatchCount()
                    + " approval_input_count=" + snapshot.getApprovalInputCount()
                    + " failure_count=" + snapshot.getFailureCount()
                    + " projection_digest=" + snapshot.getProjectionDigest()
                    + " simulated_effect_dispatch_enabled=true"
                    + " hardware_effect_dispatch_enabled=false"
                    + " hardware_accessed=false production_ready=false");
            return SimulatedScenarioBinderSnapshot.from(snapshot);
        } catch (RuntimeException failure) {
            Log.w(TAG, "simulated_scenario_binder_audit=true operation=" + operation
                    + " outcome=REJECTED error=" + failure.getClass().getSimpleName()
                    + " hardware_accessed=false production_ready=false");
            throw failure;
        }
    }

    private ScenarioCatalog loadCatalog() {
        try {
            Map<String, byte[]> assets = new LinkedHashMap<>();
            for (String name : new String[] {
                    "scene.comfort.cold.v1.json",
                    "scene.fatigue.assist.v1.json",
                    "scene.rest.nap.v1.json"
            }) {
                assets.put(name, readAsset("scenarios/" + name));
            }
            return ScenarioCatalog.load(assets);
        } catch (IOException failure) {
            throw new IllegalStateException("built-in scenario catalog unavailable", failure);
        }
    }

    private byte[] readAsset(String path) throws IOException {
        try (InputStream input = getAssets().open(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > MAX_ASSET_BYTES) {
                    throw new IOException("built-in scenario asset exceeds limit");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static ScenarioKind scenario(int value) {
        switch (value) {
            case ISimulatedScenarioRuntime.SCENARIO_COLD:
                return ScenarioKind.COLD;
            case ISimulatedScenarioRuntime.SCENARIO_FATIGUE:
                return ScenarioKind.FATIGUE;
            default:
                throw new IllegalArgumentException("CB_SIM_SCENARIO_BINDER: unknown scenario");
        }
    }

    private static DrivingProfile drivingProfile(int value) {
        switch (value) {
            case ISimulatedScenarioRuntime.DRIVING_PARKED:
                return DrivingProfile.PARKED;
            case ISimulatedScenarioRuntime.DRIVING_MOVING:
                return DrivingProfile.MOVING;
            default:
                throw new IllegalArgumentException(
                        "CB_SIM_SCENARIO_BINDER: unknown driving state");
        }
    }

    private static AgentGraphRuntime.NodeExecutionOutcome outcome(int value) {
        switch (value) {
            case ISimulatedScenarioRuntime.OUTCOME_SUCCEEDED:
                return AgentGraphRuntime.NodeExecutionOutcome.SUCCEEDED;
            case ISimulatedScenarioRuntime.OUTCOME_FAILED:
                return AgentGraphRuntime.NodeExecutionOutcome.FAILED;
            case ISimulatedScenarioRuntime.OUTCOME_SKIPPED:
                return AgentGraphRuntime.NodeExecutionOutcome.SKIPPED;
            default:
                throw new IllegalArgumentException("CB_SIM_SCENARIO_BINDER: unknown outcome");
        }
    }

    private interface SnapshotOperation {
        SimulatedScenarioEffectComposition.Snapshot run();
    }
}
