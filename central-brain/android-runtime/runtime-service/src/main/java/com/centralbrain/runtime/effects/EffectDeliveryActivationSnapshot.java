package com.centralbrain.runtime.effects;

import com.centralbrain.runtime.persistence.DurableEffectRepository;

import java.util.List;
import java.util.stream.Collectors;

/** Immutable current-product activation state shared by Runtime and diagnostics. */
public final class EffectDeliveryActivationSnapshot {
    private static final EffectDeliveryActivationSnapshot CURRENT = createCurrent();

    private final EffectDeliveryActivationGate.Result result;
    private final String materialSourceId;

    private EffectDeliveryActivationSnapshot(
            EffectDeliveryActivationGate.Result result,
            String materialSourceId) {
        this.result = result;
        this.materialSourceId = materialSourceId;
    }

    public static EffectDeliveryActivationSnapshot current() {
        return CURRENT;
    }

    private static EffectDeliveryActivationSnapshot createCurrent() {
        EmptyEffectMaterialSource materialSource = new EmptyEffectMaterialSource();
        EffectDeliveryActivationGate.Result result = new EffectDeliveryActivationGate().evaluate(
                null,
                materialSource,
                DurableEffectRepository.DESTINATION_UIB_ACTION);
        if (result.isAllowed()) {
            throw new IllegalStateException(
                    "current empty effect delivery configuration must remain blocked");
        }
        return new EffectDeliveryActivationSnapshot(
                result,
                materialSource.descriptor().getSourceId());
    }

    public boolean isActivationAllowed() {
        return result.isAllowed();
    }

    public boolean isAdapterConfigured() {
        return false;
    }

    public boolean isMaterialDurable() {
        return false;
    }

    public boolean isApplyEnabled() {
        return false;
    }

    public boolean isStatusQueryEnabled() {
        return false;
    }

    public String getMaterialSourceId() {
        return materialSourceId;
    }

    public List<EffectDeliveryActivationGate.Blocker> getBlockers() {
        return result.getBlockers();
    }

    public String getBlockersCsv() {
        return result.getBlockers().stream()
                .map(Enum::name)
                .collect(Collectors.joining(","));
    }

    public String diagnosticDetail() {
        return "activation_allowed=false"
                + ";adapter_configured=false"
                + ";material_source=" + materialSourceId
                + ";material_durable=false"
                + ";apply_enabled=false"
                + ";status_query_enabled=false"
                + ";blockers=" + getBlockersCsv()
                + ";service_dispatch_triggered=false"
                + ";hardware_accessed=false";
    }
}
