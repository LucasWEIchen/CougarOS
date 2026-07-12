package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;

import org.junit.Test;

public final class ModelRuntimeReadinessSnapshotTest {
    @Test
    public void currentSnapshotIsImmutableAndFailClosed() {
        ModelRuntimeReadinessSnapshot current = ModelRuntimeReadinessSnapshot.current();
        assertSame(current, ModelRuntimeReadinessSnapshot.current());
        assertFalse(current.isProductionInferenceAllowed());
        assertTrue(current.isModelProviderContractAvailable());
        assertTrue(current.isInferenceSchedulerContractAvailable());
        assertTrue(current.isTestModelRouterImplementationAvailable());
        assertTrue(current.isTestModelRouterTestOnly());
        assertFalse(current.isSchedulerProductionWired());
        assertFalse(current.isProductionModelRouterWired());
        assertFalse(current.isProductionModelRouterDispatchEnabled());
        assertFalse(current.isOllamaAndroidProviderConfigured());
        assertFalse(current.isVendorNpuProviderAvailable());
        assertFalse(current.isHardwareAccessed());
    }

    @Test
    public void currentProfilesAndBlockersRemainExplicit() {
        ModelRuntimeReadinessSnapshot current = ModelRuntimeReadinessSnapshot.current();
        assertEquals(ModelProviderProfiles.DETERMINISTIC_STUB_ID,
                current.getDeterministicStubProfileId());
        assertEquals(ModelProvider.Assurance.TEST_ONLY,
                current.getDeterministicStubAssurance());
        assertEquals(ModelProvider.LifecycleState.COLD,
                current.getDeterministicStubLifecycle());
        assertEquals(ModelProvider.HealthState.HEALTHY,
                current.getDeterministicStubHealth());
        assertEquals("STUB_IMPLEMENTATION_NOT_WIRED",
                current.getDeterministicStubDetailCode());
        assertFalse(current.isDeterministicStubImplementationConfigured());
        assertFalse(current.isDeterministicStubRoutingEnabled());
        assertEquals(ModelProviderProfiles.VENDOR_NPU_EMPTY_ID,
                current.getVendorNpuProfileId());
        assertEquals(ModelProvider.Assurance.EMPTY, current.getVendorNpuAssurance());
        assertEquals(ModelProvider.LifecycleState.UNAVAILABLE,
                current.getVendorNpuLifecycle());
        assertEquals(ModelProvider.HealthState.UNAVAILABLE, current.getVendorNpuHealth());
        assertEquals("VENDOR_RUNTIME_UNAVAILABLE", current.getVendorNpuDetailCode());
        assertEquals(Arrays.asList(
                        ModelRuntimeReadinessSnapshot.Blocker.PRODUCTION_PROVIDER_MISSING,
                        ModelRuntimeReadinessSnapshot.Blocker.PRODUCTION_ROUTE_MISSING,
                        ModelRuntimeReadinessSnapshot.Blocker.SCHEDULER_NOT_WIRED,
                        ModelRuntimeReadinessSnapshot.Blocker.MODEL_ROUTER_NOT_WIRED,
                        ModelRuntimeReadinessSnapshot.Blocker.VENDOR_NPU_INTERFACE_EMPTY),
                current.getBlockers());
        try {
            current.getBlockers().clear();
            fail("blockers must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test
    public void diagnosticDetailSeparatesAvailabilityFromActivation() {
        String detail = ModelRuntimeReadinessSnapshot.current().diagnosticDetail();
        assertTrue(detail.contains("model_provider_contract_available=true"));
        assertTrue(detail.contains("test_model_router_implementation_available=true"));
        assertTrue(detail.contains("deterministic_stub_implementation_configured=false"));
        assertTrue(detail.contains("deterministic_stub_lifecycle=COLD"));
        assertTrue(detail.contains("deterministic_stub_health=HEALTHY"));
        assertTrue(detail.contains("deterministic_stub_detail_code=STUB_IMPLEMENTATION_NOT_WIRED"));
        assertTrue(detail.contains("production_model_router_dispatch_enabled=false"));
        assertTrue(detail.contains("vendor_npu_provider_available=false"));
        assertTrue(detail.contains("vendor_npu_health=UNAVAILABLE"));
        assertTrue(detail.contains("vendor_npu_detail_code=VENDOR_RUNTIME_UNAVAILABLE"));
        assertTrue(detail.contains("VENDOR_NPU_INTERFACE_EMPTY"));
        assertTrue(detail.contains("service_dispatch_triggered=false"));
        assertTrue(detail.contains("hardware_accessed=false"));
    }
}
