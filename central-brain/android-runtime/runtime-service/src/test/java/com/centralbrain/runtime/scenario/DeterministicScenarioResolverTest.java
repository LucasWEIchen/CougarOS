package com.centralbrain.runtime.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.context.ContextFieldPolicy;
import com.centralbrain.runtime.context.ContextSnapshot;
import com.centralbrain.runtime.context.ContextSnapshot.SeatZone;
import com.centralbrain.runtime.context.ContextSnapshotBuilder;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.MotionState;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.SafetyState;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.SourceAssurance;
import com.centralbrain.runtime.scenario.ScenarioManifest.Source;
import com.centralbrain.runtime.scenario.ScenarioManifest.Zone;
import com.centralbrain.runtime.scenario.ScenarioResolution.Decision;
import com.centralbrain.runtime.scenario.ScenarioResolution.MatchType;
import com.centralbrain.runtime.scenario.ScenarioResolution.ReasonCode;
import com.centralbrain.runtime.scenario.ScenarioResolver.CapabilityProfile;
import com.centralbrain.runtime.scenario.ScenarioResolver.CapabilitySnapshot;
import com.centralbrain.runtime.scenario.ScenarioResolver.Request;
import com.centralbrain.runtime.vehicle.capability.CapabilityCatalog;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability.CapabilityId;
import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalSource;
import com.centralbrain.runtime.vehicle.schema.SignalTimestamp;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;
import com.centralbrain.runtime.vehicle.twin.DigitalTwinSnapshot;
import com.centralbrain.runtime.vehicle.twin.VehicleDigitalTwinStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

public final class DeterministicScenarioResolverTest {
    private static final Path ASSETS = Paths.get(
            "src/main/assets/scenarios").toAbsolutePath().normalize();
    private static final long SOURCE_EPOCH_MS = 1_750_000_000_000L;
    private final ScenarioResolver resolver = new DeterministicScenarioResolver();

    @Test
    public void explicitButtonIdWinsAndProducesStableNonExecutableResolution() throws Exception {
        ScenarioCatalog catalog = catalog();
        ContextSnapshot context = context(ContextFieldPolicy.seatComfort(), false, false);
        CapabilitySnapshot capabilities = capabilities(Set.of());
        Request request = new Request(
                "scene.comfort.cold.v1",
                "unsupported free text is ignored for an explicit ID",
                Source.HMI_BUTTON,
                Zone.ROW1_DRIVER);

        ScenarioResolution first = resolver.resolve(request, catalog, context, capabilities);
        ScenarioResolution second = resolver.resolve(request, catalog, context, capabilities);

        assertEquals(Decision.ACCEPTED, first.getDecision());
        assertEquals(MatchType.EXPLICIT_ID, first.getMatchType());
        assertEquals("scene.comfort.cold.v1", first.getScenarioId());
        assertEquals("explicit.v1", first.getMatchedRuleId());
        assertTrue(first.getReasons().contains(ReasonCode.SELECTED_EXPLICIT_ID));
        assertTrue(first.getSelectedManifest().isPresent());
        assertEquals(first.getResolutionDigest(), second.getResolutionDigest());
        assertTrue(first.getResolutionDigest().matches("[0-9a-f]{64}"));
        assertFalse(first.isExecutable());
        assertFalse(first.isProductionTrusted());
    }

    @Test
    public void deterministicTextRulesCoverColdFatigueAndRest() throws Exception {
        ScenarioCatalog catalog = catalog();
        CapabilitySnapshot capabilities = capabilities(Set.of());

        ScenarioResolution cold = resolver.resolve(
                text("我有点冷。", Zone.ROW1_DRIVER),
                catalog,
                context(ContextFieldPolicy.seatComfort(), false, false),
                capabilities);
        ScenarioResolution fatigue = resolver.resolve(
                text("我有些疲惫", Zone.ROW1_DRIVER),
                catalog,
                context(ContextFieldPolicy.seatRecline(), false, false),
                capabilities);
        ScenarioResolution rest = resolver.resolve(
                text("take   a rest!", Zone.ROW1_DRIVER),
                catalog,
                context(ContextFieldPolicy.seatRecline(), false, false),
                capabilities);

        assertEquals("scene.comfort.cold.v1", cold.getScenarioId());
        assertEquals("scene.fatigue.assist.v1", fatigue.getScenarioId());
        assertEquals("scene.rest.nap.v1", rest.getScenarioId());
        assertEquals(Decision.ACCEPTED, cold.getDecision());
        assertEquals(Decision.ACCEPTED, fatigue.getDecision());
        assertEquals(Decision.ACCEPTED, rest.getDecision());
        assertEquals(MatchType.DETERMINISTIC_TEXT, fatigue.getMatchType());
        assertEquals("intent.fatigue.v1", fatigue.getMatchedRuleId());
    }

    @Test
    public void unknownAndAmbiguousTextFailClosedWithoutSelectingManifest() throws Exception {
        ScenarioCatalog catalog = catalog();
        ContextSnapshot context = context(ContextFieldPolicy.seatComfort(), false, false);
        CapabilitySnapshot capabilities = capabilities(Set.of());

        ScenarioResolution unknown = resolver.resolve(
                text("打开天窗", Zone.ROW1_DRIVER), catalog, context, capabilities);
        ScenarioResolution ambiguous = resolver.resolve(
                text("我冷了 / 我累了", Zone.ROW1_DRIVER), catalog, context, capabilities);

        assertEquals(Decision.REJECTED, unknown.getDecision());
        assertEquals(ReasonCode.UNKNOWN_INTENT, unknown.getReasons().get(0));
        assertTrue(unknown.getSelectedManifest().isEmpty());
        assertEquals(Decision.REJECTED, ambiguous.getDecision());
        assertTrue(ambiguous.getReasons().contains(ReasonCode.AMBIGUOUS_INTENT));
        assertEquals(2, ambiguous.getCandidateScenarioIds().size());
        assertTrue(ambiguous.getSelectedManifest().isEmpty());
    }

    @Test
    public void sourceZonePolicyAndRestrictedContextAreRejected() throws Exception {
        ScenarioCatalog catalog = catalog();
        CapabilitySnapshot capabilities = capabilities(Set.of());
        ContextSnapshot driverComfort = context(
                ContextFieldPolicy.seatComfort(), false, false);

        ScenarioResolution source = resolver.resolve(
                new Request("scene.comfort.cold.v1", "", Source.TRIGGER, Zone.ROW1_DRIVER),
                catalog,
                driverComfort,
                capabilities);
        ScenarioResolution zone = resolver.resolve(
                new Request("scene.fatigue.assist.v1", "", Source.HMI_BUTTON,
                        Zone.ROW1_PASSENGER),
                catalog,
                driverComfort,
                capabilities);
        ScenarioResolution policy = resolver.resolve(
                new Request("scene.rest.nap.v1", "", Source.HMI_BUTTON, Zone.ROW1_DRIVER),
                catalog,
                driverComfort,
                capabilities);
        ContextSnapshot restricted = context(ContextFieldPolicy.seatComfort(), false, true);
        ScenarioResolution missing = resolver.resolve(
                new Request("scene.comfort.cold.v1", "", Source.HMI_BUTTON,
                        Zone.ROW1_DRIVER),
                catalog,
                restricted,
                capabilities);

        assertTrue(source.getReasons().contains(ReasonCode.SOURCE_UNSUPPORTED));
        assertTrue(zone.getReasons().contains(ReasonCode.ZONE_UNSUPPORTED));
        assertTrue(zone.getReasons().contains(ReasonCode.CONTEXT_ZONE_MISMATCH));
        assertTrue(policy.getReasons().contains(ReasonCode.CONTEXT_POLICY_MISMATCH));
        assertTrue(missing.getReasons().contains(ReasonCode.CONTEXT_RESTRICTED));
        assertTrue(missing.getReasons().contains(ReasonCode.REQUIRED_CONTEXT_UNAVAILABLE));
        assertEquals(Decision.REJECTED, missing.getDecision());
    }

    @Test
    public void requiredCapabilityRejectsWhileOptionalCapabilityDegrades() throws Exception {
        ScenarioCatalog catalog = catalog();
        ContextSnapshot context = context(ContextFieldPolicy.seatComfort(), false, false);
        Request request = new Request(
                "scene.comfort.cold.v1", "", Source.HMI_BUTTON, Zone.ROW1_DRIVER);

        ScenarioResolution required = resolver.resolve(
                request,
                catalog,
                context,
                capabilities(Set.of(CapabilityId.HVAC_POWER)));
        ScenarioResolution optional = resolver.resolve(
                request,
                catalog,
                context,
                capabilities(Set.of(CapabilityId.SEAT_HEATING_LEVEL)));

        assertEquals(Decision.REJECTED, required.getDecision());
        assertTrue(required.getReasons().contains(
                ReasonCode.REQUIRED_CAPABILITY_UNAVAILABLE));
        assertEquals(Set.of(CapabilityId.HVAC_POWER),
                new LinkedHashSet<>(required.getUnavailableRequiredCapabilities()));
        assertEquals(Decision.DEGRADED, optional.getDecision());
        assertTrue(optional.getReasons().contains(
                ReasonCode.OPTIONAL_CAPABILITY_UNAVAILABLE));
        assertEquals(Set.of(CapabilityId.SEAT_HEATING_LEVEL),
                new LinkedHashSet<>(optional.getUnavailableOptionalCapabilities()));
    }

    @Test
    public void movingFatigueDegradesButMovingRestRejectsParkedOnlyCapability() throws Exception {
        ScenarioCatalog catalog = catalog();
        ContextSnapshot moving = context(ContextFieldPolicy.seatRecline(), true, false);
        CapabilitySnapshot capabilities = capabilities(Set.of());

        ScenarioResolution fatigue = resolver.resolve(
                text("我累了", Zone.ROW1_DRIVER), catalog, moving, capabilities);
        ScenarioResolution rest = resolver.resolve(
                text("休息模式", Zone.ROW1_DRIVER), catalog, moving, capabilities);

        assertEquals(Decision.DEGRADED, fatigue.getDecision());
        assertTrue(fatigue.getReasons().contains(
                ReasonCode.OPTIONAL_CAPABILITY_POLICY_BLOCKED));
        assertTrue(fatigue.getUnavailableOptionalCapabilities().contains(
                CapabilityId.SEAT_RECLINE_ANGLE));
        assertEquals(Decision.REJECTED, rest.getDecision());
        assertTrue(rest.getReasons().contains(
                ReasonCode.REQUIRED_CAPABILITY_POLICY_BLOCKED));
        assertTrue(rest.getUnavailableRequiredCapabilities().contains(
                CapabilityId.SEAT_RECLINE_ANGLE));
    }

    @Test
    public void productionProfileAndMalformedRequestFailClosed() throws Exception {
        ScenarioCatalog catalog = catalog();
        ContextSnapshot context = context(ContextFieldPolicy.seatComfort(), false, false);
        CapabilitySnapshot production = CapabilitySnapshot.capture(
                CapabilityCatalog.stage2Defaults(),
                CapabilityProfile.PRODUCTION,
                1,
                Set.of());
        Request request = new Request(
                "scene.comfort.cold.v1", "", Source.API, Zone.ROW1_DRIVER);

        ScenarioResolution rejected = resolver.resolve(
                request, catalog, context, production);
        assertEquals(Decision.REJECTED, rejected.getDecision());
        assertTrue(rejected.getReasons().contains(
                ReasonCode.CONTEXT_NOT_PRODUCTION_TRUSTED));
        assertTrue(rejected.getReasons().contains(
                ReasonCode.REQUIRED_CAPABILITY_UNAVAILABLE));
        assertFalse(production.isProductionTrusted());
        assertThrows(UnsupportedOperationException.class,
                () -> production.getAvailableCapabilities().clear());
        assertThrows(IllegalArgumentException.class,
                () -> new Request("", "", Source.API, Zone.CABIN));
        assertThrows(IllegalArgumentException.class,
                () -> new Request("invalid", "", Source.API, Zone.CABIN));
        assertThrows(IllegalArgumentException.class,
                () -> new Request("", "bad\nintent", Source.API, Zone.CABIN));
        assertThrows(IllegalArgumentException.class,
                () -> new Request("", "bad intent\n", Source.API, Zone.CABIN));
        assertNotEquals(request.getDigest(),
                new Request("scene.comfort.cold.v1", "", Source.VOICE,
                        Zone.ROW1_DRIVER).getDigest());
    }

    private static Request text(String text, Zone zone) {
        return new Request("", text, Source.VOICE, zone);
    }

    private static CapabilitySnapshot capabilities(Set<CapabilityId> unavailable) {
        return CapabilitySnapshot.capture(
                CapabilityCatalog.stage2Defaults(),
                CapabilityProfile.SOFTWARE_SIMULATION,
                1,
                unavailable);
    }

    private static ScenarioCatalog catalog() throws Exception {
        Map<String, byte[]> assets = new LinkedHashMap<>();
        assets.put("scene.comfort.cold.v1.json", Files.readAllBytes(
                ASSETS.resolve("scene.comfort.cold.v1.json")));
        assets.put("scene.fatigue.assist.v1.json", Files.readAllBytes(
                ASSETS.resolve("scene.fatigue.assist.v1.json")));
        assets.put("scene.rest.nap.v1.json", Files.readAllBytes(
                ASSETS.resolve("scene.rest.nap.v1.json")));
        return ScenarioCatalog.load(assets);
    }

    private static ContextSnapshot context(
            ContextFieldPolicy policy,
            boolean moving,
            boolean omitSpeed) {
        long now = moving ? 2_000 : 1_000;
        VehicleDigitalTwinStore store = new VehicleDigitalTwinStore();
        if (!omitSpeed) {
            store.updateReported(decimal(
                    VehicleSignalPath.VEHICLE_SPEED,
                    moving ? 35 : 0,
                    "km/h",
                    "global",
                    now), now);
        }
        store.updateReported(text(
                VehicleSignalPath.CURRENT_GEAR,
                moving ? "D" : "P",
                "global",
                now), now);
        store.updateReported(bool(
                VehicleSignalPath.PARKING_BRAKE_ENGAGED,
                !moving,
                "global",
                now), now);
        store.updateReported(bool(
                VehicleSignalPath.SEAT_OCCUPIED,
                true,
                "row1.driver",
                now), now);
        store.updateReported(bool(
                VehicleSignalPath.SEAT_BELTED,
                true,
                "row1.driver",
                now), now);
        store.updateReported(decimal(
                VehicleSignalPath.SEAT_RECLINE_ANGLE,
                15,
                "degree",
                "row1.driver",
                now), now);
        store.updateReported(bool(
                VehicleSignalPath.HVAC_ACTIVE,
                true,
                "cabin",
                now), now);
        store.updateReported(decimal(
                VehicleSignalPath.CABIN_TEMPERATURE,
                22,
                "celsius",
                "cabin",
                now), now);

        Set<VehicleSignalPath> paths = new LinkedHashSet<>();
        for (ContextFieldPolicy.FieldRequirement requirement : policy.getRequirements()) {
            paths.add(requirement.getPath());
        }
        DigitalTwinSnapshot twin = store.snapshot(paths, now);
        SafetyVehicleStateSnapshot runtime = new SafetyVehicleStateSnapshot(
                "scenario-resolver-test",
                1,
                now,
                SafetyState.NORMAL,
                moving ? MotionState.MOVING : MotionState.PARKED,
                true,
                SourceAssurance.RUNTIME_OWNED_STUB,
                false);
        return new ContextSnapshotBuilder().build(
                twin,
                runtime,
                policy,
                SeatZone.ROW1_DRIVER,
                false);
    }

    private static SignalValue decimal(
            VehicleSignalPath path,
            double value,
            String unit,
            String area,
            long now) {
        return SignalValue.ofDecimal(
                path,
                value,
                unit,
                area,
                timestamp(now),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                1);
    }

    private static SignalValue text(
            VehicleSignalPath path,
            String value,
            String area,
            long now) {
        return SignalValue.ofText(
                path,
                value,
                "",
                area,
                timestamp(now),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                1);
    }

    private static SignalValue bool(
            VehicleSignalPath path,
            boolean value,
            String area,
            long now) {
        return SignalValue.ofBoolean(
                path,
                value,
                "",
                area,
                timestamp(now),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                1);
    }

    private static SignalTimestamp timestamp(long now) {
        return new SignalTimestamp(SOURCE_EPOCH_MS + now, now);
    }
}
