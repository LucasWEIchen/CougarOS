package com.centralbrain.runtime.effects;

import com.centralbrain.runtime.effects.AdapterRegistry.Profile;
import com.centralbrain.runtime.effects.AdapterRegistry.Resolution;
import com.centralbrain.runtime.effects.EffectVerifier.TypedValue;
import com.centralbrain.runtime.effects.EffectVerifier.VerificationEvidence;
import com.centralbrain.runtime.effects.EffectVerifier.VerificationField;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability;
import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;
import com.centralbrain.runtime.vehicle.twin.DigitalTwinSnapshot;
import com.centralbrain.runtime.vehicle.twin.ReportedStateRecord;
import com.centralbrain.sdk.effect.EffectContract;
import com.centralbrain.sdk.effect.EffectIntent;
import com.centralbrain.sdk.effect.EffectObservation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Queries delivery status and reconciles it with one immutable Digital Twin
 * snapshot. It never invokes apply and owns no timer, thread, or persistence.
 */
public final class DigitalTwinEffectReconciler {
    public static final int MAX_RECONCILE_SEQUENCE = 64;
    public static final long INITIAL_RECONCILE_DELAY_MS = 250L;
    public static final long MAX_RECONCILE_DELAY_MS = 30_000L;

    public enum Decision {
        VERIFIED,
        APPLIED_PENDING_VERIFICATION,
        RECONCILE_SCHEDULED,
        CONFIRMED_NOT_APPLIED,
        FAILED_TERMINAL,
        ALREADY_VERIFIED,
        PRODUCTION_READBACK_UNAVAILABLE
    }

    private final AdapterRegistry adapterRegistry;
    private final EffectVerifier verifier;

    public DigitalTwinEffectReconciler(
            AdapterRegistry adapterRegistry,
            EffectVerifier verifier) {
        this.adapterRegistry = Objects.requireNonNull(adapterRegistry, "adapterRegistry");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
    }

    public Result reconcile(
            EffectIntent sourceIntent,
            EffectObservation sourcePrevious,
            Profile profile,
            DigitalTwinSnapshot snapshot,
            long nowEpochMs,
            int reconcileSequence) {
        EffectIntent intent = EffectBatch.copyIntent(sourceIntent);
        EffectObservation previous = copyObservation(sourcePrevious);
        Objects.requireNonNull(profile, "profile");
        EffectContract.validateIntent(intent, intent.createdAtEpochMs);
        EffectContract.validateObservation(previous);
        requireBinding(intent, previous);
        if (nowEpochMs <= 0L || nowEpochMs < previous.occurredAtEpochMs) {
            throw violation("reconcile time is invalid or moves backwards");
        }
        if (reconcileSequence < 1 || reconcileSequence > MAX_RECONCILE_SEQUENCE) {
            throw violation("reconcile sequence is outside 1..64");
        }
        if (previous.state == EffectContract.STATE_VERIFIED) {
            return new Result(
                    Decision.ALREADY_VERIFIED,
                    "",
                    false,
                    0L,
                    List.of(),
                    previous);
        }
        requireReconcileState(previous.state);
        if (profile == Profile.PRODUCTION) {
            return new Result(
                    Decision.PRODUCTION_READBACK_UNAVAILABLE,
                    "PRODUCTION_READBACK_UNAVAILABLE",
                    false,
                    0L,
                    List.of(),
                    previous);
        }

        Resolution resolution;
        try {
            resolution = adapterRegistry.resolve(
                    intent.capabilityId, intent.targetArea, profile);
        } catch (AdapterRegistry.AdapterUnavailableException unavailable) {
            EffectVerifier.Result pending = verifier.verify(
                    intent,
                    previous,
                    VerificationEvidence.unavailable(
                            previous.source,
                            previous.sourceId,
                            false,
                            nowEpochMs,
                            EffectBatch.digest(
                                    "effect.reconciler.adapter.unavailable.v1",
                                    intent.effectId,
                                    previous.observationDigest,
                                    Integer.toString(reconcileSequence))),
                    profile,
                    nowEpochMs);
            return scheduledOrTerminal(
                    intent, pending, nowEpochMs, reconcileSequence, false);
        }

        String token = EffectBatch.digest(
                "effect.idempotency.token.v1", intent.idempotencyKey);
        EffectAdapter.StatusResult status;
        try {
            status = EffectAdapterContract.requireStatusResultMatches(
                    token, resolution.adapter().queryStatus(token));
        } catch (EffectAdapter.AdapterUnavailableException unavailable) {
            EffectVerifier.Result pending = verifier.verify(
                    intent,
                    previous,
                    VerificationEvidence.unavailable(
                            evidenceSource(resolution),
                            resolution.getRegistrationId(),
                            false,
                            nowEpochMs,
                            EffectBatch.digest(
                                    "effect.reconciler.status.unavailable.v1",
                                    intent.effectId,
                                    previous.observationDigest,
                                    Integer.toString(reconcileSequence))),
                    profile,
                    nowEpochMs);
            return scheduledOrTerminal(
                    intent, pending, nowEpochMs, reconcileSequence, true);
        } catch (EffectAdapterContract.UnsafeAdapterException unsafe) {
            EffectVerifier.Result failed = verifier.fail(
                    intent,
                    previous,
                    "ADAPTER_STATUS_CONTRACT_INVALID",
                    EffectBatch.digest(
                            "effect.reconciler.status.contract.invalid.v1",
                            intent.effectId,
                            previous.observationDigest,
                            Integer.toString(reconcileSequence)),
                    nowEpochMs);
            return fromVerifier(
                    Decision.FAILED_TERMINAL, failed, true, 0L);
        } catch (RuntimeException exception) {
            EffectVerifier.Result pending = verifier.verify(
                    intent,
                    previous,
                    VerificationEvidence.unavailable(
                            evidenceSource(resolution),
                            resolution.getRegistrationId(),
                            false,
                            nowEpochMs,
                            EffectBatch.digest(
                                    "effect.reconciler.status.exception.v1",
                                    intent.effectId,
                                    previous.observationDigest,
                                    Integer.toString(reconcileSequence))),
                    profile,
                    nowEpochMs);
            return scheduledOrTerminal(
                    intent, pending, nowEpochMs, reconcileSequence, true);
        }

        switch (status.getState()) {
            case NOT_APPLIED:
                if (previous.state == EffectContract.STATE_APPLIED) {
                    EffectVerifier.Result regression = verifier.fail(
                            intent,
                            previous,
                            "ADAPTER_STATUS_REGRESSION",
                            status.getEvidenceDigest(),
                            nowEpochMs);
                    return fromVerifier(
                            Decision.FAILED_TERMINAL, regression, true, 0L);
                }
                return new Result(
                        Decision.CONFIRMED_NOT_APPLIED,
                        "CONFIRMED_NOT_APPLIED",
                        true,
                        0L,
                        List.of(),
                        previous);
            case REJECTED:
                EffectVerifier.Result rejected = verifier.fail(
                        intent,
                        previous,
                        "ADAPTER_STATUS_REJECTED",
                        status.getEvidenceDigest(),
                        nowEpochMs);
                return fromVerifier(
                        Decision.FAILED_TERMINAL, rejected, true, 0L);
            case UNKNOWN:
                EffectVerifier.Result unknown = verifier.verify(
                        intent,
                        previous,
                        VerificationEvidence.unavailable(
                                evidenceSource(resolution),
                                resolution.getRegistrationId(),
                                false,
                                nowEpochMs,
                                status.getEvidenceDigest()),
                        profile,
                        nowEpochMs);
                return scheduledOrTerminal(
                        intent, unknown, nowEpochMs, reconcileSequence, true);
            case APPLIED:
                return reconcileApplied(
                        intent,
                        previous,
                        resolution,
                        status,
                        snapshot,
                        nowEpochMs,
                        reconcileSequence);
            default:
                throw violation("adapter delivery state is unsupported");
        }
    }

    private Result reconcileApplied(
            EffectIntent intent,
            EffectObservation previous,
            Resolution resolution,
            EffectAdapter.StatusResult status,
            DigitalTwinSnapshot snapshot,
            long nowEpochMs,
            int reconcileSequence) {
        VehicleCapability capability = verifier.requireCapability(intent.capabilityId);
        Optional<VehicleSignalPath> readbackPath = capability.getReportedSignalPath();
        VerificationEvidence evidence;
        if (intent.verificationPolicy == EffectContract.VERIFY_CALLBACK_ONLY) {
            evidence = VerificationEvidence.callbackApplied(
                    evidenceSource(resolution),
                    resolution.getRegistrationId(),
                    false,
                    nowEpochMs,
                    status.getEvidenceDigest());
        } else if (!readbackPath.isPresent()
                || intent.verificationPolicy == EffectContract.VERIFY_STATE_TRANSITION
                || intent.verificationPolicy == EffectContract.VERIFY_COMPOSITE) {
            evidence = VerificationEvidence.unavailable(
                    evidenceSource(resolution),
                    resolution.getRegistrationId(),
                    false,
                    nowEpochMs,
                    EffectBatch.digest(
                            "effect.reconciler.policy.input.unavailable.v1",
                            status.getEvidenceDigest(),
                            Integer.toString(intent.verificationPolicy)));
        } else {
            String area = EffectVerifier.requireCatalogArea(capability, intent.targetArea);
            ReportedStateRecord reported = usableReported(snapshot, readbackPath.get(), area);
            if (reported == null) {
                long snapshotRevision = snapshot == null ? -1L : snapshot.getRevision();
                evidence = VerificationEvidence.unavailable(
                        evidenceSource(resolution),
                        resolution.getRegistrationId(),
                        false,
                        nowEpochMs,
                        EffectBatch.digest(
                                "effect.reconciler.readback.unavailable.v1",
                                status.getEvidenceDigest(),
                                Long.toString(snapshotRevision),
                                readbackPath.get().getCanonicalPath(),
                                area));
            } else {
                VerificationField field = VerificationField.primary(
                        readbackPath.get(),
                        area,
                        intent,
                        reported.getValue());
                evidence = VerificationEvidence.readback(
                        evidenceSource(resolution),
                        resolution.getRegistrationId(),
                        false,
                        nowEpochMs,
                        EffectBatch.digest(
                                "effect.reconciler.readback.v1",
                                status.getEvidenceDigest(),
                                Long.toString(snapshot.getRevision()),
                                Long.toString(reported.getStoreRevision()),
                                reported.getValue().getTimestamp().getSourceEpochMs() == 0L
                                        ? "0"
                                        : Long.toString(reported.getValue()
                                                .getTimestamp().getSourceEpochMs()),
                                reported.getEffectiveQuality().name(),
                                reported.getValue().getSource().name(),
                                TypedValue.fromSignal(reported.getValue()).getDigest()),
                        List.of(field));
            }
        }

        EffectVerifier.Result verified = verifier.verify(
                intent, previous, evidence, Profile.DEBUG_SIMULATION, nowEpochMs);
        if (verified.getDecision() == EffectVerifier.Decision.VERIFIED) {
            return fromVerifier(Decision.VERIFIED, verified, true, 0L);
        }
        if (verified.getDecision() == EffectVerifier.Decision.FAILED_TERMINAL) {
            return fromVerifier(Decision.FAILED_TERMINAL, verified, true, 0L);
        }
        return scheduledOrTerminal(
                intent, verified, nowEpochMs, reconcileSequence, true);
    }

    private Result scheduledOrTerminal(
            EffectIntent intent,
            EffectVerifier.Result verifierResult,
            long nowEpochMs,
            int reconcileSequence,
            boolean adapterQueried) {
        if (verifierResult.getDecision() == EffectVerifier.Decision.FAILED_TERMINAL) {
            return fromVerifier(
                    Decision.FAILED_TERMINAL, verifierResult, adapterQueried, 0L);
        }
        if (!verifierResult.requiresReconciliation()) {
            throw violation("non-terminal verifier result is not reconcilable");
        }
        if (reconcileSequence == MAX_RECONCILE_SEQUENCE) {
            EffectVerifier.Result exhausted = verifier.fail(
                    intent,
                    verifierResult.getLatestObservation(),
                    "RECONCILE_ATTEMPTS_EXHAUSTED",
                    EffectBatch.digest(
                            "effect.reconciler.attempts.exhausted.v1",
                            verifierResult.getLatestObservation().observationDigest,
                            Integer.toString(reconcileSequence)),
                    nowEpochMs);
            List<EffectObservation> combined = new ArrayList<>(
                    verifierResult.getTransitions());
            combined.addAll(exhausted.getTransitions());
            return new Result(
                    Decision.FAILED_TERMINAL,
                    exhausted.getReasonCode(),
                    adapterQueried,
                    0L,
                    combined,
                    exhausted.getLatestObservation());
        }
        long next = nextReconcileAt(nowEpochMs, intent.deadlineEpochMs, reconcileSequence);
        Decision decision = verifierResult.getLatestObservation().state
                        == EffectContract.STATE_APPLIED
                ? Decision.APPLIED_PENDING_VERIFICATION
                : Decision.RECONCILE_SCHEDULED;
        return fromVerifier(decision, verifierResult, adapterQueried, next);
    }

    private static Result fromVerifier(
            Decision decision,
            EffectVerifier.Result verifierResult,
            boolean adapterQueried,
            long nextReconcileAtEpochMs) {
        return new Result(
                decision,
                verifierResult.getReasonCode(),
                adapterQueried,
                nextReconcileAtEpochMs,
                verifierResult.getTransitions(),
                verifierResult.getLatestObservation());
    }

    private static ReportedStateRecord usableReported(
            DigitalTwinSnapshot snapshot,
            VehicleSignalPath path,
            String area) {
        if (snapshot == null) {
            return null;
        }
        Optional<ReportedStateRecord> candidate = snapshot.reported(path, area);
        if (!candidate.isPresent()
                || candidate.get().getEffectiveQuality() != SignalQuality.VALID
                || !candidate.get().getValue().hasValue()) {
            return null;
        }
        return candidate.get();
    }

    private static int evidenceSource(Resolution resolution) {
        return resolution.isSimulation()
                ? EffectContract.SOURCE_SIMULATION
                : EffectContract.SOURCE_ADAPTER;
    }

    private static long nextReconcileAt(
            long nowEpochMs,
            long deadlineEpochMs,
            int reconcileSequence) {
        long delay = INITIAL_RECONCILE_DELAY_MS;
        for (int current = 1; current < reconcileSequence; current++) {
            delay = Math.min(MAX_RECONCILE_DELAY_MS, delay * 2L);
        }
        long candidate = nowEpochMs > Long.MAX_VALUE - delay
                ? Long.MAX_VALUE : nowEpochMs + delay;
        return Math.min(candidate, deadlineEpochMs);
    }

    private static void requireBinding(EffectIntent intent, EffectObservation observation) {
        if (!intent.effectId.equals(observation.effectId)
                || !intent.sessionId.equals(observation.sessionId)
                || !intent.actionId.equals(observation.actionId)
                || !intent.planDigest.equals(observation.planDigest)
                || intent.contextVersion != observation.contextVersion
                || !intent.targetValueDigest.equals(observation.targetValueDigest)) {
            throw violation("EffectIntent and observation binding disagree");
        }
    }

    private static void requireReconcileState(int state) {
        if (state != EffectContract.STATE_DELIVERED
                && state != EffectContract.STATE_APPLIED
                && state != EffectContract.STATE_UNKNOWN) {
            throw violation("previous observation is not reconcilable");
        }
    }

    private static EffectObservation copyObservation(EffectObservation source) {
        Objects.requireNonNull(source, "observation");
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
        return new IllegalArgumentException("CB_EFFECT_RECONCILER: " + message);
    }

    public static final class Result {
        private final Decision decision;
        private final String reasonCode;
        private final boolean adapterQueried;
        private final long nextReconcileAtEpochMs;
        private final List<EffectObservation> transitions;
        private final EffectObservation latest;

        private Result(
                Decision decision,
                String reasonCode,
                boolean adapterQueried,
                long nextReconcileAtEpochMs,
                List<EffectObservation> transitions,
                EffectObservation latest) {
            this.decision = Objects.requireNonNull(decision, "decision");
            this.reasonCode = reasonCode == null ? "" : reasonCode;
            this.adapterQueried = adapterQueried;
            if (nextReconcileAtEpochMs < 0L) {
                throw violation("next reconcile time is negative");
            }
            this.nextReconcileAtEpochMs = nextReconcileAtEpochMs;
            List<EffectObservation> copies = new ArrayList<>();
            for (EffectObservation transition : transitions) {
                copies.add(copyObservation(transition));
            }
            this.transitions = Collections.unmodifiableList(copies);
            this.latest = copyObservation(latest);
        }

        public Decision getDecision() {
            return decision;
        }

        public String getReasonCode() {
            return reasonCode;
        }

        public boolean wasAdapterQueried() {
            return adapterQueried;
        }

        public long getNextReconcileAtEpochMs() {
            return nextReconcileAtEpochMs;
        }

        public List<EffectObservation> getTransitions() {
            List<EffectObservation> copies = new ArrayList<>();
            for (EffectObservation transition : transitions) {
                copies.add(copyObservation(transition));
            }
            return Collections.unmodifiableList(copies);
        }

        public EffectObservation getLatestObservation() {
            return copyObservation(latest);
        }
    }
}
