package com.centralbrain.runtime.effects;

import com.centralbrain.runtime.persistence.DurableEffectRepository;

/** Explicit no-material provider for the current production configuration. */
public final class EmptyEffectMaterialSource implements EffectMaterialSource {
    private static final Descriptor DESCRIPTOR = new Descriptor(
            "empty.effect.material",
            Availability.EMPTY,
            Assurance.UNTRUSTED,
            false,
            false,
            false,
            false,
            0);

    @Override
    public Descriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public Material resolve(DurableEffectRepository.Snapshot claim) {
        throw new MaterialUnavailableException(
                "canonical effect material is unavailable in the current product configuration");
    }
}
