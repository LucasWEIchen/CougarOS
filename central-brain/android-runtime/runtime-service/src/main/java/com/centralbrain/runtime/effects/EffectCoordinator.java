package com.centralbrain.runtime.effects;

import com.centralbrain.runtime.effects.AdapterRegistry.PrepareResult;
import com.centralbrain.runtime.effects.AdapterRegistry.PreparedMaterial;
import com.centralbrain.runtime.effects.AdapterRegistry.Profile;
import com.centralbrain.runtime.effects.AdapterRegistry.Resolution;
import com.centralbrain.sdk.effect.EffectContract;
import com.centralbrain.sdk.effect.EffectIntent;
import com.centralbrain.sdk.effect.EffectObservation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Two-phase Effect coordinator. It owns no threads, persistence, Graph dispatch, or real adapter. */
public final class EffectCoordinator {
    private static final String RUNTIME_SOURCE_ID = "runtime.effect.coordinator";

    public enum BatchStatus {
        ALL_DISPATCHED,
        PARTIAL,
        FAILED,
        UNKNOWN,
        PREPARE_REJECTED
    }

    public enum Outcome {
        DELIVERED,
        UNKNOWN,
        RETRYABLE_FAILURE,
        TERMINAL_FAILURE,
        PREPARE_REJECTED,
        BATCH_ABORTED,
        DEPENDENCY_BLOCKED
    }

    private final AdapterRegistry adapterRegistry;
    private final EffectDependencyPlanner dependencyPlanner;

    public EffectCoordinator(
            AdapterRegistry adapterRegistry,
            EffectDependencyPlanner dependencyPlanner) {
        this.adapterRegistry = Objects.requireNonNull(adapterRegistry, "adapterRegistry");
        this.dependencyPlanner = Objects.requireNonNull(
                dependencyPlanner, "dependencyPlanner");
    }

    public ExecutionResult execute(EffectBatch batch, Profile profile, long nowEpochMs) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(profile, "profile");
        if (nowEpochMs <= 0L) {
            throw violation("execution time must be positive");
        }
        for (EffectBatch.Entry entry : batch.getEntries()) {
            EffectContract.validateIntent(entry.internalIntentCopy(), nowEpochMs);
        }
        EffectDependencyPlanner.Plan plan = dependencyPlanner.plan(batch);
        Map<String, PreparedSlot> prepared = prepare(batch, plan, profile, nowEpochMs);
        boolean requiredPrepareRejected = false;
        for (EffectBatch.Entry entry : batch.getEntries()) {
            if (entry.isRequired() && !prepared.get(entry.getEffectId()).isReady()) {
                requiredPrepareRejected = true;
                break;
            }
        }
        if (requiredPrepareRejected) {
            return abortBeforeDispatch(batch, plan, prepared, nowEpochMs);
        }
        return dispatch(batch, plan, prepared, nowEpochMs);
    }

    private Map<String, PreparedSlot> prepare(
            EffectBatch batch,
            EffectDependencyPlanner.Plan plan,
            Profile profile,
            long nowEpochMs) {
        Map<String, PreparedSlot> result = new LinkedHashMap<>();
        for (EffectDependencyPlanner.Wave wave : plan.getWaves()) {
            for (String effectId : wave.getEffectIds()) {
                EffectBatch.Entry entry = batch.requireEntry(effectId);
                EffectIntent intent = entry.internalIntentCopy();
                Resolution resolution = null;
                try {
                    resolution = adapterRegistry.resolve(
                            intent.capabilityId, intent.targetArea, profile);
                    PrepareResult prepareResult = Objects.requireNonNull(
                            resolution.preparationAdapter().prepare(intent, nowEpochMs),
                            "prepare result");
                    if (prepareResult.getState() == PrepareResult.State.REJECTED) {
                        result.put(effectId, PreparedSlot.rejected(
                                entry, resolution, prepareResult.getFailureCode()));
                        continue;
                    }
                    PreparedMaterial material = prepareResult.getMaterial();
                    if (!material.getActionId().equals(intent.actionId)
                            || !material.getDestination().equals(
                                    resolution.getDescriptor().getDestination())) {
                        result.put(effectId, PreparedSlot.rejected(
                                entry, resolution, "PREPARE_BINDING_MISMATCH"));
                        continue;
                    }
                    result.put(effectId, PreparedSlot.ready(entry, resolution, material));
                } catch (AdapterRegistry.AdapterUnavailableException exception) {
                    result.put(effectId, PreparedSlot.rejected(
                            entry, null, "ADAPTER_UNAVAILABLE"));
                } catch (RuntimeException exception) {
                    result.put(effectId, PreparedSlot.rejected(
                            entry, resolution, "PREPARE_EXCEPTION"));
                }
            }
        }
        return result;
    }

    private ExecutionResult abortBeforeDispatch(
            EffectBatch batch,
            EffectDependencyPlanner.Plan plan,
            Map<String, PreparedSlot> prepared,
            long nowEpochMs) {
        List<ItemResult> items = new ArrayList<>();
        for (EffectBatch.Entry entry : batch.getEntries()) {
            PreparedSlot slot = prepared.get(entry.getEffectId());
            if (slot.isReady()) {
                items.add(item(
                        batch,
                        entry,
                        slot,
                        Outcome.BATCH_ABORTED,
                        EffectContract.STATE_FAILED_TERMINAL,
                        "BATCH_PREPARE_ABORTED",
                        slot.material.getPreparationEvidenceDigest(),
                        nowEpochMs));
            } else {
                items.add(item(
                        batch,
                        entry,
                        slot,
                        Outcome.PREPARE_REJECTED,
                        EffectContract.STATE_REJECTED,
                        slot.failureCode,
                        failureEvidence(batch, entry, slot.failureCode),
                        nowEpochMs));
            }
        }
        return new ExecutionResult(batch, plan, BatchStatus.PREPARE_REJECTED, items);
    }

    private ExecutionResult dispatch(
            EffectBatch batch,
            EffectDependencyPlanner.Plan plan,
            Map<String, PreparedSlot> prepared,
            long nowEpochMs) {
        Map<String, ItemResult> dispatched = new LinkedHashMap<>();
        for (EffectDependencyPlanner.Wave wave : plan.getWaves()) {
            for (String effectId : wave.getEffectIds()) {
                EffectBatch.Entry entry = batch.requireEntry(effectId);
                PreparedSlot slot = prepared.get(effectId);
                if (!slot.isReady()) {
                    dispatched.put(effectId, item(
                            batch,
                            entry,
                            slot,
                            Outcome.PREPARE_REJECTED,
                            EffectContract.STATE_REJECTED,
                            slot.failureCode,
                            failureEvidence(batch, entry, slot.failureCode),
                            nowEpochMs));
                    continue;
                }
                if (!dependenciesDelivered(entry, dispatched)) {
                    dispatched.put(effectId, item(
                            batch,
                            entry,
                            slot,
                            Outcome.DEPENDENCY_BLOCKED,
                            EffectContract.STATE_FAILED_TERMINAL,
                            "DEPENDENCY_NOT_DELIVERED",
                            failureEvidence(batch, entry, "DEPENDENCY_NOT_DELIVERED"),
                            nowEpochMs));
                    continue;
                }
                dispatched.put(effectId, dispatchOne(batch, entry, slot, nowEpochMs));
            }
        }

        List<ItemResult> ordered = new ArrayList<>();
        for (EffectBatch.Entry entry : batch.getEntries()) {
            ordered.add(dispatched.get(entry.getEffectId()));
        }
        return new ExecutionResult(batch, plan, aggregate(ordered), ordered);
    }

    private static boolean dependenciesDelivered(
            EffectBatch.Entry entry,
            Map<String, ItemResult> results) {
        for (String dependencyId : entry.getDependencyEffectIds()) {
            ItemResult result = results.get(dependencyId);
            if (result == null || result.outcome != Outcome.DELIVERED) {
                return false;
            }
        }
        return true;
    }

    private ItemResult dispatchOne(
            EffectBatch batch,
            EffectBatch.Entry entry,
            PreparedSlot slot,
            long nowEpochMs) {
        EffectIntent intent = entry.internalIntentCopy();
        String token = EffectBatch.digest(
                "effect.idempotency.token.v1", intent.idempotencyKey);
        EffectAdapter.Invocation invocation = new EffectAdapter.Invocation(
                intent.effectId,
                "batch." + batch.getBatchId() + "." + intent.effectId,
                token,
                slot.material.getDestination(),
                intent.actionId,
                slot.material.getPayloadDigest(),
                slot.material.getEnvelopeDigest(),
                1,
                EffectContract.MAX_ATTEMPTS,
                slot.material.getCanonicalPayload(),
                slot.material.getCanonicalEnvelope());
        try {
            EffectAdapter.ApplyResult result = EffectAdapterContract.requireApplyResultMatches(
                    invocation, slot.resolution.adapter().apply(invocation));
            switch (result.getState()) {
                case APPLIED:
                    return item(
                            batch,
                            entry,
                            slot,
                            Outcome.DELIVERED,
                            EffectContract.STATE_DELIVERED,
                            "",
                            result.getEvidenceDigest(),
                            nowEpochMs);
                case RETRYABLE_FAILURE:
                    return item(
                            batch,
                            entry,
                            slot,
                            Outcome.RETRYABLE_FAILURE,
                            EffectContract.STATE_FAILED_RETRYABLE,
                            "ADAPTER_RETRYABLE",
                            result.getEvidenceDigest(),
                            nowEpochMs);
                case TERMINAL_FAILURE:
                    return item(
                            batch,
                            entry,
                            slot,
                            Outcome.TERMINAL_FAILURE,
                            EffectContract.STATE_FAILED_TERMINAL,
                            "ADAPTER_TERMINAL",
                            result.getEvidenceDigest(),
                            nowEpochMs);
                case UNKNOWN:
                default:
                    return item(
                            batch,
                            entry,
                            slot,
                            Outcome.UNKNOWN,
                            EffectContract.STATE_UNKNOWN,
                            "DELIVERY_UNKNOWN",
                            result.getEvidenceDigest(),
                            nowEpochMs);
            }
        } catch (EffectAdapterContract.UnsafeAdapterException exception) {
            return item(
                    batch,
                    entry,
                    slot,
                    Outcome.TERMINAL_FAILURE,
                    EffectContract.STATE_FAILED_TERMINAL,
                    "ADAPTER_CONTRACT_INVALID",
                    failureEvidence(batch, entry, "ADAPTER_CONTRACT_INVALID"),
                    nowEpochMs);
        } catch (RuntimeException exception) {
            return item(
                    batch,
                    entry,
                    slot,
                    Outcome.UNKNOWN,
                    EffectContract.STATE_UNKNOWN,
                    "DELIVERY_EXCEPTION",
                    failureEvidence(batch, entry, "DELIVERY_EXCEPTION"),
                    nowEpochMs);
        }
    }

    private static ItemResult item(
            EffectBatch batch,
            EffectBatch.Entry entry,
            PreparedSlot slot,
            Outcome outcome,
            int state,
            String failureCode,
            String evidenceDigest,
            long nowEpochMs) {
        EffectIntent intent = entry.internalIntentCopy();
        boolean runtimeSource = slot.resolution == null;
        int source = runtimeSource
                ? EffectContract.SOURCE_RUNTIME
                : slot.resolution.isSimulation()
                        ? EffectContract.SOURCE_SIMULATION
                        : EffectContract.SOURCE_ADAPTER;
        String sourceId = runtimeSource
                ? RUNTIME_SOURCE_ID
                : slot.resolution.getRegistrationId();
        int attempt = state == EffectContract.STATE_REJECTED ? 0 : 1;
        String observationSeed = EffectBatch.digest(
                "effect.coordinator.observation.id.v1",
                batch.getBatchDigest(),
                intent.effectId,
                outcome.name(),
                Integer.toString(state),
                failureCode,
                evidenceDigest);
        EffectObservation observation = new EffectObservation();
        observation.schemaVersion = EffectContract.SCHEMA_VERSION;
        observation.observationId = uuidFromDigest(observationSeed);
        observation.effectId = intent.effectId;
        observation.sessionId = intent.sessionId;
        observation.actionId = intent.actionId;
        observation.planDigest = intent.planDigest;
        observation.contextVersion = intent.contextVersion;
        observation.state = state;
        observation.source = source;
        observation.sourceId = sourceId;
        observation.attempt = attempt;
        observation.targetValueDigest = intent.targetValueDigest;
        observation.reportedValueDigest = "";
        observation.evidenceDigest = EffectBatch.requireDigest(
                evidenceDigest, "evidenceDigest");
        observation.failureCode = failureCode;
        observation.occurredAtEpochMs = nowEpochMs;
        observation.terminal = state == EffectContract.STATE_REJECTED
                || state == EffectContract.STATE_FAILED_TERMINAL;
        observation.retryable = state == EffectContract.STATE_FAILED_RETRYABLE;
        observation.simulated = source == EffectContract.SOURCE_SIMULATION;
        observation.observationDigest = EffectBatch.digest(
                "effect.coordinator.observation.v1",
                observation.observationId,
                observation.effectId,
                observation.sessionId,
                observation.actionId,
                observation.planDigest,
                Long.toString(observation.contextVersion),
                Integer.toString(observation.state),
                Integer.toString(observation.source),
                observation.sourceId,
                Integer.toString(observation.attempt),
                observation.targetValueDigest,
                observation.reportedValueDigest,
                observation.evidenceDigest,
                observation.failureCode,
                Long.toString(observation.occurredAtEpochMs),
                Boolean.toString(observation.terminal),
                Boolean.toString(observation.retryable),
                Boolean.toString(observation.simulated));
        EffectContract.validateObservation(observation);
        String beforeStateDigest = slot.material == null
                ? ""
                : slot.material.getBeforeStateDigest();
        return new ItemResult(
                intent.effectId,
                entry.getResourceKey(),
                entry.isRequired(),
                outcome,
                beforeStateDigest,
                observation);
    }

    private static BatchStatus aggregate(List<ItemResult> items) {
        boolean optionalFailure = false;
        boolean unknown = false;
        boolean requiredFailure = false;
        for (ItemResult item : items) {
            if (item.outcome == Outcome.UNKNOWN
                    || item.outcome == Outcome.RETRYABLE_FAILURE) {
                unknown = true;
            }
            if (item.outcome != Outcome.DELIVERED) {
                optionalFailure = true;
            }
            if (item.required
                    && item.outcome != Outcome.DELIVERED
                    && item.outcome != Outcome.UNKNOWN
                    && item.outcome != Outcome.RETRYABLE_FAILURE) {
                requiredFailure = true;
            }
        }
        if (requiredFailure) {
            return BatchStatus.FAILED;
        }
        if (unknown) {
            return BatchStatus.UNKNOWN;
        }
        return optionalFailure ? BatchStatus.PARTIAL : BatchStatus.ALL_DISPATCHED;
    }

    private static String failureEvidence(
            EffectBatch batch,
            EffectBatch.Entry entry,
            String failureCode) {
        return EffectBatch.digest(
                "effect.coordinator.failure.v1",
                batch.getBatchDigest(), entry.getEffectId(), failureCode);
    }

    private static String uuidFromDigest(String digest) {
        EffectBatch.requireDigest(digest, "UUID digest");
        char[] value = digest.substring(0, 32).toCharArray();
        value[12] = '5';
        value[16] = '8';
        String compact = new String(value);
        return compact.substring(0, 8)
                + "-" + compact.substring(8, 12)
                + "-" + compact.substring(12, 16)
                + "-" + compact.substring(16, 20)
                + "-" + compact.substring(20, 32);
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_EFFECT_COORDINATOR: " + message);
    }

    private static final class PreparedSlot {
        private final Resolution resolution;
        private final PreparedMaterial material;
        private final String failureCode;

        private PreparedSlot(
                EffectBatch.Entry entry,
                Resolution resolution,
                PreparedMaterial material,
                String failureCode) {
            Objects.requireNonNull(entry, "entry");
            this.resolution = resolution;
            this.material = material;
            this.failureCode = failureCode;
        }

        private static PreparedSlot ready(
                EffectBatch.Entry entry,
                Resolution resolution,
                PreparedMaterial material) {
            return new PreparedSlot(entry, resolution, material, "");
        }

        private static PreparedSlot rejected(
                EffectBatch.Entry entry,
                Resolution resolution,
                String failureCode) {
            return new PreparedSlot(entry, resolution, null, failureCode);
        }

        private boolean isReady() {
            return material != null;
        }
    }

    public static final class ItemResult {
        private final String effectId;
        private final String resourceKey;
        private final Outcome outcome;
        private final String beforeStateDigest;
        private final EffectObservation observation;
        private final boolean required;

        private ItemResult(
                String effectId,
                String resourceKey,
                boolean required,
                Outcome outcome,
                String beforeStateDigest,
                EffectObservation observation) {
            this.effectId = effectId;
            this.resourceKey = resourceKey;
            this.outcome = Objects.requireNonNull(outcome, "outcome");
            this.beforeStateDigest = beforeStateDigest;
            this.observation = copyObservation(observation);
            this.required = required;
        }

        public String getEffectId() {
            return effectId;
        }

        public String getResourceKey() {
            return resourceKey;
        }

        public Outcome getOutcome() {
            return outcome;
        }

        public String getBeforeStateDigest() {
            return beforeStateDigest;
        }

        public boolean isRequired() {
            return required;
        }

        public EffectObservation getObservation() {
            return copyObservation(observation);
        }
    }

    public static final class ExecutionResult {
        private final String batchId;
        private final String batchDigest;
        private final String dependencyPlanDigest;
        private final BatchStatus status;
        private final List<ItemResult> itemResults;
        private final String resultDigest;

        private ExecutionResult(
                EffectBatch batch,
                EffectDependencyPlanner.Plan plan,
                BatchStatus status,
                List<ItemResult> itemResults) {
            if (itemResults.size() != batch.getEntries().size()) {
                throw violation("result does not contain one observation per effect");
            }
            this.batchId = batch.getBatchId();
            this.batchDigest = batch.getBatchDigest();
            this.dependencyPlanDigest = plan.getPlanDigest();
            this.status = Objects.requireNonNull(status, "status");
            this.itemResults = Collections.unmodifiableList(new ArrayList<>(itemResults));
            List<String> digestParts = new ArrayList<>();
            digestParts.add(batchId);
            digestParts.add(batchDigest);
            digestParts.add(dependencyPlanDigest);
            digestParts.add(status.name());
            digestParts.add(Integer.toString(itemResults.size()));
            for (ItemResult item : itemResults) {
                digestParts.add(item.effectId);
                digestParts.add(item.resourceKey);
                digestParts.add(Boolean.toString(item.required));
                digestParts.add(item.outcome.name());
                digestParts.add(item.observation.observationDigest);
            }
            this.resultDigest = EffectBatch.digest(
                    "effect.coordinator.result.v1", digestParts.toArray(new String[0]));
        }

        public String getBatchId() {
            return batchId;
        }

        public String getBatchDigest() {
            return batchDigest;
        }

        public String getDependencyPlanDigest() {
            return dependencyPlanDigest;
        }

        public BatchStatus getStatus() {
            return status;
        }

        public List<ItemResult> getItemResults() {
            return itemResults;
        }

        public String getResultDigest() {
            return resultDigest;
        }
    }

    private static EffectObservation copyObservation(EffectObservation source) {
        EffectObservation copy = new EffectObservation();
        copy.schemaVersion = source.schemaVersion;
        copy.observationId = source.observationId;
        copy.effectId = source.effectId;
        copy.sessionId = source.sessionId;
        copy.actionId = source.actionId;
        copy.planDigest = source.planDigest;
        copy.contextVersion = source.contextVersion;
        copy.state = source.state;
        copy.source = source.source;
        copy.sourceId = source.sourceId;
        copy.attempt = source.attempt;
        copy.targetValueDigest = source.targetValueDigest;
        copy.reportedValueDigest = source.reportedValueDigest;
        copy.evidenceDigest = source.evidenceDigest;
        copy.observationDigest = source.observationDigest;
        copy.failureCode = source.failureCode;
        copy.occurredAtEpochMs = source.occurredAtEpochMs;
        copy.terminal = source.terminal;
        copy.retryable = source.retryable;
        copy.simulated = source.simulated;
        return copy;
    }
}
