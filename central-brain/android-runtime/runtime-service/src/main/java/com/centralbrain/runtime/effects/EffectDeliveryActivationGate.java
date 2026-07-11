package com.centralbrain.runtime.effects;

import com.centralbrain.runtime.persistence.DurableEffectRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/** Necessary-condition gate for any future production effect dispatcher. */
public final class EffectDeliveryActivationGate {
    public static final long MAX_RETENTION_MS = 2_592_000_000L;

    public Result evaluate(
            EffectAdapter adapter,
            EffectMaterialSource materialSource,
            String expectedDestination) {
        EnumSet<Blocker> blockers = EnumSet.noneOf(Blocker.class);
        if (adapter == null) {
            blockers.add(Blocker.ADAPTER_MISSING);
        } else {
            try {
                EffectAdapterContract.requireSafe(adapter, expectedDestination);
            } catch (RuntimeException unsafe) {
                blockers.add(Blocker.ADAPTER_UNSAFE);
            }
        }

        if (materialSource == null) {
            blockers.add(Blocker.MATERIAL_SOURCE_MISSING);
        } else {
            EffectMaterialSource.Descriptor descriptor;
            try {
                descriptor = Objects.requireNonNull(
                        materialSource.descriptor(),
                        "material source descriptor");
            } catch (RuntimeException invalid) {
                blockers.add(Blocker.MATERIAL_DESCRIPTOR_INVALID);
                return new Result(blockers);
            }
            if (descriptor.getAvailability() != EffectMaterialSource.Availability.AVAILABLE) {
                blockers.add(Blocker.MATERIAL_SOURCE_EMPTY);
            }
            if (descriptor.getAssurance() != EffectMaterialSource.Assurance.PRODUCTION) {
                blockers.add(Blocker.MATERIAL_SOURCE_NOT_PRODUCTION);
            }
            if (!descriptor.isDurableAcrossProcessRestart()) {
                blockers.add(Blocker.MATERIAL_NOT_DURABLE);
            }
            if (!descriptor.isEncryptedAtRest()) {
                blockers.add(Blocker.MATERIAL_NOT_ENCRYPTED);
            }
            if (!descriptor.isIntegrityBoundToEffect()) {
                blockers.add(Blocker.MATERIAL_INTEGRITY_UNBOUND);
            }
            if (!descriptor.isDeleteSupported()) {
                blockers.add(Blocker.MATERIAL_DELETE_UNSUPPORTED);
            }
            if (descriptor.getRetentionMs() < 1
                    || descriptor.getRetentionMs() > MAX_RETENTION_MS) {
                blockers.add(Blocker.MATERIAL_RETENTION_INVALID);
            }
        }
        return new Result(blockers);
    }

    public EffectAdapter.Invocation resolveInvocation(
            DurableEffectRepository.Snapshot claim,
            int maxAttempts,
            EffectAdapter adapter,
            EffectMaterialSource materialSource) {
        Objects.requireNonNull(claim, "claim");
        Result activation = evaluate(adapter, materialSource, claim.getDestination());
        if (!activation.isAllowed()) {
            throw new ActivationBlockedException(activation.getBlockers());
        }
        EffectMaterialSource.Material material = Objects.requireNonNull(
                materialSource.resolve(claim),
                "resolved effect material");
        if (!claim.getEffectId().equals(material.getEffectId())) {
            throw new IllegalArgumentException("resolved material effect ID does not match claim");
        }
        EffectAdapter.Invocation invocation = new EffectAdapter.Invocation(
                claim.getEffectId(),
                claim.getOutboxId(),
                claim.getIdempotencyToken(),
                claim.getDestination(),
                claim.getActionId(),
                claim.getPayloadDigest(),
                claim.getEnvelopeDigest(),
                claim.getAttemptCount(),
                maxAttempts,
                material.getCanonicalPayload(),
                material.getCanonicalEnvelope());
        return EffectAdapterContract.requireInvocationMatches(
                claim,
                invocation,
                maxAttempts);
    }

    public enum Blocker {
        ADAPTER_MISSING,
        ADAPTER_UNSAFE,
        MATERIAL_SOURCE_MISSING,
        MATERIAL_DESCRIPTOR_INVALID,
        MATERIAL_SOURCE_EMPTY,
        MATERIAL_SOURCE_NOT_PRODUCTION,
        MATERIAL_NOT_DURABLE,
        MATERIAL_NOT_ENCRYPTED,
        MATERIAL_INTEGRITY_UNBOUND,
        MATERIAL_DELETE_UNSUPPORTED,
        MATERIAL_RETENTION_INVALID
    }

    public static final class Result {
        private final List<Blocker> blockers;

        private Result(EnumSet<Blocker> blockers) {
            this.blockers = Collections.unmodifiableList(new ArrayList<>(blockers));
        }

        public boolean isAllowed() {
            return blockers.isEmpty();
        }

        public List<Blocker> getBlockers() {
            return blockers;
        }

        public boolean hasBlocker(Blocker blocker) {
            return blockers.contains(blocker);
        }
    }

    public static final class ActivationBlockedException extends IllegalStateException {
        private final List<Blocker> blockers;

        private ActivationBlockedException(List<Blocker> blockers) {
            super("effect delivery activation is blocked: " + blockers);
            this.blockers = blockers;
        }

        public List<Blocker> getBlockers() {
            return blockers;
        }
    }
}
