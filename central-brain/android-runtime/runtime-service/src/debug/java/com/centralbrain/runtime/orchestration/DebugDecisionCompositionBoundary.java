package com.centralbrain.runtime.orchestration;

import android.os.SystemClock;

import com.centralbrain.runtime.BuildConfig;
import com.centralbrain.runtime.agent.CabinComplianceAgentRouter;
import com.centralbrain.runtime.events.BoundedEventRuntime;
import com.centralbrain.runtime.events.ContextSourceAdapter;
import com.centralbrain.runtime.events.CooldownStore;
import com.centralbrain.runtime.events.ProactiveConsentPolicy;
import com.centralbrain.runtime.events.RuntimeHealthContextSourceAdapter;
import com.centralbrain.runtime.events.SimulatedVehicleSignalContextSourceAdapter;
import com.centralbrain.runtime.events.TimeContextSourceAdapter;
import com.centralbrain.runtime.events.TriggerEngine;
import com.centralbrain.runtime.events.TriggerRule;
import com.centralbrain.runtime.model.DeterministicStubModelProvider;
import com.centralbrain.runtime.model.CockpitModelPrompt;
import com.centralbrain.runtime.model.DevelopmentModelInputStore;
import com.centralbrain.runtime.model.LocalModelProvider;
import com.centralbrain.runtime.model.ModelContractV2;
import com.centralbrain.runtime.model.ModelProfileRouter;
import com.centralbrain.runtime.model.ModelProvider;
import com.centralbrain.runtime.model.ModelProviderRegistry;
import com.centralbrain.runtime.model.OpenClawEndpointConfig;
import com.centralbrain.runtime.model.OpenClawInferenceEngine;
import com.centralbrain.runtime.model.VllmEndpointConfig;
import com.centralbrain.runtime.model.VllmInferenceEngine;
import com.centralbrain.runtime.model.PolicyAwareModelRouter;
import com.centralbrain.runtime.model.TestOnlyModelRouter;
import com.centralbrain.runtime.scenario.ScenarioCatalog;
import com.centralbrain.runtime.scenario.ScenarioManifest;
import com.centralbrain.runtime.scheduler.InferenceResourceScheduler;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability;
import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalSource;
import com.centralbrain.runtime.vehicle.schema.SignalTimestamp;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.centralbrain.sdk.model.DevelopmentModelInputReceipt;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/** Debug-only digest composition for Context, Trigger, consent, Model, and Event contracts. */
final class DebugDecisionCompositionBoundary {
    private static final int MAX_SESSIONS = 16;
    private static final String MODEL_DIGEST = "4".repeat(64);
    private static final long MODEL_HEALTH_WINDOW_MS = 60_000L;
    private static final long MODEL_INFERENCE_TIMEOUT_MS = 120_000L;
    private static final int MAX_MODEL_PROJECTION_BYTES = 16_384;
    private static final String TEST_SMOKING_AGENT_INSTRUCTION =
            "Analyze objective smoking evidence and return the registered five-field JSON.";

    interface Clock {
        long nowMs();
    }

    private enum NetworkModelMode {
        NONE,
        VLLM_DEVELOPMENT,
        OPENCLAW_TARGET
    }

    private final ScenarioCatalog catalog;
    private final Clock clock;
    private final AtomicLong triggerEvaluationNow = new AtomicLong();
    private final TriggerEngine triggers;
    private final ProactiveConsentPolicy consent;
    private final ModelProviderRegistry modelRegistry;
    private final ManualExecutor modelExecutor = new ManualExecutor();
    private final ModelProvider.ModelSpec modelSpec;
    private final ModelProvider modelProvider;
    private final ModelProvider.ModelSpec smokingModelSpec;
    private final ModelProvider smokingModelProvider;
    private final TestOnlyModelRouter testModelRouter;
    private final VllmInferenceEngine vllmEngine;
    private final VllmInferenceEngine smokingVllmEngine;
    private final OpenClawInferenceEngine openClawEngine;
    private final NetworkModelMode networkModelMode;
    private final BoundedEventRuntime events;
    private final String smokingAgentInstruction;
    private final Map<String, Entry> bySession = new LinkedHashMap<>();
    private long modelHealthRevision;

    DebugDecisionCompositionBoundary(ScenarioCatalog catalog) {
        this(catalog, SystemClock::elapsedRealtime, sequence("decision-subscription-"),
                sequence("decision-lease-"), true, TEST_SMOKING_AGENT_INSTRUCTION);
    }

    DebugDecisionCompositionBoundary(
            ScenarioCatalog catalog,
            String smokingAgentInstruction) {
        this(catalog, SystemClock::elapsedRealtime, sequence("decision-subscription-"),
                sequence("decision-lease-"), true, smokingAgentInstruction);
    }

    DebugDecisionCompositionBoundary(
            ScenarioCatalog catalog,
            Clock clock,
            Supplier<String> subscriptionIds,
            Supplier<String> leaseIds) {
        this(catalog, clock, subscriptionIds, leaseIds, false,
                TEST_SMOKING_AGENT_INSTRUCTION);
    }

    private DebugDecisionCompositionBoundary(
            ScenarioCatalog catalog,
            Clock clock,
            Supplier<String> subscriptionIds,
            Supplier<String> leaseIds,
            boolean networkModelBuild,
            String smokingAgentInstruction) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.smokingAgentInstruction = requireAgentInstruction(
                smokingAgentInstruction);
        this.networkModelMode = resolveNetworkMode(networkModelBuild);
        triggers = TriggerEngine.createForContractTest(
                triggerManifest(catalog),
                CooldownStore.createForContractTest(MAX_SESSIONS * 2),
                triggerEvaluationNow::get);
        consent = ProactiveConsentPolicy.createForContractTest(
                MAX_SESSIONS,
                clock::nowMs,
                (mutation, evidence) -> ProactiveConsentPolicy.AuthorityDecision.DENIED);
        modelRegistry = ModelProviderRegistry.createForContractTest();
        if (networkModelMode == NetworkModelMode.VLLM_DEVELOPMENT) {
            VllmEndpointConfig endpoint = generalVllmEndpoint();
            VllmEndpointConfig smokingEndpoint = smokingVllmEndpoint();
            vllmEngine = new VllmInferenceEngine(endpoint);
            smokingVllmEngine = new VllmInferenceEngine(smokingEndpoint);
            openClawEngine = null;
            modelSpec = new ModelProvider.ModelSpec(
                    "central-intent-general-v1",
                    BuildConfig.VLLM_TARGET_ETHERNET_ENABLED
                            ? "vllm-qwen3.5-2b-awq-ctx8192-target-v1"
                            : "vllm-qwen3.5-9b-awq-ctx8192-v2",
                    digest(
                            "vllm-model-spec-v2",
                            endpoint.getModelName(),
                            Integer.toString(endpoint.getMaximumContextTokens())));
            modelProvider = LocalModelProvider.createForDevelopment(
                    modelSpec,
                    vllmEngine,
                    modelExecutor,
                    clock::nowMs,
                    LocalModelProvider.StreamLimits.defaults());
            smokingModelSpec = new ModelProvider.ModelSpec(
                    "central-vision-smoking-v1",
                    "vllm-qwen3.5-2b-awq-ctx4096-v1",
                    digest(
                            "vllm-model-spec-v2",
                            smokingEndpoint.getModelName(),
                            Integer.toString(smokingEndpoint.getMaximumContextTokens())));
            smokingModelProvider = LocalModelProvider.createForDevelopment(
                    smokingModelSpec,
                    smokingVllmEngine,
                    modelExecutor,
                    clock::nowMs,
                    LocalModelProvider.StreamLimits.defaults());
            testModelRouter = null;
        } else if (networkModelMode == NetworkModelMode.OPENCLAW_TARGET) {
            OpenClawEndpointConfig endpoint =
                    OpenClawEndpointConfig.targetProductionTransitional();
            openClawEngine = new OpenClawInferenceEngine(endpoint);
            vllmEngine = null;
            smokingVllmEngine = null;
            modelSpec = new ModelProvider.ModelSpec(
                    "central-intent-v0",
                    "openclaw-ws-v3",
                    digest("openclaw-model-spec", endpoint.getWebSocketUri().toString()));
            modelProvider = LocalModelProvider.createForTargetOpenClawIntegration(
                            modelSpec,
                            openClawEngine,
                            modelExecutor,
                            clock::nowMs,
                            LocalModelProvider.StreamLimits.defaults());
            smokingModelSpec = null;
            smokingModelProvider = null;
            testModelRouter = null;
        } else {
            vllmEngine = null;
            smokingVllmEngine = null;
            openClawEngine = null;
            modelSpec = new ModelProvider.ModelSpec(
                    "central-intent-v0", "1", MODEL_DIGEST);
            modelProvider = new DeterministicStubModelProvider(
                    modelSpec, modelExecutor, clock::nowMs);
            smokingModelSpec = null;
            smokingModelProvider = null;
            testModelRouter = TestOnlyModelRouter.createForContractTest(
                    new InferenceResourceScheduler(
                            new InferenceResourceScheduler.Limits(8, 4, 2, 1, 10_000),
                            clock::nowMs,
                            () -> Objects.requireNonNull(leaseIds, "leaseIds").get(),
                            Collections.singletonList(
                                    TestOnlyModelRouter.routeTargetForContractTest(
                                            modelProvider))),
                    modelProvider);
        }
        if (networkModelMode == NetworkModelMode.VLLM_DEVELOPMENT) {
            if (!warmupWithoutFallback(modelProvider, modelSpec)
                    || !warmupWithoutFallback(smokingModelProvider, smokingModelSpec)) {
                throw new IllegalStateException(
                        "all routed vLLM model profiles must pass prewarm");
            }
        } else {
            modelProvider.warmup(modelSpec);
        }
        events = BoundedEventRuntime.createForContractTest(
                new BoundedEventRuntime.Limits(32, MAX_SESSIONS, 4, 8, 8),
                clock::nowMs,
                Objects.requireNonNull(subscriptionIds, "subscriptionIds"));
    }

    synchronized Evidence prepare(
            OrchestrationBackend.SessionDescriptor session,
            String scenarioId,
            String requestDigest) {
        Objects.requireNonNull(session, "session");
        requireDigest(requestDigest, "requestDigest");
        Entry existing = bySession.get(session.getSessionId());
        if (existing != null) {
            if (!existing.requestDigest.equals(requestDigest)) {
                throw violation("session decision request conflict");
            }
            return existing.evidence;
        }
        if (bySession.size() >= MAX_SESSIONS) {
            throw violation("decision composition capacity exhausted");
        }
        ScenarioManifest manifest = catalog.require(scenarioId);
        long now = clock.nowMs();
        if (now < 100L) {
            throw violation("elapsed realtime is too small for a trigger window");
        }
        triggerEvaluationNow.set(now);

        List<String> contextDigests = adaptContext(scenarioId, requestDigest, now);
        TriggerEngine.ScenarioSuggestion suggestion = evaluateTrigger(
                session, manifest, scenarioId, requestDigest, now);
        ProactiveConsentPolicy.AdmissionDecision consentDecision = consent.evaluate(
                new ProactiveConsentPolicy.AutoExecutionCandidate(
                        suggestion.getSuggestionDigest(),
                        session.getOwnerFingerprint(),
                        scenarioId,
                        manifest.getArtifactDigest(),
                        capabilityForScenario(scenarioId),
                        CabinComplianceAgentRouter.SMOKING_SCENARIO_ID.equals(scenarioId)
                                ? ScenarioManifest.Zone.CABIN
                                : ScenarioManifest.Zone.ROW1_DRIVER,
                        ProactiveConsentPolicy.RiskClass.LOW));
        if (consentDecision.getCode() != ProactiveConsentPolicy.AdmissionCode.NO_ACTIVE_GRANT
                || consentDecision.isEffectDispatchAuthorized()) {
            throw violation("proactive consent boundary did not fail closed");
        }

        ModelEvidence modelEvidence = invokeModel(
                session, scenarioId, requestDigest, contextDigests, suggestion, now);
        EventEvidence eventEvidence = publishDecisionEvents(
                session, requestDigest, modelEvidence, consentDecision);
        String evidenceDigest = digest(
                "central-brain-debug-decision-composition-v1",
                requestDigest,
                String.join("|", contextDigests),
                suggestion.getSuggestionDigest(),
                consentDecision.getCode().name(),
                modelEvidence.routeDigest,
                modelEvidence.outputDigest,
                modelEvidence.profileId,
                modelEvidence.modelId,
                String.join("|", modelEvidence.admittedActions),
                eventEvidence.digest);
        Evidence evidence = new Evidence(
                evidenceDigest,
                contextDigests.size(),
                suggestion.getSuggestionDigest(),
                consentDecision.getCode(),
                modelEvidence.routeDigest,
                modelEvidence.outputDigest,
                eventEvidence.deliveredCount,
                eventEvidence.lastSequence,
                "scene.fatigue.assist.v1".equals(scenarioId),
                modelEvidence.networkAccessed,
                modelEvidence.assistantDisplayText,
                modelEvidence.providerId,
                modelEvidence.profileId,
                modelEvidence.modelId,
                modelEvidence.latencyMs,
                modelEvidence.inputAggregateDigest,
                modelEvidence.imageConsumed,
                modelEvidence.admittedActions);
        bySession.put(session.getSessionId(), new Entry(requestDigest, evidence));
        return evidence;
    }

    synchronized Completion complete(
            OrchestrationBackend.SessionDescriptor session,
            Evidence evidence) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(evidence, "evidence");
        Entry entry = bySession.get(session.getSessionId());
        if (entry == null || !entry.evidence.digest.equals(evidence.digest)) {
            throw violation("decision completion is not session bound");
        }
        if (entry.completed) {
            return new Completion(true);
        }
        entry.completed = true;
        return new Completion(false);
    }

    synchronized Snapshot snapshot() {
        int completed = 0;
        for (Entry entry : bySession.values()) {
            if (entry.completed) {
                completed++;
            }
        }
        return new Snapshot(
                bySession.size(),
                completed,
                triggers.snapshot().getSuggestionCount(),
                modelProvider.metrics().getCompletedCount()
                        + (smokingModelProvider == null
                                ? 0L : smokingModelProvider.metrics().getCompletedCount()),
                events.snapshot().getActiveSubscriptionCount());
    }

    synchronized void close() {
        modelProvider.close();
        if (smokingModelProvider != null) {
            smokingModelProvider.close();
        }
        for (Entry entry : bySession.values()) {
            entry.completed = true;
        }
    }

    private static NetworkModelMode resolveNetworkMode(boolean networkModelBuild) {
        if (!networkModelBuild) {
            return NetworkModelMode.NONE;
        }
        if (BuildConfig.OPENCLAW_TARGET_ROUTING_ENABLED) {
            if (!BuildConfig.OPENCLAW_TARGET_ENDPOINT_CONFIGURED
                    || BuildConfig.OPENCLAW_DEVELOPMENT_ROUTING_ENABLED
                    || BuildConfig.VLLM_TARGET_ETHERNET_ENABLED
                    || !"target_openclaw_transitional".equals(
                            BuildConfig.MODEL_GATEWAY_PROFILE)
                    || !"ws://169.254.208.110:18789".equals(BuildConfig.OPENCLAW_BASE_URL)
                    || BuildConfig.OPENCLAW_PROTOCOL_VERSION
                            != OpenClawEndpointConfig.TARGET_PROTOCOL_VERSION
                    || BuildConfig.OLLAMA_DEVELOPMENT_ENABLED
                    || BuildConfig.VLLM_DEVELOPMENT_ENABLED) {
                throw new IllegalStateException(
                        "target OpenClaw build configuration is invalid");
            }
            return NetworkModelMode.OPENCLAW_TARGET;
        }
        boolean targetEthernet = BuildConfig.VLLM_TARGET_ETHERNET_ENABLED;
        String expectedProfile = targetEthernet
                ? "target_ty1100_vllm_ethernet" : "development_ty1100_vllm";
        String expectedGeneralBaseUrl = targetEthernet
                ? "http://169.254.202.110:8000" : "http://127.0.0.1:10030";
        String expectedSmokingBaseUrl = targetEthernet
                ? "http://169.254.202.110:8000" : "http://127.0.0.1:10031";
        String expectedGeneralModel = targetEthernet
                ? VllmEndpointConfig.TARGET_ETHERNET_MODEL
                : VllmEndpointConfig.GENERAL_MODEL;
        if (!BuildConfig.VLLM_DEVELOPMENT_ENABLED
                || BuildConfig.OPENCLAW_DEVELOPMENT_ROUTING_ENABLED
                || BuildConfig.OLLAMA_DEVELOPMENT_ENABLED
                || !expectedProfile.equals(BuildConfig.MODEL_GATEWAY_PROFILE)
                || !expectedGeneralBaseUrl.equals(BuildConfig.VLLM_BASE_URL)
                || !expectedGeneralModel.equals(BuildConfig.VLLM_MODEL)
                || !BuildConfig.VLLM_MODEL_ROUTING_ENABLED
                || !BuildConfig.VLLM_PREWARM_REQUIRED
                || !expectedGeneralBaseUrl.equals(BuildConfig.VLLM_GENERAL_BASE_URL)
                || !expectedGeneralModel.equals(
                        BuildConfig.VLLM_GENERAL_MODEL)
                || BuildConfig.VLLM_GENERAL_CONTEXT_TOKENS
                        != ModelProfileRouter.GENERAL_MAX_CONTEXT_TOKENS
                || !expectedSmokingBaseUrl.equals(BuildConfig.VLLM_SMOKING_BASE_URL)
                || !VllmEndpointConfig.SMOKING_MODEL.equals(
                        BuildConfig.VLLM_SMOKING_MODEL)
                || BuildConfig.VLLM_SMOKING_CONTEXT_TOKENS
                        != ModelProfileRouter.SMOKING_MAX_CONTEXT_TOKENS) {
            throw new IllegalStateException("debug TY1100 vLLM build configuration is invalid");
        }
        return NetworkModelMode.VLLM_DEVELOPMENT;
    }

    private static VllmEndpointConfig generalVllmEndpoint() {
        return BuildConfig.VLLM_TARGET_ETHERNET_ENABLED
                ? VllmEndpointConfig.ty1100General2bViaTargetEthernet()
                : VllmEndpointConfig.ty1100General9bViaAdbReverse();
    }

    private static VllmEndpointConfig smokingVllmEndpoint() {
        return BuildConfig.VLLM_TARGET_ETHERNET_ENABLED
                ? VllmEndpointConfig.ty1100Smoking2bViaTargetEthernet()
                : VllmEndpointConfig.ty1100Smoking2bViaAdbReverse();
    }

    private static boolean isOpenClawMode(NetworkModelMode mode) {
        return mode == NetworkModelMode.OPENCLAW_TARGET;
    }

    private List<String> adaptContext(String scenarioId, String requestDigest, long now) {
        List<String> digests = new ArrayList<>();
        ContextSourceAdapter.AdaptationResult health =
                new RuntimeHealthContextSourceAdapter().adapt(
                        new RuntimeHealthContextSourceAdapter.RuntimeHealthSample(
                                RuntimeHealthContextSourceAdapter.HealthState.HEALTHY,
                                now,
                                1,
                                digest("runtime-health", requestDigest)),
                        now);
        addAdapted(digests, health, "Runtime health");
        ContextSourceAdapter.AdaptationResult time = new TimeContextSourceAdapter().adapt(
                new TimeContextSourceAdapter.TimeSample(
                        1_700_000_000_000L,
                        now,
                        0,
                        digest("time", requestDigest)),
                now);
        addAdapted(digests, time, "time");
        if ("scene.comfort.cold.v1".equals(scenarioId)) {
            SignalValue temperature = SignalValue.ofDecimal(
                    VehicleSignalPath.CABIN_TEMPERATURE,
                    17.0,
                    "celsius",
                    "cabin",
                    new SignalTimestamp(1_700_000_000_000L, now),
                    SignalQuality.VALID,
                    SignalSource.SIMULATED,
                    1);
            addAdapted(
                    digests,
                    new SimulatedVehicleSignalContextSourceAdapter().adapt(temperature, now),
                    "simulated vehicle signal");
        }
        return Collections.unmodifiableList(digests);
    }

    private static void addAdapted(
            List<String> digests,
            ContextSourceAdapter.AdaptationResult result,
            String source) {
        if (result.getCode() != ContextSourceAdapter.ResultCode.ADAPTED
                || result.getObservation() == null
                || !result.getObservation().isDecisionUsable()) {
            throw violation(source + " context adaptation failed closed");
        }
        digests.add(result.getObservation().getObservationDigest());
    }

    private TriggerEngine.ScenarioSuggestion evaluateTrigger(
            OrchestrationBackend.SessionDescriptor session,
            ScenarioManifest manifest,
            String scenarioId,
            String requestDigest,
            long now) {
        TriggerRule.Metric metric = metricForScenario(scenarioId);
        double value = triggerValueForScenario(scenarioId);
        TriggerEngine.Evaluation terminal = null;
        for (int index = 0; index < 3; index++) {
            long observedAt = now - 100L + index * 50L;
            TriggerEngine.EvaluationBatch batch = triggers.evaluate(
                    new TriggerEngine.Observation(
                            "obs." + requestDigest.substring(0, 24) + "." + index,
                            metric,
                            CabinComplianceAgentRouter.SMOKING_SCENARIO_ID.equals(scenarioId)
                                    ? ScenarioManifest.Zone.CABIN
                                    : ScenarioManifest.Zone.ROW1_DRIVER,
                            digest("trigger-scope", session.getSessionId()),
                            observedAt,
                            SignalQuality.VALID,
                            value,
                            digest("trigger-source", requestDigest, Integer.toString(index))));
            if (batch.getEvaluations().size() != 1) {
                throw violation("trigger evaluation did not resolve exactly one rule");
            }
            terminal = batch.getEvaluations().get(0);
        }
        if (terminal == null
                || terminal.getCode() != TriggerEngine.EvaluationCode.SUGGESTED
                || terminal.getSuggestion() == null
                || terminal.getSuggestion().isAutoExecutionRequested()
                || terminal.getSuggestion().isEffectDispatchRequested()
                || !manifest.getArtifactDigest().equals(
                        terminal.getSuggestion().getScenarioManifestDigest())) {
            throw violation(
                    "trigger suggestion boundary failed closed: "
                            + (terminal == null
                                    ? "NONE" : terminal.getCode().name()));
        }
        return terminal.getSuggestion();
    }

    private ModelEvidence invokeModel(
            OrchestrationBackend.SessionDescriptor session,
            String scenarioId,
            String requestDigest,
            List<String> contextDigests,
            TriggerEngine.ScenarioSuggestion suggestion,
            long now) {
        boolean networkModel = networkModelMode != NetworkModelMode.NONE;
        String providerId;
        ModelProviderRegistry.HealthSource healthSource;
        PolicyAwareModelRouter.RouteMode routeMode;
        if (networkModelMode == NetworkModelMode.OPENCLAW_TARGET) {
            providerId = ModelProviderRegistry.TARGET_OPENCLAW_TRANSITIONAL_ID;
            healthSource = ModelProviderRegistry.HealthSource.TARGET_OPENCLAW_RUNTIME;
            routeMode = PolicyAwareModelRouter.RouteMode.TARGET_INTEGRATION;
        } else if (networkModelMode == NetworkModelMode.VLLM_DEVELOPMENT) {
            providerId = ModelProviderRegistry.ANDROID_LOCAL_DEVELOPMENT_ID;
            healthSource = ModelProviderRegistry.HealthSource.LOCAL_DEVELOPMENT_RUNTIME;
            routeMode = PolicyAwareModelRouter.RouteMode.DEVELOPMENT;
        } else {
            providerId = ModelProviderRegistry.DETERMINISTIC_TEST_ID;
            healthSource = ModelProviderRegistry.HealthSource.CONTRACT_TEST;
            routeMode = PolicyAwareModelRouter.RouteMode.CONTRACT_TEST;
        }
        DevelopmentModelInputReceipt stagedInput = null;
        byte[] stagedImage = null;
        boolean stagedImagePresent = false;
        if ("scene.cabin.multimodal.assist.v1".equals(scenarioId)
                || CabinComplianceAgentRouter.SMOKING_SCENARIO_ID.equals(scenarioId)
                || "scene.aios.freeform.v1".equals(scenarioId)) {
            DevelopmentModelInputStore.ConsumedInput consumed =
                    DevelopmentModelInputStore.getInstance().consumeOwn(
                            session.getOwnerFingerprint(),
                            session.getSessionId(),
                            scenarioId);
            if (consumed == null) {
                throw violation("staged model input is unavailable");
            }
            try {
                stagedInput = consumed.getReceipt();
                stagedImagePresent = consumed.hasImage();
                stagedImage = stagedImagePresent
                        ? consumed.copyImageBytes() : new byte[0];
            } finally {
                consumed.close();
            }
        }
        try {
        String inputAggregateDigest = stagedInput == null
                ? "0".repeat(64) : stagedInput.inputAggregateDigest;
        String inputDigest = digest(
                "model-input", requestDigest, String.join("|", contextDigests),
                suggestion.getSuggestionDigest(), inputAggregateDigest);
        CockpitModelPrompt prompt;
        CabinComplianceAgentRouter.RouteDecision specialistRoute = null;
        if ("scene.aios.freeform.v1".equals(scenarioId)) {
            prompt = CockpitModelPrompt.forFreeform(
                    inputDigest, stagedInput.inputText);
        } else if (CabinComplianceAgentRouter.SMOKING_SCENARIO_ID.equals(scenarioId)) {
            if (!stagedImagePresent) {
                throw violation("smoking specialist requires one bound image");
            }
            specialistRoute = new CabinComplianceAgentRouter()
                    .routeExplicit(scenarioId);
            prompt = CockpitModelPrompt.forSmokingDetection(
                    inputDigest,
                    stagedInput.inputText,
                    specialistRoute.getSpecialistAgentId(),
                    smokingAgentInstruction);
        } else if (stagedInput != null) {
            prompt = CockpitModelPrompt.forMultimodal(
                    inputDigest, stagedInput.inputText);
        } else {
            prompt = CockpitModelPrompt.forScenario(inputDigest, scenarioId);
        }
        ModelContractV2.ModelRequest modelRequest = new ModelContractV2.ModelRequest(
                "decision." + requestDigest.substring(0, 24),
                ModelContractV2.Purpose.SCENARIO_REASONING,
                ModelContractV2.PrivacyClass.INTERNAL,
                new ModelContractV2.LatencyBudget(
                        networkModel ? MODEL_INFERENCE_TIMEOUT_MS : 1_000),
                new ModelContractV2.TokenBudget(128, 128, 256),
                stagedImagePresent
                        ? ModelContractV2.RequiredCapability.VISION_CLASSIFICATION
                        : ModelContractV2.RequiredCapability.TEXT_GENERATION,
                ModelContractV2.FallbackPolicy.NO_FALLBACK,
                digest("model-trace", session.getSessionId(), requestDigest),
                inputDigest);
        SelectedModel selectedModel = new SelectedModel(
                "model.default.v1", modelSpec, modelProvider, vllmEngine);
        ModelProfileRouter.RouteDecision profileRoute = null;
        boolean providerSetReady = networkModelMode == NetworkModelMode.VLLM_DEVELOPMENT
                ? isProviderReady(modelProvider) || isProviderReady(smokingModelProvider)
                : isProviderReady(selectedModel.provider);
        if (!providerSetReady) {
            throw violation("selected model provider is not ready");
        }
        String healthEvidence = digest(
                "model-health",
                requestDigest,
                providerId,
                networkModelMode == NetworkModelMode.VLLM_DEVELOPMENT
                        ? digest(
                                "model-profile-set-v1",
                                modelSpec.getArtifactDigest(),
                                smokingModelSpec.getArtifactDigest())
                        : selectedModel.spec.getArtifactDigest());
        ModelProviderRegistry.PublishResult health = modelRegistry.publishHealth(
                new ModelProviderRegistry.HealthReport(
                        providerId,
                        healthSource,
                        ModelProviderRegistry.HealthState.HEALTHY,
                        ++modelHealthRevision,
                        now,
                        now + MODEL_HEALTH_WINDOW_MS,
                        healthEvidence),
                now);
        if (health.getCode() != ModelProviderRegistry.PublishCode.UPDATED) {
            throw violation("model health publication failed closed");
        }
        PolicyAwareModelRouter.RouteDecision route = PolicyAwareModelRouter.decide(
                modelRequest,
                new PolicyAwareModelRouter.PolicySnapshot(
                        routeMode,
                        networkModel
                                ? PolicyAwareModelRouter.NetworkPolicy.ALLOW_ANY
                                : PolicyAwareModelRouter.NetworkPolicy.OFFLINE_ONLY,
                        networkModel
                                ? PolicyAwareModelRouter.NetworkState.UNMETERED
                                : PolicyAwareModelRouter.NetworkState.UNAVAILABLE,
                        PolicyAwareModelRouter.ThermalState.NOMINAL,
                        1,
                        256,
                        modelHealthRevision,
                        now,
                        now + MODEL_HEALTH_WINDOW_MS,
                        digest("model-policy", requestDigest)),
                modelRegistry.snapshot(now),
                now);
        if (route.getCode() != PolicyAwareModelRouter.DecisionCode.SELECTED
                || !providerId.equals(route.getPrimaryProviderId())
                || route.isActionAuthorizationGranted()
                || route.isEffectDispatchRequested()) {
            throw violation("model policy route failed closed");
        }
        if (networkModelMode == NetworkModelMode.VLLM_DEVELOPMENT) {
            ModelProfileRouter.TargetHealth generalHealth = targetHealth(
                    BuildConfig.VLLM_TARGET_ETHERNET_ENABLED
                            ? ModelProfileRouter.TARGET_GENERAL_PROFILE_ID
                            : ModelProfileRouter.GENERAL_PROFILE_ID,
                    modelSpec,
                    generalVllmEndpoint(),
                    modelProvider,
                    modelHealthRevision,
                    now,
                    requestDigest);
            ModelProfileRouter.TargetHealth smokingHealth = targetHealth(
                    ModelProfileRouter.SMOKING_PROFILE_ID,
                    smokingModelSpec,
                    smokingVllmEndpoint(),
                    smokingModelProvider,
                    modelHealthRevision,
                    now,
                    requestDigest);
            profileRoute = ModelProfileRouter.decide(
                    scenarioId,
                    modelRequest,
                    generalHealth,
                    smokingHealth,
                    BuildConfig.VLLM_TARGET_ETHERNET_ENABLED
                            ? ModelProfileRouter.DeploymentProfile.SINGLE_2B_TARGET_ETHERNET
                            : ModelProfileRouter.DeploymentProfile.DUAL_MODEL_DEVELOPMENT,
                    now);
            boolean smokingWorkload = CabinComplianceAgentRouter.SMOKING_SCENARIO_ID
                    .equals(scenarioId);
            ModelProvider selectedProvider = smokingWorkload
                    ? smokingModelProvider : modelProvider;
            ModelProvider.ModelSpec selectedSpec = smokingWorkload
                    ? smokingModelSpec : modelSpec;
            VllmInferenceEngine selectedEngine = smokingWorkload
                    ? smokingVllmEngine : vllmEngine;
            if (profileRoute.getCode() != ModelProfileRouter.DecisionCode.SELECTED
                    || !selectedSpec.getModelId().equals(profileRoute.getModelId())
                    || profileRoute.isFallbackSelected()
                    || profileRoute.isActionAuthorizationGranted()
                    || profileRoute.isEffectDispatchRequested()) {
                throw violation(
                        "model profile route failed closed: "
                                + profileRoute.getRejectionReason().name());
            }
            selectedModel = new SelectedModel(
                    profileRoute.getProfileId(),
                    selectedSpec,
                    selectedProvider,
                    selectedEngine);
        }
        if (!isProviderReady(selectedModel.provider)) {
            throw violation("selected model profile provider is not ready");
        }
        RecordingObserver observer = new RecordingObserver();
        if (networkModel) {
            if (isOpenClawMode(networkModelMode)) {
                openClawEngine.registerPrompt(prompt);
                if (stagedImagePresent) {
                    try {
                        openClawEngine.registerScenarioImageAttachment(
                                inputDigest,
                                stagedInput.imageMimeType,
                                stagedInput.imageFileName,
                                stagedImage);
                    } finally {
                        Arrays.fill(stagedImage, (byte) 0);
                    }
                }
            } else {
                selectedModel.vllmEngine.registerPrompt(prompt);
                if (stagedImagePresent) {
                    try {
                        selectedModel.vllmEngine.registerScenarioImageAttachment(
                                inputDigest,
                                stagedInput.imageMimeType,
                                stagedInput.imageFileName,
                                stagedImage);
                    } finally {
                        Arrays.fill(stagedImage, (byte) 0);
                    }
                }
            }
            ModelProvider.InferenceHandle handle = selectedModel.provider.infer(
                    new ModelProvider.InferenceRequest(
                            modelRequest.getRequestId(),
                            selectedModel.spec.getModelId(),
                            inputDigest,
                            now + MODEL_INFERENCE_TIMEOUT_MS,
                            true),
                    observer);
            if (!providerId.equals(handle.getProviderId())) {
                throw violation("development model provider identity mismatch");
            }
        } else {
            TestOnlyModelRouter.SubmitResult submitted = testModelRouter.submit(
                    TestOnlyModelRouter.TrustedRouteRequest.fromRuntimePolicy(
                            modelRequest.getRequestId(),
                            session.getOwnerFingerprint(),
                            modelSpec.getModelId(),
                            inputDigest,
                            InferenceResourceScheduler.EffectivePriority.NORMAL,
                            now + 10_000L,
                            5_000L,
                            true),
                    observer);
            if (!submitted.isDispatched()) {
                throw violation("test model request was not dispatched");
            }
        }
        modelExecutor.drain();
        if (observer.terminal == null
                || observer.terminal.getState() != ModelProvider.TerminalState.COMPLETED
                || observer.terminal.getOutputDigest() == null
                || observer.chunkCount < 1) {
            String failureCode = isOpenClawMode(networkModelMode)
                    ? openClawEngine.snapshot().getLastFailureCode()
                    : networkModelMode == NetworkModelMode.VLLM_DEVELOPMENT
                            ? selectedModel.vllmEngine.snapshot().getLastFailureCode()
                            : "";
            throw violation("model inference did not complete"
                    + (failureCode.isEmpty() ? "" : ": " + failureCode));
        }
        if (networkModel) {
            long latencyMs = isOpenClawMode(networkModelMode)
                    ? openClawEngine.snapshot().getLastLatencyMs()
                    : selectedModel.vllmEngine.snapshot().getLastLatencyMs();
            long completedCount = isOpenClawMode(networkModelMode)
                    ? openClawEngine.snapshot().getCompletedCount()
                    : selectedModel.vllmEngine.snapshot().getCompletedCount();
            ModelProjection projection = parseModelProjection(
                    observer.contentBytes(), prompt, completedCount, latencyMs);
            String admittedRouteDigest = route.getDecisionDigest();
            if (profileRoute != null) {
                admittedRouteDigest = digest(
                        "model-profile-route-v1",
                        admittedRouteDigest,
                        profileRoute.getDecisionDigest());
            }
            if (specialistRoute != null) {
                admittedRouteDigest = digest(
                        "model-specialist-route-v1",
                        admittedRouteDigest,
                        specialistRoute.getRouteDigest());
            }
            return new ModelEvidence(
                    admittedRouteDigest,
                    observer.terminal.getOutputDigest(),
                    healthEvidence,
                    true,
                    projection.assistantDisplayText,
                    providerId,
                    selectedModel.profileId,
                    selectedModel.spec.getModelId(),
                    projection.latencyMs,
                    inputAggregateDigest,
                    stagedImagePresent,
                    projection.admittedActions);
        }
        return new ModelEvidence(
                route.getDecisionDigest(),
                observer.terminal.getOutputDigest(),
                healthEvidence,
                false,
                "",
                "",
                "",
                "",
                0L,
                inputAggregateDigest,
                false,
                List.of());
        } finally {
            if (stagedImage != null) {
                Arrays.fill(stagedImage, (byte) 0);
            }
        }
    }

    private static boolean warmupWithoutFallback(
            ModelProvider provider,
            ModelProvider.ModelSpec spec) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(spec, "spec");
        if (isProviderReady(provider)) {
            return true;
        }
        try {
            provider.warmup(spec);
            return isProviderReady(provider);
        } catch (LocalModelProvider.ProviderUnavailableException unavailable) {
            return false;
        }
    }

    private static boolean isProviderReady(ModelProvider provider) {
        ModelProvider.Snapshot snapshot = Objects.requireNonNull(
                provider, "provider").snapshot();
        return snapshot.getLifecycleState() == ModelProvider.LifecycleState.READY
                && snapshot.getHealthState() == ModelProvider.HealthState.HEALTHY
                && snapshot.getLoadedModelCount() == 1;
    }

    private static ModelProfileRouter.TargetHealth targetHealth(
            String profileId,
            ModelProvider.ModelSpec spec,
            VllmEndpointConfig endpoint,
            ModelProvider provider,
            long revision,
            long now,
            String requestDigest) {
        ModelProvider.Snapshot snapshot = provider.snapshot();
        return new ModelProfileRouter.TargetHealth(
                profileId,
                spec.getModelId(),
                endpoint.getModelName(),
                endpoint.getMaximumContextTokens(),
                isProviderReady(provider),
                revision,
                now,
                now + MODEL_HEALTH_WINDOW_MS,
                digest(
                        "model-profile-health-v1",
                        requestDigest,
                        profileId,
                        spec.getArtifactDigest(),
                        endpoint.getModelName(),
                        snapshot.getLifecycleState().name(),
                        snapshot.getHealthState().name(),
                        snapshot.getDetailCode()));
    }

    private static ModelProjection parseModelProjection(
            byte[] canonicalOutput,
            CockpitModelPrompt prompt,
            long engineCompletedCount,
            long engineLatencyMs) {
        try {
            String expectedScenarioId = prompt.getScenarioId();
            JsonObject output = JsonParser.parseString(
                    new String(canonicalOutput, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!expectedScenarioId.equals(output.get("scenario_id").getAsString())) {
                throw violation("model projection scenario differs from request");
            }
            String reply = output.get("reply").getAsString().trim();
            if (reply.isEmpty() || reply.length() > 256) {
                throw violation("model projection reply is outside the bound");
            }
            if (engineCompletedCount < 1
                    || engineLatencyMs < 0L
                    || engineLatencyMs > MODEL_INFERENCE_TIMEOUT_MS) {
                throw violation("model projection latency evidence is invalid");
            }
            List<String> admittedActions = new ArrayList<>();
            output.getAsJsonArray("actions").forEach(element -> {
                if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                    throw violation("model projection action is invalid");
                }
                admittedActions.add(element.getAsString());
            });
            prompt.validateAdmittedActions(admittedActions);
            return new ModelProjection(reply, engineLatencyMs, admittedActions);
        } catch (RuntimeException failure) {
            if (failure instanceof IllegalArgumentException
                    && failure.getMessage() != null
                    && failure.getMessage().startsWith("CB_DEBUG_DECISION_COMPOSITION:")) {
                throw failure;
            }
            throw violation("validated model output could not be projected");
        }
    }

    private EventEvidence publishDecisionEvents(
            OrchestrationBackend.SessionDescriptor session,
            String requestDigest,
            ModelEvidence model,
            ProactiveConsentPolicy.AdmissionDecision consentDecision) {
        RecordingEventObserver observer = new RecordingEventObserver();
        BoundedEventRuntime.SubscribeResult subscription = events.subscribe(
                BoundedEventRuntime.TrustedSubscription.fromRuntimePolicy(
                        "decision." + requestDigest.substring(0, 24),
                        session.getOwnerFingerprint(),
                        List.of(
                                BoundedEventRuntime.TOPIC_MODEL_HEALTH,
                                BoundedEventRuntime.TOPIC_POLICY_DECISION),
                        events.snapshot().getLatestSequence(),
                        4),
                observer);
        if (subscription.getOutcome() != BoundedEventRuntime.SubscribeOutcome.CREATED
                || subscription.getSubscription() == null) {
            throw violation("decision Event subscription failed closed");
        }
        BoundedEventRuntime.PublishResult health = events.publish(
                BoundedEventRuntime.TrustedPublication.fromRuntimePolicy(
                        BoundedEventRuntime.TOPIC_MODEL_HEALTH,
                        "central.model-health.v1",
                        model.healthEvidenceDigest));
        BoundedEventRuntime.PublishResult policy = events.publish(
                BoundedEventRuntime.TrustedPublication.fromRuntimePolicy(
                        BoundedEventRuntime.TOPIC_POLICY_DECISION,
                        "central.policy-decision.v1",
                        digest("consent-decision", requestDigest, consentDecision.getCode().name(),
                                model.routeDigest)));
        String subscriptionId = subscription.getSubscription().getSubscriptionId();
        BoundedEventRuntime.DispatchResult dispatch = events.dispatchOwned(
                subscriptionId, session.getOwnerFingerprint(), 4);
        BoundedEventRuntime.CancelResult cancel = events.cancelOwned(
                subscriptionId, session.getOwnerFingerprint());
        if (health.getOutcome() != BoundedEventRuntime.PublishOutcome.PUBLISHED
                || policy.getOutcome() != BoundedEventRuntime.PublishOutcome.PUBLISHED
                || dispatch.getOutcome() != BoundedEventRuntime.DispatchOutcome.DELIVERED
                || dispatch.getDeliveredEventCount() != 2
                || observer.eventDigests.size() != 2
                || cancel.getOutcome() != BoundedEventRuntime.CancelOutcome.CANCELLED) {
            throw violation("decision Event delivery failed closed");
        }
        long lastSequence = policy.getEvent().getSequence();
        return new EventEvidence(
                dispatch.getDeliveredEventCount(),
                lastSequence,
                digest("decision-events", String.join("|", observer.eventDigests),
                        Long.toString(lastSequence)));
    }

    private static TriggerRule.Manifest triggerManifest(ScenarioCatalog catalog) {
        List<TriggerRule> rules = new ArrayList<>();
        rules.add(rule(
                "trigger.cold.driver.v1",
                "scene.comfort.cold.v1",
                catalog.require("scene.comfort.cold.v1").getArtifactDigest(),
                TriggerRule.Metric.CABIN_TEMPERATURE_C,
                TriggerRule.ThresholdOperator.LESS_THAN,
                18.0));
        rules.add(rule(
                "trigger.fatigue.driver.v1",
                "scene.fatigue.assist.v1",
                catalog.require("scene.fatigue.assist.v1").getArtifactDigest(),
                TriggerRule.Metric.DRIVER_FATIGUE_SCORE,
                TriggerRule.ThresholdOperator.GREATER_THAN_OR_EQUAL,
                0.8));
        try {
            rules.add(rule(
                    "trigger.cabin.multimodal.driver.v1",
                    "scene.cabin.multimodal.assist.v1",
                    catalog.require("scene.cabin.multimodal.assist.v1")
                            .getArtifactDigest(),
                    TriggerRule.Metric.CABIN_CO2_PPM,
                    TriggerRule.ThresholdOperator.GREATER_THAN_OR_EQUAL,
                    1_000.0));
        } catch (IllegalArgumentException ignored) {
            // Older focused contract fixtures intentionally load only legacy scenarios.
        }
        try {
            rules.add(rule(
                    "trigger.aios.freeform.driver.v1",
                    "scene.aios.freeform.v1",
                    catalog.require("scene.aios.freeform.v1").getArtifactDigest(),
                    TriggerRule.Metric.DRIVER_ATTENTION_SCORE,
                    TriggerRule.ThresholdOperator.GREATER_THAN_OR_EQUAL,
                    0.5));
        } catch (IllegalArgumentException ignored) {
            // Older focused contract fixtures intentionally load only legacy scenarios.
        }
        try {
            rules.add(rule(
                    "trigger.cabin.smoking.explicit.v1",
                    CabinComplianceAgentRouter.SMOKING_SCENARIO_ID,
                    catalog.require(CabinComplianceAgentRouter.SMOKING_SCENARIO_ID)
                            .getArtifactDigest(),
                    TriggerRule.Metric.CABIN_IMAGE_AVAILABLE,
                    TriggerRule.ThresholdOperator.GREATER_THAN_OR_EQUAL,
                    1.0));
        } catch (IllegalArgumentException ignored) {
            // Older focused contract fixtures intentionally load only legacy scenarios.
        }
        return new TriggerRule.Manifest("trigger-manifest.debug-decision.v1", 1, rules);
    }

    private static TriggerRule rule(
            String ruleId,
            String scenarioId,
            String scenarioDigest,
            TriggerRule.Metric metric,
            TriggerRule.ThresholdOperator operator,
            double threshold) {
        return new TriggerRule(
                ruleId,
                scenarioId,
                scenarioDigest,
                metric,
                CabinComplianceAgentRouter.SMOKING_SCENARIO_ID.equals(scenarioId)
                        ? ScenarioManifest.Zone.CABIN
                        : ScenarioManifest.Zone.ROW1_DRIVER,
                operator,
                threshold,
                100,
                100,
                3,
                0,
                1_000,
                100);
    }

    private static TriggerRule.Metric metricForScenario(String scenarioId) {
        if ("scene.comfort.cold.v1".equals(scenarioId)) {
            return TriggerRule.Metric.CABIN_TEMPERATURE_C;
        }
        if ("scene.fatigue.assist.v1".equals(scenarioId)) {
            return TriggerRule.Metric.DRIVER_FATIGUE_SCORE;
        }
        if ("scene.cabin.multimodal.assist.v1".equals(scenarioId)) {
            return TriggerRule.Metric.CABIN_CO2_PPM;
        }
        if ("scene.aios.freeform.v1".equals(scenarioId)) {
            return TriggerRule.Metric.DRIVER_ATTENTION_SCORE;
        }
        if (CabinComplianceAgentRouter.SMOKING_SCENARIO_ID.equals(scenarioId)) {
            return TriggerRule.Metric.CABIN_IMAGE_AVAILABLE;
        }
        throw violation("scenario Trigger metric is unavailable");
    }

    private static VehicleCapability.CapabilityId capabilityForScenario(String scenarioId) {
        if ("scene.comfort.cold.v1".equals(scenarioId)) {
            return VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE;
        }
        if ("scene.fatigue.assist.v1".equals(scenarioId)) {
            return VehicleCapability.CapabilityId.SEAT_RECLINE_ANGLE;
        }
        if ("scene.cabin.multimodal.assist.v1".equals(scenarioId)) {
            return VehicleCapability.CapabilityId.NAVIGATION_POI;
        }
        if ("scene.aios.freeform.v1".equals(scenarioId)) {
            return VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE;
        }
        if (CabinComplianceAgentRouter.SMOKING_SCENARIO_ID.equals(scenarioId)) {
            return VehicleCapability.CapabilityId.CABIN_SMOKING_DETECTION;
        }
        throw violation("scenario consent capability is unavailable");
    }

    private static double triggerValueForScenario(String scenarioId) {
        if ("scene.fatigue.assist.v1".equals(scenarioId)) return 0.8;
        if ("scene.aios.freeform.v1".equals(scenarioId)) return 0.9;
        if ("scene.cabin.multimodal.assist.v1".equals(scenarioId)) return 1_500.0;
        if (CabinComplianceAgentRouter.SMOKING_SCENARIO_ID.equals(scenarioId)) return 1.0;
        if ("scene.comfort.cold.v1".equals(scenarioId)) return 17.0;
        throw violation("scenario Trigger value is unavailable");
    }

    private static String requireAgentInstruction(String value) {
        String instruction = Objects.requireNonNull(
                value, "smokingAgentInstruction").trim();
        if (instruction.isEmpty() || instruction.length() > 8_192) {
            throw violation("smoking agent instruction is outside bounds");
        }
        return instruction;
    }

    private static Supplier<String> sequence(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return () -> prefix + sequence.incrementAndGet();
    }

    static String combine(Evidence decision, DebugRuntimeCompositionBoundary.Evidence runtime) {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(runtime, "runtime");
        return digest("central-brain-debug-composition-binding-v1",
                decision.getDigest(), runtime.getDigest());
    }

    private static String digest(String domain, String... parts) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
        update(digest, domain);
        for (String part : parts) {
            update(digest, Objects.requireNonNull(part, "digest part"));
        }
        byte[] bytes = digest.digest();
        char[] output = new char[bytes.length * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            output[index * 2] = alphabet[value >>> 4];
            output[index * 2 + 1] = alphabet[value & 0xf];
        }
        return new String(output);
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static void requireDigest(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256");
        }
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_DEBUG_DECISION_COMPOSITION: " + message);
    }

    static final class Evidence {
        private final String digest;
        private final int contextObservationCount;
        private final String suggestionDigest;
        private final ProactiveConsentPolicy.AdmissionCode consentCode;
        private final String modelRouteDigest;
        private final String modelOutputDigest;
        private final int deliveredEventCount;
        private final long lastEventSequence;
        private final boolean fatigueSourceStubbed;
        private final boolean networkAccessed;
        private final String assistantDisplayText;
        private final String modelProviderId;
        private final String modelProfileId;
        private final String modelId;
        private final long modelLatencyMs;
        private final String inputAggregateDigest;
        private final boolean imageConsumed;
        private final List<String> admittedActions;

        private Evidence(
                String digest,
                int contextObservationCount,
                String suggestionDigest,
                ProactiveConsentPolicy.AdmissionCode consentCode,
                String modelRouteDigest,
                String modelOutputDigest,
                int deliveredEventCount,
                long lastEventSequence,
                boolean fatigueSourceStubbed,
                boolean networkAccessed,
                String assistantDisplayText,
                String modelProviderId,
                String modelProfileId,
                String modelId,
                long modelLatencyMs,
                String inputAggregateDigest,
                boolean imageConsumed,
                List<String> admittedActions) {
            this.digest = digest;
            this.contextObservationCount = contextObservationCount;
            this.suggestionDigest = suggestionDigest;
            this.consentCode = consentCode;
            this.modelRouteDigest = modelRouteDigest;
            this.modelOutputDigest = modelOutputDigest;
            this.deliveredEventCount = deliveredEventCount;
            this.lastEventSequence = lastEventSequence;
            this.fatigueSourceStubbed = fatigueSourceStubbed;
            this.networkAccessed = networkAccessed;
            this.assistantDisplayText = assistantDisplayText;
            this.modelProviderId = modelProviderId;
            this.modelProfileId = modelProfileId;
            this.modelId = modelId;
            this.modelLatencyMs = modelLatencyMs;
            this.inputAggregateDigest = inputAggregateDigest;
            this.imageConsumed = imageConsumed;
            this.admittedActions = Collections.unmodifiableList(
                    new ArrayList<>(Objects.requireNonNull(
                            admittedActions, "admittedActions")));
        }

        String getDigest() { return digest; }
        int getContextObservationCount() { return contextObservationCount; }
        String getSuggestionDigest() { return suggestionDigest; }
        ProactiveConsentPolicy.AdmissionCode getConsentCode() { return consentCode; }
        String getModelRouteDigest() { return modelRouteDigest; }
        String getModelOutputDigest() { return modelOutputDigest; }
        int getDeliveredEventCount() { return deliveredEventCount; }
        long getLastEventSequence() { return lastEventSequence; }
        boolean isFatigueSourceStubbed() { return fatigueSourceStubbed; }
        boolean isAutoExecutionAuthorized() { return false; }
        boolean isProductionAuthority() { return false; }
        boolean isNetworkAccessed() { return networkAccessed; }
        String getAssistantDisplayText() { return assistantDisplayText; }
        String getModelProviderId() { return modelProviderId; }
        String getModelProfileId() { return modelProfileId; }
        String getModelId() { return modelId; }
        long getModelLatencyMs() { return modelLatencyMs; }
        String getInputAggregateDigest() { return inputAggregateDigest; }
        boolean isImageConsumed() { return imageConsumed; }
        List<String> getAdmittedActions() { return admittedActions; }
        boolean isNpuAccessed() { return false; }
        boolean isHardwareAccessed() { return false; }
    }

    static final class Completion {
        private final boolean replayed;

        private Completion(boolean replayed) {
            this.replayed = replayed;
        }

        boolean isReplayed() { return replayed; }
    }

    static final class Snapshot {
        private final int sessionCount;
        private final int completedCount;
        private final long suggestionCount;
        private final long completedModelCount;
        private final int activeEventSubscriptionCount;

        private Snapshot(
                int sessionCount,
                int completedCount,
                long suggestionCount,
                long completedModelCount,
                int activeEventSubscriptionCount) {
            this.sessionCount = sessionCount;
            this.completedCount = completedCount;
            this.suggestionCount = suggestionCount;
            this.completedModelCount = completedModelCount;
            this.activeEventSubscriptionCount = activeEventSubscriptionCount;
        }

        int getSessionCount() { return sessionCount; }
        int getCompletedCount() { return completedCount; }
        long getSuggestionCount() { return suggestionCount; }
        long getCompletedModelCount() { return completedModelCount; }
        int getActiveEventSubscriptionCount() { return activeEventSubscriptionCount; }
    }

    private static final class Entry {
        private final String requestDigest;
        private final Evidence evidence;
        private boolean completed;

        private Entry(String requestDigest, Evidence evidence) {
            this.requestDigest = requestDigest;
            this.evidence = evidence;
        }
    }

    private static final class SelectedModel {
        private final String profileId;
        private final ModelProvider.ModelSpec spec;
        private final ModelProvider provider;
        private final VllmInferenceEngine vllmEngine;

        private SelectedModel(
                String profileId,
                ModelProvider.ModelSpec spec,
                ModelProvider provider,
                VllmInferenceEngine vllmEngine) {
            this.profileId = Objects.requireNonNull(profileId, "profileId");
            this.spec = Objects.requireNonNull(spec, "spec");
            this.provider = Objects.requireNonNull(provider, "provider");
            this.vllmEngine = vllmEngine;
        }
    }

    private static final class ModelEvidence {
        private final String routeDigest;
        private final String outputDigest;
        private final String healthEvidenceDigest;
        private final boolean networkAccessed;
        private final String assistantDisplayText;
        private final String providerId;
        private final String profileId;
        private final String modelId;
        private final long latencyMs;
        private final String inputAggregateDigest;
        private final boolean imageConsumed;
        private final List<String> admittedActions;

        private ModelEvidence(
                String routeDigest,
                String outputDigest,
                String healthEvidenceDigest,
                boolean networkAccessed,
                String assistantDisplayText,
                String providerId,
                String profileId,
                String modelId,
                long latencyMs,
                String inputAggregateDigest,
                boolean imageConsumed,
                List<String> admittedActions) {
            this.routeDigest = routeDigest;
            this.outputDigest = outputDigest;
            this.healthEvidenceDigest = healthEvidenceDigest;
            this.networkAccessed = networkAccessed;
            this.assistantDisplayText = assistantDisplayText;
            this.providerId = providerId;
            this.profileId = profileId;
            this.modelId = modelId;
            this.latencyMs = latencyMs;
            this.inputAggregateDigest = inputAggregateDigest;
            this.imageConsumed = imageConsumed;
            this.admittedActions = Collections.unmodifiableList(
                    new ArrayList<>(admittedActions));
        }
    }

    private static final class ModelProjection {
        private final String assistantDisplayText;
        private final long latencyMs;
        private final List<String> admittedActions;

        private ModelProjection(
                String assistantDisplayText,
                long latencyMs,
                List<String> admittedActions) {
            this.assistantDisplayText = assistantDisplayText;
            this.latencyMs = latencyMs;
            this.admittedActions = Collections.unmodifiableList(
                    new ArrayList<>(admittedActions));
        }
    }

    private static final class EventEvidence {
        private final int deliveredCount;
        private final long lastSequence;
        private final String digest;

        private EventEvidence(int deliveredCount, long lastSequence, String digest) {
            this.deliveredCount = deliveredCount;
            this.lastSequence = lastSequence;
            this.digest = digest;
        }
    }

    private static final class ManualExecutor implements Executor {
        private final Queue<Runnable> pending = new ArrayDeque<>();

        @Override
        public void execute(Runnable command) {
            pending.add(Objects.requireNonNull(command, "command"));
        }

        private void drain() {
            while (!pending.isEmpty()) {
                pending.remove().run();
            }
        }
    }

    private static final class RecordingObserver implements ModelProvider.StreamObserver {
        private ModelProvider.TerminalResult terminal;
        private int chunkCount;
        private final ByteArrayOutputStream content = new ByteArrayOutputStream();

        @Override
        public void onChunk(ModelProvider.StreamChunk chunk) {
            byte[] bytes = chunk.getContent();
            if (content.size() + bytes.length > MAX_MODEL_PROJECTION_BYTES) {
                throw violation("model projection exceeds the bounded envelope");
            }
            content.write(bytes, 0, bytes.length);
            chunkCount++;
        }

        @Override
        public void onTerminal(ModelProvider.TerminalResult result) {
            terminal = result;
        }

        private byte[] contentBytes() {
            return content.toByteArray();
        }
    }

    private static final class RecordingEventObserver
            implements BoundedEventRuntime.EventObserver {
        private final List<String> eventDigests = new ArrayList<>();

        @Override
        public void onOverflow(BoundedEventRuntime.OverflowSignal overflow) {
            throw violation("unexpected Event overflow");
        }

        @Override
        public void onEvent(BoundedEventRuntime.EventEnvelope event) {
            eventDigests.add(event.getPayloadDigest());
        }

        @Override
        public void onClosed(String subscriptionId, BoundedEventRuntime.CloseReason reason) {
            // Owner cancellation is the expected terminal state.
        }
    }
}
