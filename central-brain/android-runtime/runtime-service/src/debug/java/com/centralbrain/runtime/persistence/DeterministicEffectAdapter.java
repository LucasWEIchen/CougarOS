package com.centralbrain.runtime.persistence;

import com.centralbrain.runtime.effects.EffectAdapter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Debug-only remote-adapter fixture with durable-token semantics in process memory. */
final class DeterministicEffectAdapter implements EffectAdapter {
    private final Descriptor descriptor;
    private final Map<String, AppliedRecord> applied = new HashMap<>();
    private final Map<String, StatusResult> statusOverrides = new HashMap<>();
    private final Map<String, Integer> applyCounts = new HashMap<>();
    private final Set<String> unavailableTokens = new HashSet<>();

    DeterministicEffectAdapter(String destination) {
        descriptor = new Descriptor(
                "debug.deterministic.effect",
                destination,
                IdempotencyMode.TOKEN_DEDUPLICATED,
                true,
                true,
                StatusConsistency.LINEARIZABLE,
                1_000);
    }

    @Override
    public Descriptor descriptor() {
        return descriptor;
    }

    @Override
    public synchronized ApplyResult apply(Invocation invocation) {
        String token = invocation.getIdempotencyToken();
        String invocationDigest = DurableDigest.sha256(
                "central-brain-debug-adapter-invocation-v1",
                invocation.getEffectId(),
                invocation.getOutboxId(),
                invocation.getDestination(),
                invocation.getActionId(),
                invocation.getPayloadDigest(),
                invocation.getEnvelopeDigest());
        AppliedRecord existing = applied.get(token);
        if (existing != null) {
            if (!existing.invocationDigest.equals(invocationDigest)) {
                throw new IllegalStateException(
                        "idempotency token is bound to another adapter invocation");
            }
            return existing.result;
        }
        String resultDigest = DurableDigest.sha256(
                "central-brain-debug-adapter-applied-v1",
                token,
                invocationDigest);
        ApplyResult result = new ApplyResult(token, ApplyState.APPLIED, resultDigest);
        applied.put(token, new AppliedRecord(invocationDigest, result));
        applyCounts.put(token, getApplyCount(token) + 1);
        statusOverrides.remove(token);
        return result;
    }

    @Override
    public synchronized StatusResult queryStatus(String idempotencyToken) {
        if (unavailableTokens.contains(idempotencyToken)) {
            throw new AdapterUnavailableException("debug adapter status is unavailable");
        }
        StatusResult override = statusOverrides.get(idempotencyToken);
        if (override != null) {
            return override;
        }
        AppliedRecord record = applied.get(idempotencyToken);
        if (record != null) {
            return new StatusResult(
                    idempotencyToken,
                    DeliveryState.APPLIED,
                    record.result.getEvidenceDigest());
        }
        return new StatusResult(
                idempotencyToken,
                DeliveryState.NOT_APPLIED,
                DurableDigest.sha256(
                        "central-brain-debug-adapter-not-applied-v1",
                        idempotencyToken));
    }

    synchronized void setStatus(String idempotencyToken, DeliveryState state) {
        statusOverrides.put(
                idempotencyToken,
                new StatusResult(
                        idempotencyToken,
                        state,
                        DurableDigest.sha256(
                                "central-brain-debug-adapter-status-v1",
                                idempotencyToken,
                                state.name())));
    }

    synchronized void setStatusUnavailable(String idempotencyToken, boolean unavailable) {
        if (unavailable) {
            unavailableTokens.add(idempotencyToken);
        } else {
            unavailableTokens.remove(idempotencyToken);
        }
    }

    synchronized int getApplyCount(String idempotencyToken) {
        Integer count = applyCounts.get(idempotencyToken);
        return count == null ? 0 : count;
    }

    private static final class AppliedRecord {
        final String invocationDigest;
        final ApplyResult result;

        AppliedRecord(String invocationDigest, ApplyResult result) {
            this.invocationDigest = invocationDigest;
            this.result = result;
        }
    }
}
