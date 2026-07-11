package com.centralbrain.runtime.effects;

import com.centralbrain.runtime.persistence.DurableEffectRepository;

import java.util.Objects;

/** Fail-closed activation and response validation for effect adapters. */
public final class EffectAdapterContract {
    private EffectAdapterContract() {
    }

    public static EffectAdapter.Descriptor requireSafe(
            EffectAdapter adapter,
            String expectedDestination) {
        Objects.requireNonNull(adapter, "adapter");
        EffectAdapter.Descriptor descriptor = Objects.requireNonNull(
                adapter.descriptor(),
                "adapter descriptor");
        if (!descriptor.getDestination().equals(expectedDestination)) {
            throw new UnsafeAdapterException("adapter destination does not match effect route");
        }
        if (descriptor.getIdempotencyMode()
                != EffectAdapter.IdempotencyMode.TOKEN_DEDUPLICATED) {
            throw new UnsafeAdapterException("adapter does not deduplicate the persisted token");
        }
        if (!descriptor.isDuplicateApplyReturnsOriginal()) {
            throw new UnsafeAdapterException(
                    "duplicate adapter apply does not return the original result");
        }
        if (!descriptor.isAppliedStatusReturnsOriginalEvidence()) {
            throw new UnsafeAdapterException(
                    "applied status does not return the original result evidence");
        }
        if (descriptor.getStatusConsistency()
                != EffectAdapter.StatusConsistency.LINEARIZABLE) {
            throw new UnsafeAdapterException("adapter status query is not linearizable");
        }
        return descriptor;
    }

    public static EffectAdapter.Invocation requireInvocationMatches(
            DurableEffectRepository.Snapshot claim,
            EffectAdapter.Invocation invocation,
            int maxAttempts) {
        Objects.requireNonNull(claim, "claim");
        Objects.requireNonNull(invocation, "invocation");
        if (!DurableEffectRepository.EFFECT_STATE_IN_FLIGHT.equals(claim.getEffectState())
                || !DurableEffectRepository.OUTBOX_STATE_IN_FLIGHT.equals(
                        claim.getOutboxState())) {
            throw new IllegalArgumentException("adapter invocation requires an IN_FLIGHT claim");
        }
        if (!claim.getEffectId().equals(invocation.getEffectId())
                || !claim.getOutboxId().equals(invocation.getOutboxId())
                || !claim.getIdempotencyToken().equals(invocation.getIdempotencyToken())
                || !claim.getDestination().equals(invocation.getDestination())
                || !claim.getActionId().equals(invocation.getActionId())
                || !claim.getPayloadDigest().equals(invocation.getPayloadDigest())
                || !claim.getEnvelopeDigest().equals(invocation.getEnvelopeDigest())
                || claim.getAttemptCount() != invocation.getAttempt()
                || invocation.getMaxAttempts() != maxAttempts) {
            throw new IllegalArgumentException(
                    "adapter invocation does not match the durable claim");
        }
        return invocation;
    }

    public static EffectAdapter.ApplyResult requireApplyResultMatches(
            EffectAdapter.Invocation invocation,
            EffectAdapter.ApplyResult result) {
        Objects.requireNonNull(invocation, "invocation");
        Objects.requireNonNull(result, "result");
        if (!invocation.getIdempotencyToken().equals(result.getIdempotencyToken())) {
            throw new UnsafeAdapterException("adapter apply result token does not match request");
        }
        return result;
    }

    public static EffectAdapter.StatusResult requireStatusResultMatches(
            String idempotencyToken,
            EffectAdapter.StatusResult result) {
        Objects.requireNonNull(result, "result");
        if (!idempotencyToken.equals(result.getIdempotencyToken())) {
            throw new UnsafeAdapterException("adapter status token does not match query");
        }
        return result;
    }

    public static final class UnsafeAdapterException extends IllegalStateException {
        public UnsafeAdapterException(String message) {
            super(message);
        }
    }
}
