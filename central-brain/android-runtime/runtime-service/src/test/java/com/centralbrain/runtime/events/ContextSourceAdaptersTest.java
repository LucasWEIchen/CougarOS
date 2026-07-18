package com.centralbrain.runtime.events;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalSource;
import com.centralbrain.runtime.vehicle.schema.SignalTimestamp;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Test;

public final class ContextSourceAdaptersTest {
    private static final String EVIDENCE = "a".repeat(64);

    @Test
    public void initialDescriptorCatalogIsFixedAndUnpublished() {
        List<ContextSourceAdapter.Descriptor> descriptors =
                ContextSourceAdapter.initialDescriptors();
        assertEquals(ContextSourceAdapter.INITIAL_SOURCE_COUNT, descriptors.size());
        Set<ContextSourceAdapter.SourceId> ids = new HashSet<>();
        for (ContextSourceAdapter.Descriptor descriptor : descriptors) {
            assertTrue(ids.add(descriptor.getSourceId()));
            assertFalse(descriptor.isProductionPublished());
            assertEquals(64, descriptor.getDescriptorDigest().length());
        }
        assertEquals(Set.of(
                ContextSourceAdapter.SourceId.RUNTIME_HEALTH,
                ContextSourceAdapter.SourceId.SIMULATED_VEHICLE_SIGNAL,
                ContextSourceAdapter.SourceId.TIME), ids);
    }

    @Test
    public void runtimeHealthNormalizesFreshStaleAndUnavailable() {
        RuntimeHealthContextSourceAdapter adapter =
                new RuntimeHealthContextSourceAdapter();
        RuntimeHealthContextSourceAdapter.RuntimeHealthSample healthy =
                new RuntimeHealthContextSourceAdapter.RuntimeHealthSample(
                        RuntimeHealthContextSourceAdapter.HealthState.HEALTHY,
                        1_000,
                        7,
                        EVIDENCE);
        ContextSourceAdapter.AdaptationResult fresh = adapter.adapt(healthy, 1_500);
        ContextSourceAdapter.AdaptationResult stale = adapter.adapt(healthy, 2_001);
        assertEquals(ContextSourceAdapter.ResultCode.ADAPTED, fresh.getCode());
        assertEquals(SignalQuality.VALID, fresh.getObservation().getQuality());
        assertEquals("HEALTHY", fresh.getObservation().getValue().getTextValue());
        assertEquals(ContextSourceAdapter.ResultCode.STALE, stale.getCode());
        assertEquals(SignalQuality.STALE, stale.getObservation().getQuality());
        assertNotEquals(fresh.getObservation().getObservationDigest(),
                stale.getObservation().getObservationDigest());

        RuntimeHealthContextSourceAdapter.RuntimeHealthSample unavailable =
                new RuntimeHealthContextSourceAdapter.RuntimeHealthSample(
                        RuntimeHealthContextSourceAdapter.HealthState.UNAVAILABLE,
                        2_000,
                        8,
                        EVIDENCE);
        ContextSourceAdapter.AdaptationResult missing = adapter.adapt(unavailable, 2_000);
        assertEquals(ContextSourceAdapter.ResultCode.SOURCE_UNAVAILABLE, missing.getCode());
        assertNull(missing.getObservation().getValue());
        assertFalse(missing.isAvailable());
    }

    @Test
    public void simulatedVehicleSignalPreservesTypeAndNormalizesFreshness() {
        SimulatedVehicleSignalContextSourceAdapter adapter =
                new SimulatedVehicleSignalContextSourceAdapter();
        SignalValue temperature = SignalValue.ofDecimal(
                VehicleSignalPath.CABIN_TEMPERATURE,
                18.5,
                "celsius",
                "cabin",
                new SignalTimestamp(1_700_000_000_000L, 1_000),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                3);
        ContextSourceAdapter.AdaptationResult fresh = adapter.adapt(temperature, 2_000);
        ContextSourceAdapter.AdaptationResult stale = adapter.adapt(temperature, 6_001);
        assertEquals(ContextSourceAdapter.ResultCode.ADAPTED, fresh.getCode());
        assertEquals(18.5, fresh.getObservation().getValue().getDecimalValue(), 0.0);
        assertEquals("context.vehicle.cabin-temperature",
                fresh.getObservation().getContextKey());
        assertEquals(ContextSourceAdapter.TrustClass.SIMULATED,
                fresh.getObservation().getTrustClass());
        assertFalse(fresh.getObservation().isProductionTrusted());
        assertEquals(ContextSourceAdapter.ResultCode.STALE, stale.getCode());
        assertEquals(SignalQuality.STALE, stale.getObservation().getQuality());
    }

    @Test
    public void simulatedVehicleSignalRejectsNonSimulatedAndContradictoryQuality() {
        SimulatedVehicleSignalContextSourceAdapter adapter =
                new SimulatedVehicleSignalContextSourceAdapter();
        SignalValue aaos = SignalValue.ofBoolean(
                VehicleSignalPath.PARKING_BRAKE_ENGAGED,
                true,
                "",
                "global",
                new SignalTimestamp(1_700_000_000_000L, 1_000),
                SignalQuality.VALID,
                SignalSource.AAOS,
                1);
        assertEquals(ContextSourceAdapter.ResultCode.REJECTED_PROVENANCE,
                adapter.adapt(aaos, 1_000).getCode());

        SignalValue contradictory = SignalValue.ofBoolean(
                VehicleSignalPath.PARKING_BRAKE_ENGAGED,
                true,
                "",
                "global",
                new SignalTimestamp(1_700_000_000_000L, 1_000),
                SignalQuality.STALE,
                SignalSource.SIMULATED,
                1);
        ContextSourceAdapter.AdaptationResult rejected = adapter.adapt(contradictory, 1_001);
        assertEquals(ContextSourceAdapter.ResultCode.REJECTED_INPUT, rejected.getCode());
        assertNull(rejected.getObservation());

        SignalValue arbitraryGearText = SignalValue.ofText(
                VehicleSignalPath.CURRENT_GEAR,
                "driver message",
                "",
                "global",
                new SignalTimestamp(1_700_000_000_000L, 1_000),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                1);
        assertEquals(ContextSourceAdapter.ResultCode.REJECTED_INPUT,
                adapter.adapt(arbitraryGearText, 1_001).getCode());
    }

    @Test
    public void timeSourceUsesInjectedClockAndRejectsFutureSample() {
        TimeContextSourceAdapter adapter = new TimeContextSourceAdapter();
        long epochAtUtcMidnight = 1_704_067_200_000L;
        TimeContextSourceAdapter.TimeSample sample =
                new TimeContextSourceAdapter.TimeSample(
                        epochAtUtcMidnight,
                        1_000,
                        8 * 60,
                        EVIDENCE);
        ContextSourceAdapter.AdaptationResult result = adapter.adapt(sample, 1_500);
        assertEquals(ContextSourceAdapter.ResultCode.ADAPTED, result.getCode());
        assertEquals(8 * 60, result.getObservation().getValue().getIntegerValue());
        assertEquals("context.time.local-minute-of-day",
                result.getObservation().getContextKey());
        assertEquals(ContextSourceAdapter.ResultCode.REJECTED_FUTURE,
                adapter.adapt(sample, 999).getCode());
    }

    @Test
    public void contractRemainsFailClosedAndDisconnected() {
        ContextSourceAdapter.ContractSnapshot snapshot =
                ContextSourceAdapter.ContractSnapshot.current();
        assertEquals(3, snapshot.getSourceCount());
        assertTrue(snapshot.isAllowlistDefined());
        assertTrue(snapshot.isFreshnessQualityNormalizationDefined());
        assertFalse(snapshot.isProductionRegistryPublished());
        assertFalse(snapshot.isRuntimeWired());
        assertFalse(snapshot.isTriggerEngineWired());
        assertFalse(snapshot.isVehiclePropertyMappingConfigured());
        assertFalse(snapshot.isHardwareAccessed());

        ContextSourceAdapter.AdaptationResult rejected =
                new RuntimeHealthContextSourceAdapter().adapt(null, 1_000);
        assertEquals(ContextSourceAdapter.ResultCode.REJECTED_INPUT, rejected.getCode());
        assertNull(rejected.getObservation());
        assertFalse(rejected.isTriggerInputPublished());
        assertFalse(rejected.isRuntimeWired());
        assertFalse(rejected.isHardwareAccessed());
        assertNotNull(new TimeContextSourceAdapter().descriptor().getDescriptorDigest());
    }
}
