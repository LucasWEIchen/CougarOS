package com.centralbrain.runtime.model;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public final class ModelProviderContractProbeActivity extends Activity {
    private static final String TAG = "CbModelProbe";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            ModelProviderProfiles.Profile stub = ModelProviderProfiles.deterministicStub();
            ModelProvider.Descriptor stubDescriptor = stub.getDescriptor();
            boolean deterministicStubProfileVerified =
                    stubDescriptor.getBackendKind()
                                    == ModelProvider.BackendKind.DETERMINISTIC_STUB
                            && stubDescriptor.getAssurance()
                                    == ModelProvider.Assurance.TEST_ONLY
                            && stubDescriptor.isSupportsWarmup()
                            && stubDescriptor.isSupportsInference()
                            && stubDescriptor.isSupportsStreaming()
                            && stubDescriptor.isSupportsCancellation()
                            && stubDescriptor.isSupportsMetrics()
                            && stubDescriptor.isSupportsFaultReporting()
                            && stubDescriptor.getMaxConcurrentRequests() == 1
                            && !stubDescriptor.isHardwareBacked()
                            && !stubDescriptor.isProductionEligible()
                            && !stub.isImplementationConfigured()
                            && !stub.isRoutingEnabled()
                            && stub.getSnapshot().getLifecycleState()
                                    == ModelProvider.LifecycleState.COLD
                            && !stub.getSnapshot().isHardwareAccessed();

            ModelProviderProfiles.Profile vendor = ModelProviderProfiles.vendorNpuEmpty();
            ModelProvider.Descriptor vendorDescriptor = vendor.getDescriptor();
            boolean vendorNpuEmptyProfileVerified =
                    vendorDescriptor.getBackendKind() == ModelProvider.BackendKind.VENDOR_NPU
                            && vendorDescriptor.getAssurance() == ModelProvider.Assurance.EMPTY
                            && !vendorDescriptor.isSupportsWarmup()
                            && !vendorDescriptor.isSupportsInference()
                            && !vendorDescriptor.isSupportsStreaming()
                            && !vendorDescriptor.isSupportsCancellation()
                            && !vendorDescriptor.isSupportsMetrics()
                            && vendorDescriptor.isSupportsFaultReporting()
                            && vendorDescriptor.getMaxConcurrentRequests() == 0
                            && !vendorDescriptor.isHardwareBacked()
                            && !vendor.isImplementationConfigured()
                            && !vendor.isRoutingEnabled()
                            && vendor.getSnapshot().getLifecycleState()
                                    == ModelProvider.LifecycleState.UNAVAILABLE
                            && vendor.getSnapshot().getHealthState()
                                    == ModelProvider.HealthState.UNAVAILABLE
                            && !vendor.getSnapshot().isHardwareAccessed();

            boolean unsafeProviderDescriptorRejected = false;
            try {
                new ModelProvider.Descriptor(
                        "unsafe.stub",
                        ModelProvider.BackendKind.DETERMINISTIC_STUB,
                        ModelProvider.Assurance.PRODUCTION,
                        ModelProvider.FallbackClass.POLICY_CONTROLLED,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        1);
            } catch (IllegalArgumentException expected) {
                unsafeProviderDescriptorRejected = true;
            }

            boolean contractVerified = deterministicStubProfileVerified
                    && vendorNpuEmptyProfileVerified
                    && unsafeProviderDescriptorRejected;
            Log.i(TAG, "nonce=" + nonce
                    + " model_provider_probe_complete=true"
                    + " model_provider_contract_verified=" + contractVerified
                    + " deterministic_stub_profile_verified="
                    + deterministicStubProfileVerified
                    + " vendor_npu_empty_profile_verified="
                    + vendorNpuEmptyProfileVerified
                    + " unsafe_provider_descriptor_rejected="
                    + unsafeProviderDescriptorRejected
                    + " deterministic_stub_implementation_configured=false"
                    + " deterministic_stub_routing_enabled=false"
                    + " vendor_npu_provider_available=false"
                    + " model_provider_runtime_wired=false"
                    + " model_router_dispatch_enabled=false"
                    + " ollama_android_provider_configured=false"
                    + " hardware_accessed=false"
                    + " driver_development_triggered=false"
                    + " virtualization_development_triggered=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " model_provider_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " model_provider_runtime_wired=false"
                    + " model_router_dispatch_enabled=false"
                    + " vendor_npu_provider_available=false"
                    + " ollama_android_provider_configured=false"
                    + " hardware_accessed=false", exception);
        }
    }
}
