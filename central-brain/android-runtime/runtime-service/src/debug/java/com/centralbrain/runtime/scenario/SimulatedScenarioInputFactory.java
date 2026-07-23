package com.centralbrain.runtime.scenario;

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
import com.centralbrain.runtime.scenario.ScenarioPlanCompiler.CompileRequest;
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
import com.centralbrain.sdk.plan.PlanContract;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Creates fixed, build-owned debug inputs. It accepts no arbitrary text or vehicle scalar. */
public final class SimulatedScenarioInputFactory {
    public enum ScenarioKind {
        COLD("scene.comfort.cold.v1"),
        FATIGUE("scene.fatigue.assist.v1"),
        CABIN_MULTIMODAL("scene.cabin.multimodal.assist.v1");

        private final String scenarioId;

        ScenarioKind(String scenarioId) {
            this.scenarioId = scenarioId;
        }

        String getScenarioId() {
            return scenarioId;
        }
    }

    public enum DrivingProfile {
        PARKED,
        MOVING
    }

    public static final class Input {
        private final CompileRequest request;
        private final ScenarioResolution resolution;
        private final ContextSnapshot context;
        private final CapabilitySnapshot capabilities;

        private Input(
                CompileRequest request,
                ScenarioResolution resolution,
                ContextSnapshot context,
                CapabilitySnapshot capabilities) {
            this.request = request;
            this.resolution = resolution;
            this.context = context;
            this.capabilities = capabilities;
        }

        public CompileRequest getRequest() {
            return request;
        }

        public ScenarioResolution getResolution() {
            return resolution;
        }

        public ContextSnapshot getContext() {
            return context;
        }

        public CapabilitySnapshot getCapabilities() {
            return capabilities;
        }
    }

    private static final long DEADLINE_MS = 120_000L;

    private final ScenarioCatalog catalog;
    private final LongSupplier epochMs;
    private final LongSupplier elapsedRealtimeMs;
    private final Supplier<String> uuidSupplier;

    public SimulatedScenarioInputFactory(
            ScenarioCatalog catalog,
            LongSupplier epochMs,
            LongSupplier elapsedRealtimeMs,
            Supplier<String> uuidSupplier) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
        this.epochMs = Objects.requireNonNull(epochMs, "epochMs");
        this.elapsedRealtimeMs = Objects.requireNonNull(
                elapsedRealtimeMs, "elapsedRealtimeMs");
        this.uuidSupplier = Objects.requireNonNull(uuidSupplier, "uuidSupplier");
        catalog.require(ScenarioKind.COLD.scenarioId);
        catalog.require(ScenarioKind.FATIGUE.scenarioId);
    }

    public Input create(ScenarioKind scenario, DrivingProfile driving) {
        return createForSession(scenario, driving, nextUuid());
    }

    public Input createForSession(
            ScenarioKind scenario,
            DrivingProfile driving,
            String sessionId) {
        long nowEpochMs = epochMs.getAsLong();
        return createForSession(
                scenario,
                driving,
                sessionId,
                nowEpochMs,
                nowEpochMs + DEADLINE_MS,
                Set.of());
    }

    public Input createForSession(
            ScenarioKind scenario,
            DrivingProfile driving,
            String sessionId,
            long deadlineEpochMs) {
        return createForSession(
                scenario,
                driving,
                sessionId,
                epochMs.getAsLong(),
                deadlineEpochMs,
                Set.of());
    }

    public Input createForSession(
            ScenarioKind scenario,
            DrivingProfile driving,
            String sessionId,
            long deadlineEpochMs,
            Set<CapabilityId> runtimeUnavailable) {
        return createForSession(
                scenario,
                driving,
                sessionId,
                epochMs.getAsLong(),
                deadlineEpochMs,
                runtimeUnavailable);
    }

    private Input createForSession(
            ScenarioKind scenario,
            DrivingProfile driving,
            String sessionId,
            long nowEpochMs,
            long deadlineEpochMs,
            Set<CapabilityId> runtimeUnavailable) {
        ScenarioKind requiredScenario = Objects.requireNonNull(scenario, "scenario");
        DrivingProfile requiredDriving = Objects.requireNonNull(driving, "driving");
        String canonicalSessionId = canonicalUuid(sessionId, "sessionId");
        long nowElapsedMs = elapsedRealtimeMs.getAsLong();
        if (nowEpochMs <= 0
                || nowElapsedMs < 0
                || deadlineEpochMs <= nowEpochMs
                || deadlineEpochMs - nowEpochMs > PlanContract.MAX_PLAN_DEADLINE_MS) {
            throw violation("clock is invalid");
        }
        ContextFieldPolicy policy = requiredScenario != ScenarioKind.FATIGUE
                ? ContextFieldPolicy.seatComfort() : ContextFieldPolicy.seatRecline();
        ContextSnapshot context = context(policy, requiredDriving, nowEpochMs, nowElapsedMs);
        CapabilitySnapshot capabilities = CapabilitySnapshot.capture(
                CapabilityCatalog.stage2Defaults(),
                CapabilityProfile.SOFTWARE_SIMULATION,
                1,
                Objects.requireNonNull(runtimeUnavailable, "runtimeUnavailable"));
        ScenarioResolution resolution = new DeterministicScenarioResolver().resolve(
                new Request(
                        requiredScenario.scenarioId,
                        "",
                        Source.HMI_BUTTON,
                        Zone.ROW1_DRIVER),
                catalog,
                context,
                capabilities);
        CompileRequest request = new CompileRequest(
                nextUuid(),
                canonicalSessionId,
                1,
                nowEpochMs,
                deadlineEpochMs);
        return new Input(request, resolution, context, capabilities);
    }

    private static ContextSnapshot context(
            ContextFieldPolicy policy,
            DrivingProfile driving,
            long nowEpochMs,
            long nowElapsedMs) {
        boolean moving = driving == DrivingProfile.MOVING;
        VehicleDigitalTwinStore store = new VehicleDigitalTwinStore();
        store.updateReported(decimal(
                VehicleSignalPath.VEHICLE_SPEED,
                moving ? 35 : 0,
                "km/h",
                "global",
                nowEpochMs,
                nowElapsedMs), nowElapsedMs);
        store.updateReported(text(
                VehicleSignalPath.CURRENT_GEAR,
                moving ? "D" : "P",
                "global",
                nowEpochMs,
                nowElapsedMs), nowElapsedMs);
        store.updateReported(bool(
                VehicleSignalPath.PARKING_BRAKE_ENGAGED,
                !moving,
                "global",
                nowEpochMs,
                nowElapsedMs), nowElapsedMs);
        store.updateReported(bool(
                VehicleSignalPath.SEAT_OCCUPIED,
                true,
                "row1.driver",
                nowEpochMs,
                nowElapsedMs), nowElapsedMs);
        store.updateReported(bool(
                VehicleSignalPath.SEAT_BELTED,
                true,
                "row1.driver",
                nowEpochMs,
                nowElapsedMs), nowElapsedMs);
        store.updateReported(decimal(
                VehicleSignalPath.SEAT_RECLINE_ANGLE,
                15,
                "degree",
                "row1.driver",
                nowEpochMs,
                nowElapsedMs), nowElapsedMs);
        store.updateReported(bool(
                VehicleSignalPath.HVAC_ACTIVE,
                true,
                "cabin",
                nowEpochMs,
                nowElapsedMs), nowElapsedMs);
        store.updateReported(decimal(
                VehicleSignalPath.CABIN_TEMPERATURE,
                22,
                "celsius",
                "cabin",
                nowEpochMs,
                nowElapsedMs), nowElapsedMs);

        Set<VehicleSignalPath> paths = new LinkedHashSet<>();
        for (ContextFieldPolicy.FieldRequirement requirement : policy.getRequirements()) {
            paths.add(requirement.getPath());
        }
        DigitalTwinSnapshot twin = store.snapshot(paths, nowElapsedMs);
        SafetyVehicleStateSnapshot safety = new SafetyVehicleStateSnapshot(
                "simulated-scenario-binder-stub",
                1,
                nowElapsedMs,
                SafetyState.NORMAL,
                moving ? MotionState.MOVING : MotionState.PARKED,
                true,
                SourceAssurance.RUNTIME_OWNED_STUB,
                false);
        return new ContextSnapshotBuilder().build(
                twin, safety, policy, SeatZone.ROW1_DRIVER, false);
    }

    private String nextUuid() {
        String value = uuidSupplier.get();
        return canonicalUuid(value, "UUID supplier value");
    }

    private static String canonicalUuid(String value, String field) {
        try {
            if (value == null || !UUID.fromString(value).toString().equals(value)) {
                throw violation(field + " is not canonical lowercase UUID");
            }
            return value;
        } catch (IllegalArgumentException failure) {
            throw violation(field + " is not canonical lowercase UUID");
        }
    }

    private static SignalValue decimal(
            VehicleSignalPath path,
            double value,
            String unit,
            String area,
            long epochMs,
            long elapsedMs) {
        return SignalValue.ofDecimal(
                path,
                value,
                unit,
                area,
                timestamp(epochMs, elapsedMs),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                1);
    }

    private static SignalValue text(
            VehicleSignalPath path,
            String value,
            String area,
            long epochMs,
            long elapsedMs) {
        return SignalValue.ofText(
                path,
                value,
                "",
                area,
                timestamp(epochMs, elapsedMs),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                1);
    }

    private static SignalValue bool(
            VehicleSignalPath path,
            boolean value,
            String area,
            long epochMs,
            long elapsedMs) {
        return SignalValue.ofBoolean(
                path,
                value,
                "",
                area,
                timestamp(epochMs, elapsedMs),
                SignalQuality.VALID,
                SignalSource.SIMULATED,
                1);
    }

    private static SignalTimestamp timestamp(long epochMs, long elapsedMs) {
        return new SignalTimestamp(epochMs, elapsedMs);
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_SIM_SCENARIO_INPUT: " + message);
    }
}
