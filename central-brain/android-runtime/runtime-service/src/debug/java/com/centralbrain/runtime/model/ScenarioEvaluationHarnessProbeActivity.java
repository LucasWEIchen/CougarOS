package com.centralbrain.runtime.model;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import com.centralbrain.runtime.model.ScenarioEvaluationHarness.CaseResult;
import com.centralbrain.runtime.model.ScenarioEvaluationHarness.EvaluationReport;
import com.centralbrain.runtime.model.ScenarioEvaluationHarness.ExpectedDisposition;
import com.centralbrain.runtime.model.ScenarioEvaluationHarness.FallbackKind;
import com.centralbrain.runtime.model.ScenarioEvaluationHarness.SyntheticCase;
import com.centralbrain.runtime.scenario.ScenarioCatalog;
import com.centralbrain.runtime.vehicle.capability.CapabilityCatalog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ScenarioEvaluationHarnessProbeActivity extends Activity {
    private static final String TAG = "CbModelEvalProbe";
    private static final String TRACE = "e".repeat(64);
    private static final String INPUT = "f".repeat(64);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private void runProbe(String nonce) {
        try {
            ScenarioCatalog scenarios = loadScenarios();
            CapabilityCatalog capabilities = CapabilityCatalog.stage2Defaults();
            EvaluationReport report = ScenarioEvaluationHarness.aggregate(
                    evaluateFixedCorpus(scenarios, capabilities));
            boolean corpusVerified = report.getTotalCaseCount()
                    == ScenarioEvaluationHarness.EXPECTED_CASE_COUNT
                    && report.getCorpusDigest().equals(
                            ScenarioEvaluationHarness.getCorpusDigest());
            boolean metricsVerified = report.getIntentAccuracyPermille() == 1_000
                    && report.getUnsafeProposalRatePermille() == 0
                    && report.getInvalidSchemaRatePermille() == 0
                    && report.getFallbackRatePermille() == 0
                    && report.getP50LatencyMs() == 5
                    && report.getP95LatencyMs() == 11
                    && report.getMaximumLatencyMs() == 11
                    && report.getTokenCostUnits() == 190;
            boolean boundaryVerified = !report.isRawContentRetained()
                    && !report.isModelInvoked()
                    && !report.isActionAuthorizationGranted()
                    && !report.isEffectDispatchRequested()
                    && !report.isProductionQualified();
            boolean verified = corpusVerified && metricsVerified && boundaryVerified;

            Log.i(TAG, "nonce=" + nonce
                    + " scenario_evaluation_probe_complete=true"
                    + " scenario_evaluation_verified=" + verified
                    + " evaluation_corpus_verified=" + corpusVerified
                    + " evaluation_metrics_verified=" + metricsVerified
                    + " evaluation_boundary_verified=" + boundaryVerified
                    + " evaluation_case_count=" + report.getTotalCaseCount()
                    + " intent_accuracy_permille=" + report.getIntentAccuracyPermille()
                    + " unsafe_proposal_rate_permille="
                    + report.getUnsafeProposalRatePermille()
                    + " invalid_schema_rate_permille="
                    + report.getInvalidSchemaRatePermille()
                    + " fallback_rate_permille=" + report.getFallbackRatePermille()
                    + " p50_latency_ms=" + report.getP50LatencyMs()
                    + " p95_latency_ms=" + report.getP95LatencyMs()
                    + " maximum_latency_ms=" + report.getMaximumLatencyMs()
                    + " token_cost_units=" + report.getTokenCostUnits()
                    + " scenario_evaluation_runtime_wired=false"
                    + " raw_evaluation_content_logged=false"
                    + " model_invoked=false"
                    + " network_accessed=false"
                    + " npu_accessed=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (IOException | RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " scenario_evaluation_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " scenario_evaluation_runtime_wired=false"
                    + " raw_evaluation_content_logged=false"
                    + " model_invoked=false"
                    + " network_accessed=false"
                    + " npu_accessed=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false", exception);
        }
    }

    private static List<CaseResult> evaluateFixedCorpus(
            ScenarioCatalog scenarios,
            CapabilityCatalog capabilities) {
        List<CaseResult> results = new ArrayList<>();
        int index = 0;
        for (SyntheticCase syntheticCase : ScenarioEvaluationHarness.corpus()) {
            if (syntheticCase.getExpectedDisposition() == ExpectedDisposition.NO_PROPOSAL) {
                results.add(ScenarioEvaluationHarness.evaluateNoProposal(
                        syntheticCase.getCaseId(),
                        request("eval-probe-no-output-" + index),
                        index,
                        10,
                        0,
                        FallbackKind.NONE,
                        scenarios,
                        capabilities));
            } else {
                results.add(ScenarioEvaluationHarness.evaluateOutput(
                        syntheticCase.getCaseId(),
                        request("eval-probe-output-" + index),
                        outputFor(syntheticCase.getExpectedScenarioId()),
                        index,
                        10,
                        10,
                        FallbackKind.NONE,
                        scenarios,
                        capabilities));
            }
            index++;
        }
        return results;
    }

    private ScenarioCatalog loadScenarios() throws IOException {
        Map<String, byte[]> assets = new LinkedHashMap<>();
        for (String name : getAssets().list("scenarios")) {
            if (name.startsWith("scene.") && name.endsWith(".json")) {
                try (java.io.InputStream stream = getAssets().open("scenarios/" + name)) {
                    assets.put(name, stream.readAllBytes());
                }
            }
        }
        return ScenarioCatalog.load(assets);
    }

    private static byte[] outputFor(String scenarioId) {
        String parameter;
        if ("scene.comfort.cold.v1".equals(scenarioId)) {
            parameter = parameter("vehicle.hvac.power", "cabin", "true");
        } else if ("scene.fatigue.assist.v1".equals(scenarioId)) {
            parameter = parameter("vehicle.hvac.fan_level", "cabin", "2");
        } else if ("scene.rest.nap.v1".equals(scenarioId)) {
            parameter = parameter("vehicle.hvac.power", "cabin", "true");
        } else {
            throw new IllegalArgumentException("unsupported synthetic scenario");
        }
        return ("{\"schemaVersion\":1,\"scenarioId\":\""
                + scenarioId
                + "\",\"parameters\":["
                + parameter
                + "],\"summary\":\"synthetic-evaluation\"}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static String parameter(String capabilityId, String area, String value) {
        return "{\"capabilityId\":\"" + capabilityId
                + "\",\"area\":\"" + area
                + "\",\"value\":" + value + "}";
    }

    private static ModelContractV2.ModelRequest request(String requestId) {
        return new ModelContractV2.ModelRequest(
                requestId,
                ModelContractV2.Purpose.SCENARIO_REASONING,
                ModelContractV2.PrivacyClass.INTERNAL,
                new ModelContractV2.LatencyBudget(120_000),
                new ModelContractV2.TokenBudget(1_024, 1_024, 2_048),
                ModelContractV2.RequiredCapability.STRUCTURED_SCENARIO_CANDIDATE,
                ModelContractV2.FallbackPolicy.NO_FALLBACK,
                TRACE,
                INPUT);
    }
}
