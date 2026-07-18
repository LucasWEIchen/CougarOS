package com.centralbrain.runtime.events;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.scenario.ScenarioManifest;
import com.centralbrain.runtime.vehicle.schema.SignalQuality;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Debug-only API 33 ARM64 probe for the P6-W03 TriggerRule/TriggerEngine contract. */
public final class TriggerEngineProbeActivity extends Activity {
    private static final String TAG = "CbTriggerEngine";
    private static final String SCENARIO_DIGEST =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String SCOPE_A =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    private static final String SCOPE_B =
            "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
    private static final String EVIDENCE =
            "dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd";

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
        TriggerRule rule = rule();
        TriggerRule.Manifest manifest = new TriggerRule.Manifest(
                "trigger-manifest.default.v1",
                1,
                List.of(rule));
        AtomicLong now = new AtomicLong(1_000);
        CooldownStore cooldownStore = CooldownStore.createForContractTest(16);
        TriggerEngine engine = TriggerEngine.createForContractTest(
                manifest,
                cooldownStore,
                now::get);

        TriggerEngine.Evaluation first = evaluate(
                engine, now, "probe.1", SCOPE_A, 1_000, 17.0, SignalQuality.VALID);
        TriggerEngine.Evaluation second = evaluate(
                engine, now, "probe.2", SCOPE_A, 1_050, 17.0, SignalQuality.VALID);
        TriggerEngine.Evaluation debounced = evaluate(
                engine, now, "probe.3", SCOPE_A, 1_100, 17.0, SignalQuality.VALID);
        TriggerEngine.Evaluation suggested = evaluate(
                engine, now, "probe.4", SCOPE_A, 1_120, 17.0, SignalQuality.VALID);
        TriggerEngine.Evaluation cooldown = evaluate(
                engine, now, "probe.5", SCOPE_A, 1_130, 17.0, SignalQuality.VALID);
        TriggerEngine.Evaluation unavailable = evaluate(
                engine, now, "probe.6", SCOPE_A, 1_140, null, SignalQuality.UNAVAILABLE);

        CooldownStore scoped = CooldownStore.createForContractTest(2);
        CooldownStore.Reservation reservedA = scoped.reserve(
                rule.getRuleId(), SCOPE_A, "e".repeat(64), 1_000, 100);
        CooldownStore.Reservation suppressedA = scoped.reserve(
                rule.getRuleId(), SCOPE_A, "f".repeat(64), 1_010, 100);
        CooldownStore.Reservation reservedB = scoped.reserve(
                rule.getRuleId(), SCOPE_B, "f".repeat(64), 1_010, 100);

        boolean manifestVerified = manifest.getRules().size() == 1
                && manifest.getRules().get(0).getRuleDigest().equals(rule.getRuleDigest())
                && !manifest.isProductionTrusted();
        boolean thresholdWindowDebounce = first.getCode()
                == TriggerEngine.EvaluationCode.ACCUMULATING_WINDOW
                && second.getCode() == TriggerEngine.EvaluationCode.ACCUMULATING_WINDOW
                && debounced.getCode() == TriggerEngine.EvaluationCode.DEBOUNCING
                && suggested.getCode() == TriggerEngine.EvaluationCode.SUGGESTED;
        boolean cooldownScope = cooldown.getCode()
                == TriggerEngine.EvaluationCode.COOLDOWN_ACTIVE
                && reservedA.getCode() == CooldownStore.ReservationCode.RESERVED
                && suppressedA.getCode() == CooldownStore.ReservationCode.COOLDOWN_ACTIVE
                && reservedB.getCode() == CooldownStore.ReservationCode.RESERVED;
        boolean inputFailClosed = unavailable.getCode()
                == TriggerEngine.EvaluationCode.REJECTED_QUALITY;
        TriggerEngine.EngineSnapshot snapshot = engine.snapshot();
        boolean suggestionOnly = suggested.getSuggestion() != null
                && !suggested.getSuggestion().isAutoExecutionRequested()
                && !suggested.getSuggestion().isEffectDispatchRequested()
                && snapshot.isSuggestionOnly()
                && !snapshot.isEffectDispatchEnabled()
                && !snapshot.isRuntimeWired();
        boolean complete = manifestVerified
                && thresholdWindowDebounce
                && cooldownScope
                && inputFailClosed
                && suggestionOnly;

        Log.i(TAG, String.join("\n",
                "nonce=" + nonce + " trigger_engine_probe_complete=" + complete,
                "trigger_rule_manifest_verified=" + manifestVerified,
                "trigger_threshold_window_debounce_verified="
                        + thresholdWindowDebounce,
                "trigger_cooldown_scope_verified=" + cooldownScope,
                "trigger_input_fail_closed_verified=" + inputFailClosed,
                "trigger_suggestion_only_verified=" + suggestionOnly,
                "trigger_engine_android13_arm64_verified=" + complete,
                "trigger_engine_process_local=true",
                "trigger_cooldown_persistence_wired=false",
                "trigger_source_adapter_wired=false",
                "trigger_auto_execution_enabled=false",
                "trigger_runtime_wired=false",
                "graph_execution_enabled=false",
                "effect_dispatch_enabled=false",
                "vehicle_readback_accessed=false",
                "model_invoked=false",
                "npu_accessed=false",
                "network_accessed=false",
                "hardware_accessed=false",
                "production_ready=false",
                "target_hardware_validated=false"));
    }

    private static TriggerEngine.Evaluation evaluate(
            TriggerEngine engine,
            AtomicLong now,
            String observationId,
            String scope,
            long timestamp,
            Double value,
            SignalQuality quality) {
        now.set(timestamp);
        return engine.evaluate(new TriggerEngine.Observation(
                observationId,
                TriggerRule.Metric.CABIN_TEMPERATURE_C,
                ScenarioManifest.Zone.ROW1_DRIVER,
                scope,
                timestamp,
                quality,
                value,
                EVIDENCE)).getEvaluations().get(0);
    }

    private static TriggerRule rule() {
        return new TriggerRule(
                "trigger.cold.driver.v1",
                "scene.comfort.cold.v1",
                SCENARIO_DIGEST,
                TriggerRule.Metric.CABIN_TEMPERATURE_C,
                ScenarioManifest.Zone.ROW1_DRIVER,
                TriggerRule.ThresholdOperator.LESS_THAN,
                18.0,
                100,
                100,
                3,
                20,
                200,
                100);
    }
}
