package com.centralbrain.runtime.model;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public final class ModelProviderContractTest {
    @Test
    public void deterministicStubProfileIsTestOnlyAndNotRoutableYet() {
        ModelProviderProfiles.Profile profile = ModelProviderProfiles.deterministicStub();
        ModelProvider.Descriptor descriptor = profile.getDescriptor();

        assertTrue(descriptor.isSupportsWarmup());
        assertTrue(descriptor.isSupportsInference());
        assertTrue(descriptor.isSupportsStreaming());
        assertTrue(descriptor.isSupportsCancellation());
        assertTrue(descriptor.isSupportsMetrics());
        assertTrue(descriptor.isSupportsFaultReporting());
        assertTrue(descriptor.getMaxConcurrentRequests() == 1);
        assertTrue(descriptor.getAssurance() == ModelProvider.Assurance.TEST_ONLY);
        assertFalse(descriptor.isHardwareBacked());
        assertFalse(descriptor.isProductionEligible());
        assertFalse(profile.isImplementationConfigured());
        assertFalse(profile.isRoutingEnabled());
        assertTrue(profile.getSnapshot().getLifecycleState()
                == ModelProvider.LifecycleState.COLD);
        assertFalse(profile.getSnapshot().isHardwareAccessed());
    }

    @Test
    public void vendorNpuEmptyProfileCannotInferOrAccessHardware() {
        ModelProviderProfiles.Profile profile = ModelProviderProfiles.vendorNpuEmpty();
        ModelProvider.Descriptor descriptor = profile.getDescriptor();

        assertTrue(descriptor.getAssurance() == ModelProvider.Assurance.EMPTY);
        assertFalse(descriptor.isSupportsWarmup());
        assertFalse(descriptor.isSupportsInference());
        assertFalse(descriptor.isSupportsStreaming());
        assertFalse(descriptor.isSupportsCancellation());
        assertFalse(descriptor.isSupportsMetrics());
        assertTrue(descriptor.isSupportsFaultReporting());
        assertTrue(descriptor.getMaxConcurrentRequests() == 0);
        assertTrue(profile.getSnapshot().getLifecycleState()
                == ModelProvider.LifecycleState.UNAVAILABLE);
        assertTrue(profile.getSnapshot().getHealthState()
                == ModelProvider.HealthState.UNAVAILABLE);
        assertFalse(profile.isImplementationConfigured());
        assertFalse(profile.isRoutingEnabled());
        assertFalse(descriptor.isHardwareBacked());
        assertFalse(profile.getSnapshot().isHardwareAccessed());
    }

    @Test
    public void androidLocalProfileIsDevelopmentOnlyAndNotConfiguredByDefault() {
        ModelProviderProfiles.Profile profile =
                ModelProviderProfiles.androidLocalDevelopment();
        ModelProvider.Descriptor descriptor = profile.getDescriptor();

        assertTrue(descriptor.getBackendKind()
                == ModelProvider.BackendKind.ANDROID_LOCAL_DEVELOPMENT);
        assertTrue(descriptor.getAssurance() == ModelProvider.Assurance.DEBUG_ONLY);
        assertTrue(descriptor.getFallbackClass() == ModelProvider.FallbackClass.NEVER);
        assertTrue(descriptor.isSupportsInference());
        assertTrue(descriptor.isSupportsStreaming());
        assertTrue(descriptor.isSupportsCancellation());
        assertFalse(descriptor.isHardwareBacked());
        assertFalse(descriptor.isProductionEligible());
        assertFalse(profile.isImplementationConfigured());
        assertFalse(profile.isRoutingEnabled());
        assertFalse(profile.getSnapshot().isHardwareAccessed());
    }

    @Test
    public void unsafeStubAndEmptyDescriptorsAreRejected() {
        expectInvalid(() -> new ModelProvider.Descriptor(
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
                1));
        expectInvalid(() -> new ModelProvider.Descriptor(
                "unsafe.empty",
                ModelProvider.BackendKind.VENDOR_NPU,
                ModelProvider.Assurance.EMPTY,
                ModelProvider.FallbackClass.NEVER,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                true,
                1));
        expectInvalid(() -> new ModelProvider.Descriptor(
                "unsafe.local",
                ModelProvider.BackendKind.ANDROID_LOCAL_DEVELOPMENT,
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
                1));
    }

    @Test
    public void streamChunksAreBoundedAndDefensivelyCopied() {
        byte[] source = "stub-output".getBytes(StandardCharsets.UTF_8);
        ModelProvider.StreamChunk chunk = new ModelProvider.StreamChunk(
                "request-1",
                1,
                source);
        source[0] = 0;
        byte[] first = chunk.getContent();
        first[0] = 0;
        assertArrayEquals(
                "stub-output".getBytes(StandardCharsets.UTF_8),
                chunk.getContent());
    }

    private static void expectInvalid(Runnable operation) {
        try {
            operation.run();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected fail-closed contract rejection.
        }
    }
}
