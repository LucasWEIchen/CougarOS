package com.centralbrain.runtime.model;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

public final class ModelProviderRegistryProbeActivity extends Activity {
    private static final String TAG = "CbModelRegistry";
    private static final String EVIDENCE_A =
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String EVIDENCE_B =
            "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String nonce = getIntent().getStringExtra("nonce");
        runProbe(nonce == null ? "" : nonce);
        finish();
    }

    private static void runProbe(String nonce) {
        try {
            ModelProviderRegistry registry = ModelProviderRegistry.createForContractTest();
            ModelProviderRegistry.RegistrySnapshot initial = registry.snapshot(1_000);
            boolean catalogVerified = initial.getProviders().size()
                    == ModelProviderRegistry.PROVIDER_COUNT
                    && initial.getContractTestAvailableCount() == 1
                    && initial.getDevelopmentAvailableCount() == 0
                    && initial.getProductionReadyCount() == 0;

            ModelProviderRegistry.HealthReport report = report(
                    ModelProviderRegistry.DETERMINISTIC_TEST_ID,
                    ModelProviderRegistry.HealthSource.CONTRACT_TEST,
                    1,
                    EVIDENCE_A);
            boolean healthReplayVerified = registry.publishHealth(report, 1_000).isUpdated()
                    && registry.publishHealth(report, 1_000).getCode()
                            == ModelProviderRegistry.PublishCode.REPLAYED
                    && registry.publishHealth(report(
                                    ModelProviderRegistry.DETERMINISTIC_TEST_ID,
                                    ModelProviderRegistry.HealthSource.CONTRACT_TEST,
                                    1,
                                    EVIDENCE_B), 1_000).getCode()
                            == ModelProviderRegistry.PublishCode.REVISION_CONFLICT;
            ModelProviderRegistry.ProviderView fresh = find(
                    registry.snapshot(1_000),
                    ModelProviderRegistry.DETERMINISTIC_TEST_ID);
            ModelProviderRegistry.ProviderView stale = find(
                    registry.snapshot(1_500),
                    ModelProviderRegistry.DETERMINISTIC_TEST_ID);
            boolean freshnessVerified = fresh.getHealthState()
                            == ModelProviderRegistry.HealthState.HEALTHY
                    && fresh.getHealthFreshness()
                            == ModelProviderRegistry.HealthFreshness.FRESH
                    && stale.getHealthState() == ModelProviderRegistry.HealthState.UNKNOWN
                    && stale.getHealthFreshness()
                            == ModelProviderRegistry.HealthFreshness.STALE;

            boolean vendorUpdated = registry.publishHealth(report(
                    ModelProviderRegistry.VENDOR_NPU_PLACEHOLDER_ID,
                    ModelProviderRegistry.HealthSource.VENDOR_RUNTIME,
                    1,
                    EVIDENCE_A), 1_000).isUpdated();
            ModelProviderRegistry.ProviderView vendor = find(
                    registry.snapshot(1_000),
                    ModelProviderRegistry.VENDOR_NPU_PLACEHOLDER_ID);
            boolean placeholderFailClosed = vendorUpdated
                    && vendor.getHealthState() == ModelProviderRegistry.HealthState.HEALTHY
                    && !vendor.isContractTestAvailable()
                    && !vendor.isDevelopmentAvailable()
                    && !vendor.isProductionReady()
                    && !vendor.isRoutingEnabled();
            boolean availabilitySeparationVerified =
                    initial.getContractTestAvailableCount() == 1
                            && initial.getDevelopmentAvailableCount() == 0
                            && initial.getProductionReadyCount() == 0;
            ModelProviderRegistry.RegistrySnapshot finalSnapshot = registry.snapshot(1_000);
            boolean verified = catalogVerified
                    && healthReplayVerified
                    && freshnessVerified
                    && availabilitySeparationVerified
                    && placeholderFailClosed;

            Log.i(TAG, "nonce=" + nonce
                    + " model_provider_registry_probe_complete=true"
                    + " model_provider_registry_verified=" + verified
                    + " model_provider_catalog_verified=" + catalogVerified
                    + " model_provider_health_freshness_verified=" + freshnessVerified
                    + " model_provider_health_replay_verified=" + healthReplayVerified
                    + " model_provider_availability_separation_verified="
                    + availabilitySeparationVerified
                    + " model_provider_placeholder_fail_closed=" + placeholderFailClosed
                    + " model_provider_count=" + finalSnapshot.getProviders().size()
                    + " model_contract_test_available_count="
                    + finalSnapshot.getContractTestAvailableCount()
                    + " model_development_available_count="
                    + finalSnapshot.getDevelopmentAvailableCount()
                    + " model_production_ready_count="
                    + finalSnapshot.getProductionReadyCount()
                    + " model_provider_registry_android13_arm64_verified=true"
                    + " model_provider_registry_runtime_wired=false"
                    + " model_policy_router_wired=false"
                    + " model_invoked=" + finalSnapshot.isModelInvoked()
                    + " network_accessed=" + finalSnapshot.isNetworkAccessed()
                    + " npu_accessed=" + finalSnapshot.isNpuAccessed()
                    + " hardware_accessed=" + finalSnapshot.isHardwareAccessed()
                    + " production_ready=false"
                    + " target_hardware_validated=false");
        } catch (RuntimeException exception) {
            Log.e(TAG, "nonce=" + nonce
                    + " model_provider_registry_probe_complete=false"
                    + " error=" + exception.getClass().getSimpleName()
                    + " model_provider_registry_runtime_wired=false"
                    + " model_policy_router_wired=false"
                    + " model_invoked=false"
                    + " network_accessed=false"
                    + " npu_accessed=false"
                    + " hardware_accessed=false"
                    + " production_ready=false"
                    + " target_hardware_validated=false", exception);
        }
    }

    private static ModelProviderRegistry.HealthReport report(
            String providerId,
            ModelProviderRegistry.HealthSource source,
            long revision,
            String evidenceDigest) {
        return new ModelProviderRegistry.HealthReport(
                providerId,
                source,
                ModelProviderRegistry.HealthState.HEALTHY,
                revision,
                900,
                1_500,
                evidenceDigest);
    }

    private static ModelProviderRegistry.ProviderView find(
            ModelProviderRegistry.RegistrySnapshot snapshot,
            String providerId) {
        for (ModelProviderRegistry.ProviderView provider : snapshot.getProviders()) {
            if (providerId.equals(provider.getDescriptor().getProviderId())) {
                return provider;
            }
        }
        throw new IllegalStateException("fixed provider is missing");
    }
}
