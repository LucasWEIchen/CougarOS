package com.centralbrain.runtime.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Immutable production visibility for the currently blocked model runtime. */
public final class ModelRuntimeReadinessSnapshot {
    public enum Blocker {
        PRODUCTION_PROVIDER_MISSING,
        PRODUCTION_ROUTE_MISSING,
        SCHEDULER_NOT_WIRED,
        MODEL_ROUTER_NOT_WIRED,
        VENDOR_NPU_INTERFACE_EMPTY
    }

    private static final ModelRuntimeReadinessSnapshot CURRENT = createCurrent();

    private final ModelProviderProfiles.Profile deterministicStub;
    private final ModelProviderProfiles.Profile vendorNpu;
    private final List<Blocker> blockers;

    private ModelRuntimeReadinessSnapshot(
            ModelProviderProfiles.Profile deterministicStub,
            ModelProviderProfiles.Profile vendorNpu,
            List<Blocker> blockers) {
        this.deterministicStub = deterministicStub;
        this.vendorNpu = vendorNpu;
        this.blockers = Collections.unmodifiableList(blockers);
    }

    public static ModelRuntimeReadinessSnapshot current() {
        return CURRENT;
    }

    private static ModelRuntimeReadinessSnapshot createCurrent() {
        ModelProviderProfiles.Profile deterministicStub =
                ModelProviderProfiles.deterministicStub();
        ModelProviderProfiles.Profile vendorNpu = ModelProviderProfiles.vendorNpuEmpty();
        if (deterministicStub.isImplementationConfigured()
                || deterministicStub.isRoutingEnabled()
                || deterministicStub.getDescriptor().isProductionEligible()
                || deterministicStub.getDescriptor().isHardwareBacked()
                || deterministicStub.getSnapshot().isHardwareAccessed()
                || vendorNpu.isImplementationConfigured()
                || vendorNpu.isRoutingEnabled()
                || vendorNpu.getDescriptor().getAssurance()
                        != ModelProvider.Assurance.EMPTY
                || vendorNpu.getSnapshot().getLifecycleState()
                        != ModelProvider.LifecycleState.UNAVAILABLE
                || vendorNpu.getSnapshot().getHealthState()
                        != ModelProvider.HealthState.UNAVAILABLE
                || vendorNpu.getSnapshot().isHardwareAccessed()) {
            throw new IllegalStateException(
                    "current model runtime configuration must remain blocked");
        }
        return new ModelRuntimeReadinessSnapshot(
                deterministicStub,
                vendorNpu,
                Arrays.asList(
                        Blocker.PRODUCTION_PROVIDER_MISSING,
                        Blocker.PRODUCTION_ROUTE_MISSING,
                        Blocker.SCHEDULER_NOT_WIRED,
                        Blocker.MODEL_ROUTER_NOT_WIRED,
                        Blocker.VENDOR_NPU_INTERFACE_EMPTY));
    }

    public boolean isProductionInferenceAllowed() {
        return false;
    }

    public boolean isModelProviderContractAvailable() {
        return true;
    }

    public boolean isInferenceSchedulerContractAvailable() {
        return true;
    }

    public boolean isTestModelRouterImplementationAvailable() {
        return true;
    }

    public boolean isTestModelRouterTestOnly() {
        return true;
    }

    public boolean isSchedulerProductionWired() {
        return false;
    }

    public boolean isProductionModelRouterWired() {
        return false;
    }

    public boolean isProductionModelRouterDispatchEnabled() {
        return false;
    }

    public boolean isOllamaAndroidProviderConfigured() {
        return false;
    }

    public boolean isVendorNpuProviderAvailable() {
        return false;
    }

    public boolean isHardwareAccessed() {
        return false;
    }

    public String getDeterministicStubProfileId() {
        return deterministicStub.getDescriptor().getProviderId();
    }

    public ModelProvider.Assurance getDeterministicStubAssurance() {
        return deterministicStub.getDescriptor().getAssurance();
    }

    public ModelProvider.LifecycleState getDeterministicStubLifecycle() {
        return deterministicStub.getSnapshot().getLifecycleState();
    }

    public ModelProvider.HealthState getDeterministicStubHealth() {
        return deterministicStub.getSnapshot().getHealthState();
    }

    public String getDeterministicStubDetailCode() {
        return deterministicStub.getSnapshot().getDetailCode();
    }

    public boolean isDeterministicStubImplementationConfigured() {
        return deterministicStub.isImplementationConfigured();
    }

    public boolean isDeterministicStubRoutingEnabled() {
        return deterministicStub.isRoutingEnabled();
    }

    public String getVendorNpuProfileId() {
        return vendorNpu.getDescriptor().getProviderId();
    }

    public ModelProvider.Assurance getVendorNpuAssurance() {
        return vendorNpu.getDescriptor().getAssurance();
    }

    public ModelProvider.LifecycleState getVendorNpuLifecycle() {
        return vendorNpu.getSnapshot().getLifecycleState();
    }

    public ModelProvider.HealthState getVendorNpuHealth() {
        return vendorNpu.getSnapshot().getHealthState();
    }

    public String getVendorNpuDetailCode() {
        return vendorNpu.getSnapshot().getDetailCode();
    }

    public List<Blocker> getBlockers() {
        return blockers;
    }

    public String getBlockersCsv() {
        return blockers.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    public String diagnosticDetail() {
        return "production_inference_allowed=false"
                + ";model_provider_contract_available=true"
                + ";inference_scheduler_contract_available=true"
                + ";test_model_router_implementation_available=true"
                + ";model_router_test_only=true"
                + ";deterministic_stub_profile_id=" + getDeterministicStubProfileId()
                + ";deterministic_stub_assurance=" + getDeterministicStubAssurance()
                + ";deterministic_stub_lifecycle=" + getDeterministicStubLifecycle()
                + ";deterministic_stub_health=" + getDeterministicStubHealth()
                + ";deterministic_stub_detail_code=" + getDeterministicStubDetailCode()
                + ";deterministic_stub_implementation_configured=false"
                + ";deterministic_stub_routing_enabled=false"
                + ";vendor_npu_profile_id=" + getVendorNpuProfileId()
                + ";vendor_npu_assurance=" + getVendorNpuAssurance()
                + ";vendor_npu_lifecycle=" + getVendorNpuLifecycle()
                + ";vendor_npu_health=" + getVendorNpuHealth()
                + ";vendor_npu_detail_code=" + getVendorNpuDetailCode()
                + ";vendor_npu_provider_available=false"
                + ";scheduler_production_wired=false"
                + ";production_model_router_wired=false"
                + ";production_model_router_dispatch_enabled=false"
                + ";ollama_android_provider_configured=false"
                + ";blockers=" + getBlockersCsv()
                + ";service_dispatch_triggered=false"
                + ";hardware_accessed=false";
    }
}
