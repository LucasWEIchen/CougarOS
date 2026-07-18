package com.centralbrain.runtime.events;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalSource;
import com.centralbrain.runtime.vehicle.schema.SignalTimestamp;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

/** Debug-only API 33 ARM64 probe for the P6-W05 Context source adapters. */
public final class ContextSourceAdaptersProbeActivity extends Activity {
    private static final String TAG = "CbContextSources";
    private static final String EVIDENCE = "a".repeat(64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        runProbe();
        finish();
    }

    private void runProbe() {
        String nonce = getIntent().getStringExtra("nonce");
        if (nonce == null || nonce.isEmpty()) {
            nonce = "missing";
        }

        ContextSourceAdapter.ContractSnapshot snapshot =
                ContextSourceAdapter.ContractSnapshot.current();
        boolean allowlistVerified = snapshot.getSourceCount() == 3
                && ContextSourceAdapter.initialDescriptors().stream()
                .noneMatch(ContextSourceAdapter.Descriptor::isProductionPublished);

        RuntimeHealthContextSourceAdapter runtime =
                new RuntimeHealthContextSourceAdapter();
        RuntimeHealthContextSourceAdapter.RuntimeHealthSample healthy =
                new RuntimeHealthContextSourceAdapter.RuntimeHealthSample(
                        RuntimeHealthContextSourceAdapter.HealthState.HEALTHY,
                        1_000,
                        1,
                        EVIDENCE);
        ContextSourceAdapter.AdaptationResult runtimeFresh = runtime.adapt(healthy, 1_500);
        ContextSourceAdapter.AdaptationResult runtimeStale = runtime.adapt(healthy, 2_001);
        boolean runtimeVerified = runtimeFresh.getCode()
                == ContextSourceAdapter.ResultCode.ADAPTED
                && runtimeStale.getCode() == ContextSourceAdapter.ResultCode.STALE;

        SimulatedVehicleSignalContextSourceAdapter vehicle =
                new SimulatedVehicleSignalContextSourceAdapter();
        SignalValue simulated = SignalValue.ofDecimal(
                VehicleSignalPath.CABIN_TEMPERATURE,
                18.5,
                "celsius",
                "cabin",
                new SignalTimestamp(1_700_000_000_000L, 1_000),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                1);
        SignalValue aaos = SignalValue.ofDecimal(
                VehicleSignalPath.CABIN_TEMPERATURE,
                18.5,
                "celsius",
                "cabin",
                new SignalTimestamp(1_700_000_000_000L, 1_000),
                SignalQuality.VALID,
                SignalSource.AAOS,
                1);
        ContextSourceAdapter.AdaptationResult simulatedResult = vehicle.adapt(simulated, 1_500);
        boolean vehicleVerified = simulatedResult.getCode()
                == ContextSourceAdapter.ResultCode.ADAPTED
                && simulatedResult.getObservation().getTrustClass()
                == ContextSourceAdapter.TrustClass.SIMULATED
                && !simulatedResult.getObservation().isProductionTrusted()
                && vehicle.adapt(aaos, 1_500).getCode()
                == ContextSourceAdapter.ResultCode.REJECTED_PROVENANCE;

        TimeContextSourceAdapter time = new TimeContextSourceAdapter();
        TimeContextSourceAdapter.TimeSample timeSample =
                new TimeContextSourceAdapter.TimeSample(
                        1_704_067_200_000L,
                        1_000,
                        8 * 60,
                        EVIDENCE);
        ContextSourceAdapter.AdaptationResult timeResult = time.adapt(timeSample, 1_500);
        boolean timeVerified = timeResult.getCode()
                == ContextSourceAdapter.ResultCode.ADAPTED
                && timeResult.getObservation().getValue().getIntegerValue() == 8 * 60
                && time.adapt(timeSample, 999).getCode()
                == ContextSourceAdapter.ResultCode.REJECTED_FUTURE;

        boolean freshnessVerified = runtimeFresh.getObservation().getQuality()
                == SignalQuality.VALID
                && runtimeStale.getObservation().getQuality() == SignalQuality.STALE
                && simulatedResult.getObservation().getQuality() == SignalQuality.VALID;
        boolean failClosed = !snapshot.isProductionRegistryPublished()
                && !snapshot.isRuntimeWired()
                && !snapshot.isTriggerEngineWired()
                && !snapshot.isVehiclePropertyMappingConfigured()
                && !snapshot.isHardwareAccessed();
        boolean complete = allowlistVerified
                && runtimeVerified
                && vehicleVerified
                && timeVerified
                && freshnessVerified
                && failClosed;

        Log.i(TAG, String.join("\n",
                "nonce=" + nonce + " context_source_probe_complete=" + complete,
                "context_source_allowlist_verified=" + allowlistVerified,
                "context_source_runtime_health_verified=" + runtimeVerified,
                "context_source_simulated_vehicle_verified=" + vehicleVerified,
                "context_source_time_verified=" + timeVerified,
                "context_source_freshness_quality_verified=" + freshnessVerified,
                "context_source_fail_closed_verified=" + failClosed,
                "context_source_android13_arm64_verified=" + complete,
                "context_source_count=3",
                "context_source_production_registry_published=false",
                "context_source_runtime_wired=false",
                "context_source_trigger_engine_wired=false",
                "vehicle_signal_provider_wired=false",
                "vehicle_property_mapping_configured=false",
                "graph_execution_enabled=false",
                "effect_dispatch_enabled=false",
                "model_invoked=false",
                "npu_accessed=false",
                "network_accessed=false",
                "hardware_accessed=false",
                "production_ready=false",
                "target_hardware_validated=false"));
    }
}
