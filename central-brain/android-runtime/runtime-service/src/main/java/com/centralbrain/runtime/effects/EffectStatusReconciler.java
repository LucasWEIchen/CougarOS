package com.centralbrain.runtime.effects;

import com.centralbrain.runtime.persistence.DurableEffectRepository;

import java.util.Objects;

/** Reconciles an interrupted claim through adapter status only; it never invokes apply. */
public final class EffectStatusReconciler {
    private final DurableEffectRepository repository;

    public EffectStatusReconciler(DurableEffectRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public Result reconcile(
            DurableEffectRepository.Snapshot interruptedClaim,
            EffectAdapter adapter,
            long retryDelayMs) {
        Objects.requireNonNull(interruptedClaim, "interruptedClaim");
        EffectAdapterContract.requireSafe(adapter, interruptedClaim.getDestination());
        if (!DurableEffectRepository.EFFECT_STATE_IN_FLIGHT.equals(
                        interruptedClaim.getEffectState())
                || !DurableEffectRepository.OUTBOX_STATE_IN_FLIGHT.equals(
                        interruptedClaim.getOutboxState())) {
            throw new IllegalArgumentException("status reconciliation requires an IN_FLIGHT claim");
        }

        EffectAdapter.StatusResult status;
        try {
            status = adapter.queryStatus(interruptedClaim.getIdempotencyToken());
        } catch (EffectAdapter.AdapterUnavailableException unavailable) {
            return Result.deferred();
        }
        EffectAdapterContract.requireStatusResultMatches(
                interruptedClaim.getIdempotencyToken(),
                status);

        switch (status.getState()) {
            case APPLIED:
                return Result.mutated(
                        Decision.APPLIED,
                        repository.recordSuccess(
                                interruptedClaim.getEffectId(),
                                interruptedClaim.getOutboxId(),
                                interruptedClaim.getOwnerFingerprint(),
                                interruptedClaim.getAttemptCount(),
                                status.getEvidenceDigest()));
            case NOT_APPLIED:
                if (interruptedClaim.getAttemptCount() < repository.getMaxAttempts()) {
                    return Result.mutated(
                            Decision.RETRY_SCHEDULED,
                            repository.scheduleRetry(
                                    interruptedClaim.getEffectId(),
                                    interruptedClaim.getOutboxId(),
                                    interruptedClaim.getOwnerFingerprint(),
                                    interruptedClaim.getAttemptCount(),
                                    retryDelayMs,
                                    status.getEvidenceDigest()));
                }
                return Result.mutated(
                        Decision.DEAD_LETTERED,
                        repository.deadLetter(
                                interruptedClaim.getEffectId(),
                                interruptedClaim.getOutboxId(),
                                interruptedClaim.getOwnerFingerprint(),
                                interruptedClaim.getAttemptCount(),
                                status.getEvidenceDigest()));
            case REJECTED:
            case UNKNOWN:
                return Result.mutated(
                        Decision.DEAD_LETTERED,
                        repository.deadLetter(
                                interruptedClaim.getEffectId(),
                                interruptedClaim.getOutboxId(),
                                interruptedClaim.getOwnerFingerprint(),
                                interruptedClaim.getAttemptCount(),
                                status.getEvidenceDigest()));
            default:
                throw new IllegalStateException("unsupported adapter delivery state");
        }
    }

    public enum Decision {
        APPLIED,
        RETRY_SCHEDULED,
        DEAD_LETTERED,
        DEFERRED
    }

    public static final class Result {
        private final Decision decision;
        private final DurableEffectRepository.MutationOutcome mutationOutcome;

        private Result(
                Decision decision,
                DurableEffectRepository.MutationOutcome mutationOutcome) {
            this.decision = decision;
            this.mutationOutcome = mutationOutcome;
        }

        private static Result mutated(
                Decision decision,
                DurableEffectRepository.MutationOutcome mutationOutcome) {
            return new Result(decision, mutationOutcome);
        }

        private static Result deferred() {
            return new Result(Decision.DEFERRED, null);
        }

        public Decision getDecision() {
            return decision;
        }

        public DurableEffectRepository.MutationOutcome getMutationOutcome() {
            return mutationOutcome;
        }

        public boolean isReplay() {
            return mutationOutcome == DurableEffectRepository.MutationOutcome.REPLAYED;
        }
    }
}
