package com.centralbrain.runtime.effects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;

import com.centralbrain.runtime.persistence.DurableEffectRepository;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class EffectAdapterContractTest {
    private static final String TOKEN = repeat("a", 64);
    private static final byte[] PAYLOAD = "payload".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ENVELOPE = "envelope".getBytes(StandardCharsets.UTF_8);

    @Test
    public void safeContractRequiresTokenDeduplicationAndLinearizableStatus() {
        EffectAdapter safe = adapter(
                EffectAdapter.IdempotencyMode.TOKEN_DEDUPLICATED,
                true,
                true,
                EffectAdapter.StatusConsistency.LINEARIZABLE);
        assertEquals(
                "adapter.test",
                EffectAdapterContract.requireSafe(
                        safe,
                        DurableEffectRepository.DESTINATION_UIB_ACTION).getAdapterId());

        assertThrows(
                EffectAdapterContract.UnsafeAdapterException.class,
                () -> EffectAdapterContract.requireSafe(
                        adapter(
                                EffectAdapter.IdempotencyMode.NONE,
                                true,
                                true,
                                EffectAdapter.StatusConsistency.LINEARIZABLE),
                        DurableEffectRepository.DESTINATION_UIB_ACTION));
        assertThrows(
                EffectAdapterContract.UnsafeAdapterException.class,
                () -> EffectAdapterContract.requireSafe(
                        adapter(
                                EffectAdapter.IdempotencyMode.TOKEN_DEDUPLICATED,
                                false,
                                true,
                                EffectAdapter.StatusConsistency.LINEARIZABLE),
                        DurableEffectRepository.DESTINATION_UIB_ACTION));
        assertThrows(
                EffectAdapterContract.UnsafeAdapterException.class,
                () -> EffectAdapterContract.requireSafe(
                        adapter(
                                EffectAdapter.IdempotencyMode.TOKEN_DEDUPLICATED,
                                true,
                                true,
                                EffectAdapter.StatusConsistency.EVENTUAL),
                        DurableEffectRepository.DESTINATION_UIB_ACTION));
        assertThrows(
                EffectAdapterContract.UnsafeAdapterException.class,
                () -> EffectAdapterContract.requireSafe(
                        adapter(
                                EffectAdapter.IdempotencyMode.TOKEN_DEDUPLICATED,
                                true,
                                false,
                                EffectAdapter.StatusConsistency.LINEARIZABLE),
                        DurableEffectRepository.DESTINATION_UIB_ACTION));
    }

    @Test
    public void invocationVerifiesTransientMaterialAndDefensiveCopies() {
        EffectAdapter.Invocation invocation = invocation(PAYLOAD, ENVELOPE);
        byte[] first = invocation.getCanonicalPayload();
        byte[] second = invocation.getCanonicalPayload();
        assertNotSame(first, second);
        first[0] = 0;
        assertEquals(PAYLOAD[0], invocation.getCanonicalPayload()[0]);

        assertThrows(
                IllegalArgumentException.class,
                () -> new EffectAdapter.Invocation(
                        "effect-1",
                        "outbox-1",
                        TOKEN,
                        DurableEffectRepository.DESTINATION_UIB_ACTION,
                        "climate.setTemperature",
                        sha256(PAYLOAD),
                        sha256(ENVELOPE),
                        1,
                        3,
                        "changed".getBytes(StandardCharsets.UTF_8),
                        ENVELOPE));
    }

    @Test
    public void adapterResponsesMustEchoThePersistedToken() {
        EffectAdapter.Invocation invocation = invocation(PAYLOAD, ENVELOPE);
        assertThrows(
                EffectAdapterContract.UnsafeAdapterException.class,
                () -> EffectAdapterContract.requireApplyResultMatches(
                        invocation,
                        new EffectAdapter.ApplyResult(
                                repeat("b", 64),
                                EffectAdapter.ApplyState.APPLIED,
                                repeat("c", 64))));
        assertThrows(
                EffectAdapterContract.UnsafeAdapterException.class,
                () -> EffectAdapterContract.requireStatusResultMatches(
                        TOKEN,
                        new EffectAdapter.StatusResult(
                                repeat("b", 64),
                                EffectAdapter.DeliveryState.APPLIED,
                                repeat("c", 64))));
    }

    private static EffectAdapter.Invocation invocation(byte[] payload, byte[] envelope) {
        return new EffectAdapter.Invocation(
                "effect-1",
                "outbox-1",
                TOKEN,
                DurableEffectRepository.DESTINATION_UIB_ACTION,
                "climate.setTemperature",
                sha256(payload),
                sha256(envelope),
                1,
                3,
                payload,
                envelope);
    }

    private static EffectAdapter adapter(
            EffectAdapter.IdempotencyMode mode,
            boolean duplicateReturnsOriginal,
            boolean statusReturnsOriginalEvidence,
            EffectAdapter.StatusConsistency consistency) {
        return new EffectAdapter() {
            @Override
            public Descriptor descriptor() {
                return new Descriptor(
                        "adapter.test",
                        DurableEffectRepository.DESTINATION_UIB_ACTION,
                        mode,
                        duplicateReturnsOriginal,
                        statusReturnsOriginalEvidence,
                        consistency,
                        1_000);
            }

            @Override
            public ApplyResult apply(Invocation invocation) {
                return new ApplyResult(
                        invocation.getIdempotencyToken(),
                        ApplyState.APPLIED,
                        repeat("c", 64));
            }

            @Override
            public StatusResult queryStatus(String idempotencyToken) {
                return new StatusResult(
                        idempotencyToken,
                        DeliveryState.NOT_APPLIED,
                        repeat("d", 64));
            }
        };
    }

    private static String sha256(byte[] value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value);
            StringBuilder output = new StringBuilder(bytes.length * 2);
            for (byte current : bytes) {
                output.append(String.format("%02x", current & 0xff));
            }
            return output.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String repeat(String value, int count) {
        return String.join("", java.util.Collections.nCopies(count, value));
    }
}
