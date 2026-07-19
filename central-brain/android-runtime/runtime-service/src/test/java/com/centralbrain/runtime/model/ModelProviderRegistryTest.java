package com.centralbrain.runtime.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

public final class ModelProviderRegistryTest {
    private static final String EVIDENCE_A = digest('a');
    private static final String EVIDENCE_B = digest('b');

    @Test
    public void fixedCatalogIsSortedBoundedAndDigestStable() {
        ModelProviderRegistry.RegistrySnapshot first = registry().snapshot(1_000);
        ModelProviderRegistry.RegistrySnapshot second = registry().snapshot(9_000);
        List<String> providerIds = new ArrayList<>();
        for (ModelProviderRegistry.ProviderView provider : first.getProviders()) {
            providerIds.add(provider.getDescriptor().getProviderId());
        }

        assertEquals(1, first.getSchemaVersion());
        assertEquals(ModelProviderRegistry.PROVIDER_COUNT, first.getProviders().size());
        assertEquals(Arrays.asList(
                ModelProviderRegistry.ANDROID_LOCAL_DEVELOPMENT_ID,
                ModelProviderRegistry.CLOUD_PLACEHOLDER_ID,
                ModelProviderRegistry.DETERMINISTIC_TEST_ID,
                ModelProviderRegistry.VENDOR_NPU_PLACEHOLDER_ID), providerIds);
        assertEquals(first.getCatalogDigest(), second.getCatalogDigest());
        ModelProviderRegistry.ProviderView local = first.getProviders().get(0);
        ModelProviderRegistry.ProviderView cloud = first.getProviders().get(1);
        ModelProviderRegistry.ProviderView deterministic = first.getProviders().get(2);
        ModelProviderRegistry.ProviderView vendor = first.getProviders().get(3);
        assertEquals(ModelProviderRegistry.HealthSource.LOCAL_DEVELOPMENT_RUNTIME,
                local.getDescriptor().getHealthSource());
        assertEquals(ModelProviderRegistry.HealthSource.CLOUD_CONTROL_PLANE,
                cloud.getDescriptor().getHealthSource());
        assertEquals(ModelProviderRegistry.HealthSource.CONTRACT_TEST,
                deterministic.getDescriptor().getHealthSource());
        assertEquals(ModelProviderRegistry.HealthSource.VENDOR_RUNTIME,
                vendor.getDescriptor().getHealthSource());
        assertTrue(deterministic.getDescriptor().supports(
                ModelContractV2.RequiredCapability.TEXT_GENERATION));
        assertFalse(deterministic.getDescriptor().supports(
                ModelContractV2.RequiredCapability.STRUCTURED_SCENARIO_CANDIDATE));
        assertTrue(vendor.getDescriptor().supports(
                ModelContractV2.RequiredCapability.STRUCTURED_SCENARIO_CANDIDATE));
        assertTrue(local.getDescriptor().isNetworkRequired());
        assertTrue(first.getProviders().get(1).getDescriptor().isNetworkRequired());
        assertTrue(first.getProviders().get(3).getDescriptor().isHardwareExpected());
        try {
            deterministic.getDescriptor().getCapabilities().clear();
            fail("capability set must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected immutable descriptor.
        }
        try {
            first.getProviders().clear();
            fail("provider views must be immutable");
        } catch (UnsupportedOperationException expected) {
            // Expected immutable snapshot.
        }
    }

    @Test
    public void testAvailabilityIsSeparateFromDevelopmentAndProductionReadiness() {
        ModelProviderRegistry.RegistrySnapshot snapshot = registry().snapshot(1_000);

        assertEquals(1, snapshot.getContractTestAvailableCount());
        assertEquals(1, snapshot.getDevelopmentAvailableCount());
        assertEquals(0, snapshot.getProductionReadyCount());
        ModelProviderRegistry.ProviderView testProvider = find(
                snapshot,
                ModelProviderRegistry.DETERMINISTIC_TEST_ID);
        assertTrue(testProvider.isContractTestAvailable());
        assertFalse(testProvider.isDevelopmentAvailable());
        assertFalse(testProvider.isProductionReady());
        ModelProviderRegistry.ProviderView localProvider = find(
                snapshot,
                ModelProviderRegistry.ANDROID_LOCAL_DEVELOPMENT_ID);
        assertFalse(localProvider.isContractTestAvailable());
        assertTrue(localProvider.isDevelopmentAvailable());
        assertFalse(localProvider.getDescriptor().isProductionImplementationAvailable());
        assertFalse(localProvider.getDescriptor().isProductionEligible());
        assertFalse(localProvider.isProductionReady());
        assertFalse(localProvider.isRoutingEnabled());
        assertEquals(ModelProviderRegistry.HealthFreshness.MISSING,
                testProvider.getHealthFreshness());
    }

    @Test
    public void healthPublicationRequiresKnownProviderSourceAndFreshWindow() {
        ModelProviderRegistry registry = registry();
        assertEquals(ModelProviderRegistry.PublishCode.UNKNOWN_PROVIDER,
                registry.publishHealth(report(
                        "unknown.provider",
                        ModelProviderRegistry.HealthSource.CONTRACT_TEST,
                        1,
                        900,
                        1_500,
                        EVIDENCE_A), 1_000).getCode());
        assertEquals(ModelProviderRegistry.PublishCode.SOURCE_MISMATCH,
                registry.publishHealth(report(
                        ModelProviderRegistry.DETERMINISTIC_TEST_ID,
                        ModelProviderRegistry.HealthSource.VENDOR_RUNTIME,
                        1,
                        900,
                        1_500,
                        EVIDENCE_A), 1_000).getCode());
        assertEquals(ModelProviderRegistry.PublishCode.REJECTED_FUTURE,
                registry.publishHealth(report(
                        ModelProviderRegistry.DETERMINISTIC_TEST_ID,
                        ModelProviderRegistry.HealthSource.CONTRACT_TEST,
                        1,
                        1_001,
                        1_500,
                        EVIDENCE_A), 1_000).getCode());
        assertEquals(ModelProviderRegistry.PublishCode.REJECTED_EXPIRED,
                registry.publishHealth(report(
                        ModelProviderRegistry.DETERMINISTIC_TEST_ID,
                        ModelProviderRegistry.HealthSource.CONTRACT_TEST,
                        1,
                        800,
                        1_000,
                        EVIDENCE_A), 1_000).getCode());
    }

    @Test
    public void healthReplayConflictRevisionAndStalenessFailClosed() {
        ModelProviderRegistry registry = registry();
        ModelProviderRegistry.HealthReport initial = report(
                ModelProviderRegistry.DETERMINISTIC_TEST_ID,
                ModelProviderRegistry.HealthSource.CONTRACT_TEST,
                1,
                900,
                1_500,
                EVIDENCE_A);
        assertEquals(ModelProviderRegistry.PublishCode.UPDATED,
                registry.publishHealth(initial, 1_000).getCode());
        assertEquals(ModelProviderRegistry.PublishCode.REPLAYED,
                registry.publishHealth(initial, 1_000).getCode());
        assertEquals(ModelProviderRegistry.PublishCode.REVISION_CONFLICT,
                registry.publishHealth(report(
                        ModelProviderRegistry.DETERMINISTIC_TEST_ID,
                        ModelProviderRegistry.HealthSource.CONTRACT_TEST,
                        1,
                        900,
                        1_500,
                        EVIDENCE_B), 1_000).getCode());
        assertEquals(ModelProviderRegistry.PublishCode.UPDATED,
                registry.publishHealth(report(
                        ModelProviderRegistry.DETERMINISTIC_TEST_ID,
                        ModelProviderRegistry.HealthSource.CONTRACT_TEST,
                        2,
                        1_100,
                        1_600,
                        EVIDENCE_B), 1_200).getCode());
        assertEquals(ModelProviderRegistry.PublishCode.REJECTED_REVISION,
                registry.publishHealth(initial, 1_200).getCode());

        ModelProviderRegistry.ProviderView fresh = find(
                registry.snapshot(1_200),
                ModelProviderRegistry.DETERMINISTIC_TEST_ID);
        ModelProviderRegistry.ProviderView stale = find(
                registry.snapshot(1_600),
                ModelProviderRegistry.DETERMINISTIC_TEST_ID);
        assertEquals(ModelProviderRegistry.HealthState.HEALTHY, fresh.getHealthState());
        assertEquals(ModelProviderRegistry.HealthFreshness.FRESH,
                fresh.getHealthFreshness());
        assertEquals(ModelProviderRegistry.HealthState.UNKNOWN, stale.getHealthState());
        assertEquals(ModelProviderRegistry.HealthFreshness.STALE,
                stale.getHealthFreshness());
    }

    @Test
    public void healthyPlaceholderNeverBecomesAvailableReadyOrRoutable() {
        ModelProviderRegistry registry = registry();
        assertTrue(registry.publishHealth(report(
                ModelProviderRegistry.VENDOR_NPU_PLACEHOLDER_ID,
                ModelProviderRegistry.HealthSource.VENDOR_RUNTIME,
                1,
                900,
                1_500,
                EVIDENCE_A), 1_000).isUpdated());

        ModelProviderRegistry.ProviderView vendor = find(
                registry.snapshot(1_000),
                ModelProviderRegistry.VENDOR_NPU_PLACEHOLDER_ID);
        assertEquals(ModelProviderRegistry.HealthState.HEALTHY, vendor.getHealthState());
        assertFalse(vendor.isContractTestAvailable());
        assertFalse(vendor.isDevelopmentAvailable());
        assertFalse(vendor.getDescriptor().isProductionImplementationAvailable());
        assertFalse(vendor.getDescriptor().isProductionEligible());
        assertFalse(vendor.isProductionReady());
        assertFalse(vendor.isRoutingEnabled());
    }

    @Test
    public void runtimeModelNetworkNpuAndHardwareBoundariesRemainClosed() {
        ModelProviderRegistry.RegistrySnapshot snapshot = registry().snapshot(1_000);

        assertFalse(snapshot.isProductionRoutingEnabled());
        assertFalse(snapshot.isModelInvoked());
        assertFalse(snapshot.isNetworkAccessed());
        assertFalse(snapshot.isNpuAccessed());
        assertFalse(snapshot.isHardwareAccessed());
        assertNotEquals(ModelProviderRegistry.DETERMINISTIC_TEST_ID,
                ModelProviderRegistry.ANDROID_LOCAL_DEVELOPMENT_ID);
    }

    private static ModelProviderRegistry registry() {
        return ModelProviderRegistry.createForContractTest();
    }

    private static ModelProviderRegistry.HealthReport report(
            String providerId,
            ModelProviderRegistry.HealthSource source,
            long revision,
            long observedAt,
            long validUntil,
            String evidenceDigest) {
        return new ModelProviderRegistry.HealthReport(
                providerId,
                source,
                ModelProviderRegistry.HealthState.HEALTHY,
                revision,
                observedAt,
                validUntil,
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
        throw new AssertionError("provider not found: " + providerId);
    }

    private static String digest(char value) {
        StringBuilder builder = new StringBuilder(64);
        for (int index = 0; index < 64; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
