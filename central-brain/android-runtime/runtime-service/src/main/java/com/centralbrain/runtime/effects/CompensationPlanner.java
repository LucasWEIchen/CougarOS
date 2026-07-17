package com.centralbrain.runtime.effects;

import com.centralbrain.runtime.vehicle.capability.CapabilityCatalog;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability;
import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;
import com.centralbrain.sdk.effect.EffectContract;
import com.centralbrain.sdk.effect.EffectIntent;
import com.centralbrain.sdk.effect.EffectObservation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Builds a new compensation plan from terminal source Effects. The planner owns
 * no clock, persistence, scheduler, adapter, governance authority, or hardware.
 */
public final class CompensationPlanner {
    public static final int MAX_COMPENSATION_EFFECTS = EffectBatch.MAX_EFFECTS;

    private static final Pattern CATALOG_AREA =
            Pattern.compile("[a-z][a-z0-9_-]*(?:[.][a-z0-9][a-z0-9_-]*){0,7}");

    private final CapabilityCatalog capabilityCatalog;
    private final Set<String> reversibleTargets;

    public CompensationPlanner(
            CapabilityCatalog capabilityCatalog,
            List<ReversibleTarget> reversibleTargets) {
        this.capabilityCatalog = Objects.requireNonNull(
                capabilityCatalog, "capabilityCatalog");
        Objects.requireNonNull(reversibleTargets, "reversibleTargets");
        if (reversibleTargets.isEmpty()) {
            throw violation("reversible target policy must not be empty");
        }
        Set<String> keys = new HashSet<>();
        for (ReversibleTarget target : reversibleTargets) {
            ReversibleTarget checked = Objects.requireNonNull(target, "reversibleTarget");
            VehicleCapability capability = requireCapability(checked.capabilityId);
            String area = EffectVerifier.requireCatalogArea(capability, checked.area);
            if (!area.equals(checked.area)) {
                throw violation("reversible policy area must use the catalog form");
            }
            if (!capability.getAvailability().isWritable()) {
                throw violation("reversible policy contains a non-writable capability");
            }
            if (!keys.add(targetKey(checked.capabilityId, area))) {
                throw violation("reversible target policy contains a duplicate");
            }
        }
        this.reversibleTargets = Collections.unmodifiableSet(keys);
    }

    public Plan plan(
            EffectBatch sourceBatch,
            List<SourceState> sourceStates,
            long nowEpochMs) {
        Objects.requireNonNull(sourceBatch, "sourceBatch");
        Objects.requireNonNull(sourceStates, "sourceStates");
        if (nowEpochMs <= 0L) {
            throw violation("planning time must be positive");
        }
        if (sourceStates.size() != sourceBatch.getEntries().size()) {
            throw violation("source state must cover every batch Effect exactly once");
        }

        Map<String, SourceState> stateByEffectId = new LinkedHashMap<>();
        for (SourceState sourceState : sourceStates) {
            SourceState checked = Objects.requireNonNull(sourceState, "sourceState");
            if (stateByEffectId.put(checked.effectId, checked) != null) {
                throw violation("source state contains a duplicate Effect");
            }
        }

        Map<String, Step> reversible = new LinkedHashMap<>();
        String compensationSessionId = null;
        String compensationPlanId = null;
        String compensationActionId = null;
        String compensationPlanDigest = null;
        String compensationContextDigest = null;
        long compensationContextVersion = 0L;
        long compensationDeadlineEpochMs = Long.MAX_VALUE;

        for (EffectBatch.Entry entry : sourceBatch.getEntries()) {
            EffectIntent sourceIntent = entry.getIntent();
            SourceState state = stateByEffectId.remove(sourceIntent.effectId);
            if (state == null) {
                throw violation("source state omits a batch Effect");
            }
            validateSourceObservation(sourceIntent, state.observation);
            if (state.observation.state != EffectContract.STATE_VERIFIED) {
                if (!state.observation.terminal) {
                    throw violation("source batch is not terminal and cannot be undone");
                }
                if (state.beforeSnapshot != null || state.compensationIntent != null) {
                    throw violation("non-verified Effect must not carry compensation material");
                }
                continue;
            }
            if (!sourceIntent.reversible) {
                throw violation("verified irreversible Effect makes full undo unavailable");
            }
            if (state.beforeSnapshot == null || state.compensationIntent == null) {
                throw violation("verified reversible Effect requires before state and new intent");
            }

            VehicleCapability capability = requireCapability(sourceIntent.capabilityId);
            String catalogArea = EffectVerifier.requireCatalogArea(
                    capability, sourceIntent.targetArea);
            if (!reversibleTargets.contains(targetKey(sourceIntent.capabilityId, catalogArea))) {
                throw violation("Effect target is not explicitly reversible");
            }
            String expectedDescriptor = expectedCompensationDescriptorDigest(sourceIntent);
            if (!expectedDescriptor.equals(sourceIntent.compensationDigest)) {
                throw violation("source compensation descriptor digest is invalid");
            }
            validateBeforeSnapshot(
                    sourceIntent,
                    state.observation,
                    capability,
                    catalogArea,
                    state.beforeSnapshot,
                    state.preparedBeforeStateDigest);
            validateCompensationIntent(
                    sourceBatch,
                    sourceIntent,
                    state.beforeSnapshot,
                    state.compensationIntent,
                    nowEpochMs);

            EffectIntent compensationIntent = EffectBatch.copyIntent(state.compensationIntent);
            if (compensationSessionId == null) {
                compensationSessionId = compensationIntent.sessionId;
                compensationPlanId = compensationIntent.planId;
                compensationActionId = compensationIntent.actionId;
                compensationPlanDigest = compensationIntent.planDigest;
                compensationContextDigest = compensationIntent.contextDigest;
                compensationContextVersion = compensationIntent.contextVersion;
            } else if (!compensationSessionId.equals(compensationIntent.sessionId)
                    || !compensationPlanId.equals(compensationIntent.planId)
                    || !compensationActionId.equals(compensationIntent.actionId)
                    || !compensationPlanDigest.equals(compensationIntent.planDigest)
                    || !compensationContextDigest.equals(compensationIntent.contextDigest)
                    || compensationContextVersion != compensationIntent.contextVersion) {
                throw violation("compensation Effects do not share one new governed task");
            }
            compensationDeadlineEpochMs = Math.min(
                    compensationDeadlineEpochMs, compensationIntent.deadlineEpochMs);
            reversible.put(
                    sourceIntent.effectId,
                    new Step(
                            entry.getResourceKey(),
                            sourceIntent,
                            state.observation,
                            state.beforeSnapshot,
                            compensationIntent,
                            expectedDescriptor));
        }
        if (!stateByEffectId.isEmpty()) {
            throw violation("source state references an Effect outside the batch");
        }
        if (reversible.isEmpty()) {
            throw violation("source batch has no verified reversible Effect");
        }

        for (EffectBatch.Entry entry : sourceBatch.getEntries()) {
            if (!reversible.containsKey(entry.getEffectId())) {
                continue;
            }
            for (String dependencyId : entry.getDependencyEffectIds()) {
                if (!reversible.containsKey(dependencyId)) {
                    throw violation("verified Effect has a dependency that is not reversible and verified");
                }
            }
        }

        EffectDependencyPlanner.Plan forwardPlan =
                new EffectDependencyPlanner().plan(sourceBatch);
        List<Wave> waves = new ArrayList<>();
        List<EffectDependencyPlanner.Wave> forwardWaves = forwardPlan.getWaves();
        for (int index = forwardWaves.size() - 1; index >= 0; index--) {
            EffectDependencyPlanner.Wave forwardWave = forwardWaves.get(index);
            List<Step> steps = new ArrayList<>();
            for (String effectId : forwardWave.getEffectIds()) {
                Step step = reversible.get(effectId);
                if (step != null) {
                    steps.add(step);
                }
            }
            if (!steps.isEmpty()) {
                waves.add(new Wave(waves.size(), forwardWave.getWaveIndex(), steps));
            }
        }
        if (waves.isEmpty()) {
            throw violation("compensation plan contains no executable wave");
        }
        return new Plan(
                sourceBatch,
                forwardPlan.getPlanDigest(),
                compensationSessionId,
                compensationPlanId,
                compensationActionId,
                compensationPlanDigest,
                compensationContextDigest,
                compensationContextVersion,
                compensationDeadlineEpochMs,
                waves);
    }

    public static String expectedCompensationDescriptorDigest(EffectIntent sourceIntent) {
        Objects.requireNonNull(sourceIntent, "sourceIntent");
        if (!sourceIntent.reversible) {
            throw violation("irreversible Effect has no compensation descriptor");
        }
        return EffectBatch.digest(
                "effect.compensation.descriptor.v1",
                sourceIntent.capabilityId == null ? "" : sourceIntent.capabilityId,
                sourceIntent.targetArea == null ? "" : sourceIntent.targetArea,
                Integer.toString(sourceIntent.valueKind),
                sourceIntent.unit == null ? "" : sourceIntent.unit,
                Integer.toString(sourceIntent.riskClass),
                Integer.toString(sourceIntent.verificationPolicy));
    }

    public static String expectedCompensationIdempotencyKey(
            String compensationPlanId,
            String sourceEffectId) {
        return "compensate:"
                + EffectBatch.canonicalUuid(compensationPlanId, "compensationPlanId")
                + ":"
                + EffectBatch.canonicalUuid(sourceEffectId, "sourceEffectId");
    }

    private void validateBeforeSnapshot(
            EffectIntent sourceIntent,
            EffectObservation verified,
            VehicleCapability capability,
            String catalogArea,
            BeforeSnapshot snapshot,
            String preparedBeforeStateDigest) {
        if (!snapshot.snapshotDigest.equals(
                EffectBatch.requireDigest(
                        preparedBeforeStateDigest, "preparedBeforeStateDigest"))) {
            throw violation("before snapshot does not match prepared Effect material");
        }
        SignalValue value = snapshot.value;
        VehicleSignalPath expectedPath = capability.getReportedSignalPath().orElseThrow(
                () -> violation("reversible target has no cataloged absolute readback"));
        if (value.getPath() != expectedPath
                || !value.getArea().equals(catalogArea)
                || !value.getUnit().equals(sourceIntent.unit)) {
            throw violation("before snapshot is not bound to the source capability target");
        }
        validateCapabilityValue(capability, value);
        if (!snapshot.contextDigest.equals(sourceIntent.contextDigest)
                || snapshot.contextVersion != sourceIntent.contextVersion) {
            throw violation("before snapshot Context binding differs from the source Effect");
        }
        if (snapshot.capturedAtEpochMs < sourceIntent.createdAtEpochMs
                || snapshot.capturedAtEpochMs > verified.occurredAtEpochMs) {
            throw violation("before snapshot is outside the source execution window");
        }
    }

    private static void validateCompensationIntent(
            EffectBatch sourceBatch,
            EffectIntent source,
            BeforeSnapshot before,
            EffectIntent compensation,
            long nowEpochMs) {
        EffectContract.validateIntent(compensation, nowEpochMs);
        if (compensation.effectId.equals(source.effectId)
                || compensation.sessionId.equals(sourceBatch.getSessionId())
                || compensation.planId.equals(sourceBatch.getPlanId())
                || compensation.actionId.equals(sourceBatch.getActionId())) {
            throw violation("compensation must be a new governed operation");
        }
        if (!compensation.capabilityId.equals(source.capabilityId)
                || !compensation.targetArea.equals(source.targetArea)
                || compensation.riskClass != source.riskClass
                || !compensation.unit.equals(source.unit)
                || compensation.required != source.required) {
            throw violation("compensation target changes source capability metadata");
        }
        if (compensation.contextVersion < source.contextVersion) {
            throw violation("compensation Context version regressed");
        }
        if (compensation.reversible || !compensation.compensationDigest.isEmpty()) {
            throw violation("compensation Effect must not recursively advertise Undo");
        }
        if (compensation.verificationPolicy == EffectContract.VERIFY_STATE_TRANSITION
                || compensation.verificationPolicy == EffectContract.VERIFY_COMPOSITE) {
            throw violation("absolute compensation target requires directly verifiable policy");
        }
        if (!sameScalar(compensation, before.value)) {
            throw violation("compensation target is not the absolute before value");
        }
        String expectedTargetDigest = EffectVerifier.expectedTargetDigest(
                compensation, List.of());
        if (!expectedTargetDigest.equals(compensation.targetValueDigest)) {
            throw violation("compensation target digest is invalid");
        }
        String expectedIdempotency = expectedCompensationIdempotencyKey(
                compensation.planId, source.effectId);
        if (!expectedIdempotency.equals(compensation.idempotencyKey)) {
            throw violation("compensation idempotency key is not bound to the source Effect");
        }
    }

    private static void validateSourceObservation(
            EffectIntent intent,
            EffectObservation observation) {
        EffectContract.validateObservation(observation);
        if (!intent.effectId.equals(observation.effectId)
                || !intent.sessionId.equals(observation.sessionId)
                || !intent.actionId.equals(observation.actionId)
                || !intent.planDigest.equals(observation.planDigest)
                || intent.contextVersion != observation.contextVersion
                || !intent.targetValueDigest.equals(observation.targetValueDigest)) {
            throw violation("source observation binding differs from its Effect");
        }
    }

    private static void validateCapabilityValue(
            VehicleCapability capability,
            SignalValue value) {
        if (!value.hasValue() || value.getQuality() != SignalQuality.VALID) {
            throw violation("before snapshot must contain a VALID typed value");
        }
        VehicleCapability.TargetRange range = capability.getTargetRange();
        switch (value.getScalarType()) {
            case BOOLEAN:
                range.validateBoolean(value.getBooleanValue());
                break;
            case INTEGER:
                range.validateInteger(value.getIntegerValue());
                break;
            case DECIMAL:
                range.validateDecimal(value.getDecimalValue());
                break;
            case TEXT:
                range.validateText(value.getTextValue());
                break;
            default:
                throw violation("before snapshot scalar type is unsupported");
        }
    }

    private static boolean sameScalar(EffectIntent intent, SignalValue value) {
        if (intent.valueKind == EffectContract.VALUE_BOOLEAN
                && value.getScalarType() == SignalValue.ScalarType.BOOLEAN) {
            return intent.booleanValue == value.getBooleanValue();
        }
        if (intent.valueKind == EffectContract.VALUE_INTEGER
                && value.getScalarType() == SignalValue.ScalarType.INTEGER) {
            return intent.integerValue == value.getIntegerValue();
        }
        if (intent.valueKind == EffectContract.VALUE_DECIMAL
                && value.getScalarType() == SignalValue.ScalarType.DECIMAL) {
            return Double.compare(intent.decimalValue, value.getDecimalValue()) == 0;
        }
        if (intent.valueKind == EffectContract.VALUE_TEXT
                && value.getScalarType() == SignalValue.ScalarType.TEXT) {
            return intent.textValue.equals(value.getTextValue());
        }
        return false;
    }

    private VehicleCapability requireCapability(String capabilityId) {
        for (VehicleCapability capability : capabilityCatalog.all()) {
            if (capability.getId().getCanonicalId().equals(capabilityId)) {
                return capability;
            }
        }
        throw violation("capability is not cataloged");
    }

    private static String targetKey(String capabilityId, String catalogArea) {
        return Objects.requireNonNull(capabilityId, "capabilityId")
                + "\u0000"
                + Objects.requireNonNull(catalogArea, "catalogArea");
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

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_COMPENSATION_PLANNER: " + message);
    }

    public static final class ReversibleTarget {
        private final String capabilityId;
        private final String area;

        public ReversibleTarget(String capabilityId, String area) {
            this.capabilityId = EffectBatch.requireQualifiedId(
                    capabilityId,
                    EffectContract.MAX_CAPABILITY_ID_CHARS,
                    "capabilityId");
            if (area == null
                    || area.length() > EffectContract.MAX_TARGET_AREA_CHARS
                    || !CATALOG_AREA.matcher(area).matches()) {
                throw violation("area is not canonical");
            }
            this.area = area;
        }

        public String getCapabilityId() {
            return capabilityId;
        }

        public String getArea() {
            return area;
        }
    }

    public static final class BeforeSnapshot {
        private final SignalValue value;
        private final String contextDigest;
        private final long contextVersion;
        private final long capturedAtEpochMs;
        private final long capturedAtElapsedMs;
        private final boolean productionTrusted;
        private final String snapshotDigest;

        public BeforeSnapshot(
                SignalValue value,
                String contextDigest,
                long contextVersion,
                long capturedAtEpochMs,
                long capturedAtElapsedMs,
                boolean productionTrusted) {
            this.value = Objects.requireNonNull(value, "value");
            if (!value.hasValue() || value.getQuality() != SignalQuality.VALID) {
                throw violation("before snapshot requires a VALID typed value");
            }
            value.validateFreshness(capturedAtElapsedMs);
            this.contextDigest = EffectBatch.requireDigest(
                    contextDigest, "contextDigest");
            if (contextVersion < 1L) {
                throw violation("before snapshot Context version must be positive");
            }
            if (capturedAtEpochMs <= 0L || capturedAtElapsedMs < 0L) {
                throw violation("before snapshot time is invalid");
            }
            if (productionTrusted
                    && value.getSource()
                            == com.centralbrain.runtime.vehicle.schema.SignalSource.SIMULATED) {
                throw violation("simulation before state cannot be production-trusted");
            }
            this.contextVersion = contextVersion;
            this.capturedAtEpochMs = capturedAtEpochMs;
            this.capturedAtElapsedMs = capturedAtElapsedMs;
            this.productionTrusted = productionTrusted;
            this.snapshotDigest = EffectBatch.digest(
                    "effect.compensation.before.v1",
                    value.getPath().getCanonicalPath(),
                    value.getArea(),
                    EffectVerifier.TypedValue.fromSignal(value).getDigest(),
                    value.getQuality().name(),
                    value.getSource().name(),
                    Long.toString(value.getRevision()),
                    Long.toString(value.getTimestamp().getSourceEpochMs()),
                    Long.toString(value.getTimestamp().getReceivedElapsedRealtimeMs()),
                    this.contextDigest,
                    Long.toString(this.contextVersion),
                    Long.toString(this.capturedAtEpochMs),
                    Long.toString(this.capturedAtElapsedMs),
                    Boolean.toString(this.productionTrusted));
        }

        public SignalValue getValue() {
            return value;
        }

        public String getContextDigest() {
            return contextDigest;
        }

        public long getContextVersion() {
            return contextVersion;
        }

        public long getCapturedAtEpochMs() {
            return capturedAtEpochMs;
        }

        public boolean isProductionTrusted() {
            return productionTrusted;
        }

        public String getSnapshotDigest() {
            return snapshotDigest;
        }
    }

    public static final class SourceState {
        private final String effectId;
        private final EffectObservation observation;
        private final BeforeSnapshot beforeSnapshot;
        private final String preparedBeforeStateDigest;
        private final EffectIntent compensationIntent;

        private SourceState(
                String effectId,
                EffectObservation observation,
                BeforeSnapshot beforeSnapshot,
                String preparedBeforeStateDigest,
                EffectIntent compensationIntent) {
            this.effectId = EffectBatch.canonicalUuid(effectId, "effectId");
            this.observation = copyObservation(
                    Objects.requireNonNull(observation, "observation"));
            this.beforeSnapshot = beforeSnapshot;
            this.preparedBeforeStateDigest = preparedBeforeStateDigest;
            this.compensationIntent = compensationIntent == null
                    ? null : EffectBatch.copyIntent(compensationIntent);
        }

        public static SourceState notVerified(
                String effectId,
                EffectObservation observation) {
            return new SourceState(effectId, observation, null, null, null);
        }

        public static SourceState verified(
                String effectId,
                EffectObservation observation,
                BeforeSnapshot beforeSnapshot,
                String preparedBeforeStateDigest,
                EffectIntent compensationIntent) {
            return new SourceState(
                    effectId,
                    observation,
                    Objects.requireNonNull(beforeSnapshot, "beforeSnapshot"),
                    EffectBatch.requireDigest(
                            preparedBeforeStateDigest, "preparedBeforeStateDigest"),
                    Objects.requireNonNull(compensationIntent, "compensationIntent"));
        }
    }

    public static final class Step {
        private final String resourceKey;
        private final EffectIntent sourceIntent;
        private final EffectObservation verifiedObservation;
        private final BeforeSnapshot beforeSnapshot;
        private final EffectIntent compensationIntent;
        private final String descriptorDigest;
        private final String stepDigest;

        private Step(
                String resourceKey,
                EffectIntent sourceIntent,
                EffectObservation verifiedObservation,
                BeforeSnapshot beforeSnapshot,
                EffectIntent compensationIntent,
                String descriptorDigest) {
            this.resourceKey = resourceKey;
            this.sourceIntent = EffectBatch.copyIntent(sourceIntent);
            this.verifiedObservation = copyObservation(verifiedObservation);
            this.beforeSnapshot = beforeSnapshot;
            this.compensationIntent = EffectBatch.copyIntent(compensationIntent);
            this.descriptorDigest = descriptorDigest;
            this.stepDigest = EffectBatch.digest(
                    "effect.compensation.step.v1",
                    resourceKey,
                    sourceIntent.effectId,
                    verifiedObservation.observationId,
                    verifiedObservation.observationDigest,
                    beforeSnapshot.snapshotDigest,
                    descriptorDigest,
                    compensationIntent.effectId,
                    compensationIntent.sessionId,
                    compensationIntent.planId,
                    compensationIntent.nodeId,
                    compensationIntent.actionId,
                    compensationIntent.targetValueDigest,
                    compensationIntent.idempotencyKey,
                    compensationIntent.planDigest,
                    compensationIntent.contextDigest,
                    Long.toString(compensationIntent.contextVersion),
                    Long.toString(compensationIntent.deadlineEpochMs));
        }

        public String getResourceKey() {
            return resourceKey;
        }

        public EffectIntent getSourceIntent() {
            return EffectBatch.copyIntent(sourceIntent);
        }

        public EffectObservation getVerifiedObservation() {
            return copyObservation(verifiedObservation);
        }

        public BeforeSnapshot getBeforeSnapshot() {
            return beforeSnapshot;
        }

        public EffectIntent getCompensationIntent() {
            return EffectBatch.copyIntent(compensationIntent);
        }

        public String getDescriptorDigest() {
            return descriptorDigest;
        }

        public String getStepDigest() {
            return stepDigest;
        }
    }

    public static final class Wave {
        private final int waveIndex;
        private final int sourceWaveIndex;
        private final List<Step> steps;

        private Wave(int waveIndex, int sourceWaveIndex, List<Step> steps) {
            this.waveIndex = waveIndex;
            this.sourceWaveIndex = sourceWaveIndex;
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
            Set<String> resources = new HashSet<>();
            for (Step step : this.steps) {
                if (!resources.add(step.resourceKey)) {
                    throw violation("compensation wave contains a resource conflict");
                }
            }
        }

        public int getWaveIndex() {
            return waveIndex;
        }

        public int getSourceWaveIndex() {
            return sourceWaveIndex;
        }

        public List<Step> getSteps() {
            return steps;
        }
    }

    public static final class Plan {
        private final String sourceBatchId;
        private final String sourceBatchDigest;
        private final String sourceSessionId;
        private final String sourcePlanId;
        private final String sourceActionId;
        private final String sourceDependencyPlanDigest;
        private final String compensationSessionId;
        private final String compensationPlanId;
        private final String compensationActionId;
        private final String compensationPlanSourceDigest;
        private final String compensationContextDigest;
        private final long compensationContextVersion;
        private final long compensationDeadlineEpochMs;
        private final List<Wave> waves;
        private final List<Step> orderedSteps;
        private final String planDigest;

        private Plan(
                EffectBatch sourceBatch,
                String sourceDependencyPlanDigest,
                String compensationSessionId,
                String compensationPlanId,
                String compensationActionId,
                String compensationPlanSourceDigest,
                String compensationContextDigest,
                long compensationContextVersion,
                long compensationDeadlineEpochMs,
                List<Wave> waves) {
            this.sourceBatchId = sourceBatch.getBatchId();
            this.sourceBatchDigest = sourceBatch.getBatchDigest();
            this.sourceSessionId = sourceBatch.getSessionId();
            this.sourcePlanId = sourceBatch.getPlanId();
            this.sourceActionId = sourceBatch.getActionId();
            this.sourceDependencyPlanDigest = sourceDependencyPlanDigest;
            this.compensationSessionId = compensationSessionId;
            this.compensationPlanId = compensationPlanId;
            this.compensationActionId = compensationActionId;
            this.compensationPlanSourceDigest = compensationPlanSourceDigest;
            this.compensationContextDigest = compensationContextDigest;
            this.compensationContextVersion = compensationContextVersion;
            this.compensationDeadlineEpochMs = compensationDeadlineEpochMs;
            this.waves = Collections.unmodifiableList(new ArrayList<>(waves));
            List<Step> flat = new ArrayList<>();
            List<String> parts = new ArrayList<>();
            parts.add(sourceBatchId);
            parts.add(sourceBatchDigest);
            parts.add(sourceDependencyPlanDigest);
            parts.add(compensationSessionId);
            parts.add(compensationPlanId);
            parts.add(compensationActionId);
            parts.add(compensationPlanSourceDigest);
            parts.add(compensationContextDigest);
            parts.add(Long.toString(compensationContextVersion));
            parts.add(Long.toString(compensationDeadlineEpochMs));
            parts.add(Integer.toString(waves.size()));
            for (Wave wave : waves) {
                parts.add(Integer.toString(wave.waveIndex));
                parts.add(Integer.toString(wave.sourceWaveIndex));
                parts.add(Integer.toString(wave.steps.size()));
                for (Step step : wave.steps) {
                    flat.add(step);
                    parts.add(step.stepDigest);
                }
            }
            this.orderedSteps = Collections.unmodifiableList(flat);
            this.planDigest = EffectBatch.digest(
                    "effect.compensation.plan.v1", parts.toArray(new String[0]));
        }

        public String getSourceBatchId() {
            return sourceBatchId;
        }

        public String getSourceBatchDigest() {
            return sourceBatchDigest;
        }

        public String getSourceSessionId() {
            return sourceSessionId;
        }

        public String getSourcePlanId() {
            return sourcePlanId;
        }

        public String getSourceActionId() {
            return sourceActionId;
        }

        public String getCompensationSessionId() {
            return compensationSessionId;
        }

        public String getCompensationPlanId() {
            return compensationPlanId;
        }

        public String getCompensationActionId() {
            return compensationActionId;
        }

        public String getCompensationContextDigest() {
            return compensationContextDigest;
        }

        public long getCompensationContextVersion() {
            return compensationContextVersion;
        }

        public long getCompensationDeadlineEpochMs() {
            return compensationDeadlineEpochMs;
        }

        public List<Wave> getWaves() {
            return waves;
        }

        public List<Step> getOrderedSteps() {
            return orderedSteps;
        }

        public String getPlanDigest() {
            return planDigest;
        }
    }
}
