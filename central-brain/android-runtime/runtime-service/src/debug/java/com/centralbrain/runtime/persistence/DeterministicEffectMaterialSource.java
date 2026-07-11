package com.centralbrain.runtime.persistence;

import com.centralbrain.runtime.effects.EffectMaterialSource;

import java.util.HashMap;
import java.util.Map;

/** Debug-only material source used to exercise the activation contract. */
final class DeterministicEffectMaterialSource implements EffectMaterialSource {
    private final Descriptor descriptor;
    private final Map<String, Material> materials = new HashMap<>();

    DeterministicEffectMaterialSource(Assurance assurance) {
        descriptor = new Descriptor(
                "debug.deterministic.material",
                Availability.AVAILABLE,
                assurance,
                true,
                true,
                true,
                true,
                86_400_000L);
    }

    @Override
    public Descriptor descriptor() {
        return descriptor;
    }

    @Override
    public synchronized Material resolve(DurableEffectRepository.Snapshot claim) {
        Material material = materials.get(claim.getEffectId());
        if (material == null) {
            throw new MaterialUnavailableException("debug material is not registered");
        }
        return material;
    }

    synchronized void register(
            DurableEffectRepository.Snapshot claim,
            byte[] canonicalPayload,
            byte[] canonicalEnvelope) {
        String revision = DurableDigest.sha256(
                "central-brain-debug-material-revision-v1",
                claim.getEffectId(),
                claim.getPayloadDigest(),
                claim.getEnvelopeDigest());
        materials.put(
                claim.getEffectId(),
                new Material(
                        claim.getEffectId(),
                        revision,
                        canonicalPayload,
                        canonicalEnvelope));
    }
}
