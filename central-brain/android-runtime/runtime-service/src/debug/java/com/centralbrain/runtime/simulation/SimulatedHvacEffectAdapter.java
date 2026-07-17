package com.centralbrain.runtime.simulation;

import com.centralbrain.runtime.effects.EffectAdapter;
import com.centralbrain.runtime.vehicle.capability.CapabilityCatalog;
import com.centralbrain.runtime.vehicle.capability.VehicleCapability;
import com.centralbrain.runtime.vehicle.schema.SignalQuality;
import com.centralbrain.runtime.vehicle.schema.SignalSource;
import com.centralbrain.runtime.vehicle.schema.SignalTimestamp;
import com.centralbrain.runtime.vehicle.schema.SignalValue;
import com.centralbrain.runtime.vehicle.schema.VehicleSignalPath;
import com.centralbrain.runtime.vehicle.twin.DesiredStateRecord;
import com.centralbrain.runtime.vehicle.twin.DigitalTwinSnapshot;
import com.centralbrain.runtime.vehicle.twin.VehicleDigitalTwinStore;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Debug-only typed HVAC adapter backed by an isolated in-process Digital Twin. */
public final class SimulatedHvacEffectAdapter extends SimulatedEffectAdapter {
    public static final String ADAPTER_ID = "debug.simulated.hvac.v1";
    public static final String DESTINATION = "vehicle.hvac";
    public static final int TARGET_SCHEMA_VERSION = 1;
    public static final long DESIRED_TTL_MS = 180_000L;

    private static final int TARGET_MAGIC = 0x43424856;
    private static final int TARGET_FIXED_BYTES = Integer.BYTES * 5 + Long.BYTES;
    private static final long SYNTHETIC_SOURCE_EPOCH_BASE_MS = 1_700_000_000_000L;
    private static final Set<VehicleCapability.CapabilityId> SUPPORTED_CAPABILITIES =
            Collections.unmodifiableSet(EnumSet.of(
                    VehicleCapability.CapabilityId.HVAC_POWER,
                    VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE,
                    VehicleCapability.CapabilityId.HVAC_FAN_LEVEL));

    public static final class HvacTarget {
        private static final int KIND_BOOLEAN = 1;
        private static final int KIND_INTEGER = 2;
        private static final int KIND_DECIMAL = 3;

        private final VehicleCapability.CapabilityId capabilityId;
        private final String area;
        private final int kind;
        private final long encodedValue;

        private HvacTarget(
                VehicleCapability.CapabilityId capabilityId,
                String area,
                int kind,
                long encodedValue) {
            this.capabilityId = Objects.requireNonNull(capabilityId, "capabilityId");
            this.area = requireArea(area);
            this.kind = kind;
            this.encodedValue = encodedValue;
        }

        public static HvacTarget power(boolean enabled) {
            return new HvacTarget(
                    VehicleCapability.CapabilityId.HVAC_POWER,
                    "cabin",
                    KIND_BOOLEAN,
                    enabled ? 1 : 0);
        }

        public static HvacTarget targetTemperature(String area, double celsius) {
            if (!Double.isFinite(celsius)) {
                throw new IllegalArgumentException("CB_SIM_HVAC: temperature is not finite");
            }
            return new HvacTarget(
                    VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE,
                    area,
                    KIND_DECIMAL,
                    Double.doubleToLongBits(celsius));
        }

        public static HvacTarget fanLevel(String area, long level) {
            return new HvacTarget(
                    VehicleCapability.CapabilityId.HVAC_FAN_LEVEL,
                    area,
                    KIND_INTEGER,
                    level);
        }

        public VehicleCapability.CapabilityId getCapabilityId() {
            return capabilityId;
        }

        public String getArea() {
            return area;
        }

        public boolean getBooleanValue() {
            requireKind(KIND_BOOLEAN);
            return encodedValue == 1;
        }

        public long getIntegerValue() {
            requireKind(KIND_INTEGER);
            return encodedValue;
        }

        public double getDecimalValue() {
            requireKind(KIND_DECIMAL);
            return Double.longBitsToDouble(encodedValue);
        }

        public byte[] toCanonicalPayload() {
            byte[] areaBytes = area.getBytes(StandardCharsets.UTF_8);
            ByteBuffer output = ByteBuffer.allocate(TARGET_FIXED_BYTES + areaBytes.length);
            output.putInt(TARGET_MAGIC);
            output.putInt(TARGET_SCHEMA_VERSION);
            output.putInt(capabilityCode(capabilityId));
            output.putInt(areaBytes.length);
            output.put(areaBytes);
            output.putInt(kind);
            output.putLong(encodedValue);
            return output.array();
        }

        public static HvacTarget fromCanonicalPayload(byte[] canonicalPayload) {
            Objects.requireNonNull(canonicalPayload, "canonicalPayload");
            if (canonicalPayload.length < TARGET_FIXED_BYTES
                    || canonicalPayload.length > TARGET_FIXED_BYTES + 32) {
                throw new IllegalArgumentException("CB_SIM_HVAC: payload size is invalid");
            }
            ByteBuffer input = ByteBuffer.wrap(canonicalPayload);
            if (input.getInt() != TARGET_MAGIC || input.getInt() != TARGET_SCHEMA_VERSION) {
                throw new IllegalArgumentException("CB_SIM_HVAC: payload header is invalid");
            }
            VehicleCapability.CapabilityId capabilityId =
                    capabilityForCode(input.getInt());
            int areaLength = input.getInt();
            if (areaLength < 1
                    || areaLength > 32
                    || input.remaining() != areaLength + Integer.BYTES + Long.BYTES) {
                throw new IllegalArgumentException("CB_SIM_HVAC: payload area length is invalid");
            }
            byte[] areaBytes = new byte[areaLength];
            input.get(areaBytes);
            String area = decodeArea(areaBytes);
            int kind = input.getInt();
            long value = input.getLong();
            switch (capabilityId) {
                case HVAC_POWER:
                    if (kind != KIND_BOOLEAN || (value != 0 && value != 1)) {
                        throw new IllegalArgumentException(
                                "CB_SIM_HVAC: power payload type is invalid");
                    }
                    return new HvacTarget(capabilityId, area, kind, value);
                case HVAC_TARGET_TEMPERATURE:
                    if (kind != KIND_DECIMAL
                            || !Double.isFinite(Double.longBitsToDouble(value))) {
                        throw new IllegalArgumentException(
                                "CB_SIM_HVAC: temperature payload type is invalid");
                    }
                    return new HvacTarget(capabilityId, area, kind, value);
                case HVAC_FAN_LEVEL:
                    if (kind != KIND_INTEGER) {
                        throw new IllegalArgumentException(
                                "CB_SIM_HVAC: fan payload type is invalid");
                    }
                    return new HvacTarget(capabilityId, area, kind, value);
                default:
                    throw new IllegalArgumentException(
                            "CB_SIM_HVAC: payload capability is unsupported");
            }
        }

        private void requireKind(int expected) {
            if (kind != expected) {
                throw new IllegalStateException("CB_SIM_HVAC: target accessor type mismatch");
            }
        }

        private static String requireArea(String area) {
            if (area == null || area.isEmpty() || area.length() > 32) {
                throw new IllegalArgumentException("CB_SIM_HVAC: target area is invalid");
            }
            for (int index = 0; index < area.length(); index++) {
                if (Character.isISOControl(area.charAt(index))) {
                    throw new IllegalArgumentException(
                            "CB_SIM_HVAC: target area contains a control character");
                }
            }
            return area;
        }

        private static String decodeArea(byte[] areaBytes) {
            String area = new String(areaBytes, StandardCharsets.UTF_8);
            if (!java.util.Arrays.equals(areaBytes, area.getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("CB_SIM_HVAC: target area is not canonical UTF-8");
            }
            return requireArea(area);
        }

        private static int capabilityCode(VehicleCapability.CapabilityId capabilityId) {
            switch (capabilityId) {
                case HVAC_POWER:
                    return 1;
                case HVAC_TARGET_TEMPERATURE:
                    return 2;
                case HVAC_FAN_LEVEL:
                    return 3;
                default:
                    throw new IllegalArgumentException(
                            "CB_SIM_HVAC: capability is not HVAC");
            }
        }

        private static VehicleCapability.CapabilityId capabilityForCode(int code) {
            switch (code) {
                case 1:
                    return VehicleCapability.CapabilityId.HVAC_POWER;
                case 2:
                    return VehicleCapability.CapabilityId.HVAC_TARGET_TEMPERATURE;
                case 3:
                    return VehicleCapability.CapabilityId.HVAC_FAN_LEVEL;
                default:
                    throw new IllegalArgumentException(
                            "CB_SIM_HVAC: capability code is unknown");
            }
        }
    }

    private final CapabilityCatalog capabilityCatalog;
    private VehicleDigitalTwinStore twinStore = new VehicleDigitalTwinStore();
    private long signalRevision;

    public SimulatedHvacEffectAdapter(
            SimulationClock clock,
            FaultInjectionProfile initialProfile) {
        super(ADAPTER_ID, DESTINATION, clock, initialProfile);
        capabilityCatalog = CapabilityCatalog.stage2Defaults();
    }

    public Set<VehicleCapability.CapabilityId> getSupportedCapabilities() {
        return SUPPORTED_CAPABILITIES;
    }

    public synchronized DigitalTwinSnapshot getDigitalTwinSnapshot() {
        return twinStore.snapshot(
                Set.of(
                        VehicleSignalPath.HVAC_ACTIVE,
                        VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                        VehicleSignalPath.HVAC_FAN_LEVEL),
                nowSimulationElapsedRealtimeMs());
    }

    public synchronized long getDigitalTwinRevision() {
        return twinStore.getRevision();
    }

    @Override
    protected void validateSimulationInvocation(EffectAdapter.Invocation invocation) {
        HvacTarget target = decodeAndValidate(invocation);
        VehicleCapability capability = capabilityCatalog.require(target.getCapabilityId());
        if (!capability.getAvailability().isWritable()
                || !capability.getAvailability().isSimulatable()
                || capability.getAvailability().canUseProduction()) {
            throw new IllegalStateException(
                    "CB_SIM_HVAC: capability availability is unsafe for simulation");
        }
        if (!capability.getAreas().contains(target.getArea())) {
            throw new IllegalArgumentException("CB_SIM_HVAC: target area is unsupported");
        }
        validateRange(capability, target);
    }

    @Override
    protected synchronized void onSimulationAdmitted(
            EffectAdapter.Invocation invocation,
            FaultInjectionProfile profile) {
        HvacTarget target = decodeAndValidate(invocation);
        long now = nowSimulationElapsedRealtimeMs();
        long expires = safeAdd(now, DESIRED_TTL_MS);
        twinStore.setDesired(desiredRecord(target, now, expires), now);
    }

    @Override
    protected synchronized void onSimulationApplied(
            EffectAdapter.Invocation invocation,
            FaultInjectionProfile profile) {
        HvacTarget desired = decodeAndValidate(invocation);
        HvacTarget reported = profile.getMode() == FaultInjectionProfile.Mode.READBACK_MISMATCH
                ? mismatchFor(desired) : desired;
        long now = nowSimulationElapsedRealtimeMs();
        long revision = nextSignalRevision();
        SignalTimestamp timestamp = new SignalTimestamp(
                safeAdd(SYNTHETIC_SOURCE_EPOCH_BASE_MS, revision),
                now);
        twinStore.updateReported(
                signalValue(reported, timestamp, revision),
                now);
    }

    @Override
    protected synchronized void onSimulationReset() {
        twinStore = new VehicleDigitalTwinStore();
        signalRevision = 0;
    }

    private HvacTarget decodeAndValidate(EffectAdapter.Invocation invocation) {
        if (!DESTINATION.equals(invocation.getDestination())) {
            throw new IllegalArgumentException("CB_SIM_HVAC: destination is invalid");
        }
        HvacTarget target = HvacTarget.fromCanonicalPayload(
                invocation.getCanonicalPayload());
        if (!target.getCapabilityId().getCanonicalId().equals(invocation.getActionId())) {
            throw new IllegalArgumentException(
                    "CB_SIM_HVAC: action does not match target capability");
        }
        return target;
    }

    private static void validateRange(
            VehicleCapability capability,
            HvacTarget target) {
        switch (target.getCapabilityId()) {
            case HVAC_POWER:
                capability.getTargetRange().validateBoolean(target.getBooleanValue());
                return;
            case HVAC_TARGET_TEMPERATURE:
                capability.getTargetRange().validateDecimal(target.getDecimalValue());
                return;
            case HVAC_FAN_LEVEL:
                capability.getTargetRange().validateInteger(target.getIntegerValue());
                return;
            default:
                throw new IllegalArgumentException("CB_SIM_HVAC: capability is unsupported");
        }
    }

    private static DesiredStateRecord desiredRecord(
            HvacTarget target,
            long requestedAt,
            long expiresAt) {
        switch (target.getCapabilityId()) {
            case HVAC_POWER:
                return DesiredStateRecord.ofBoolean(
                        VehicleSignalPath.HVAC_ACTIVE,
                        target.getBooleanValue(),
                        "",
                        target.getArea(),
                        requestedAt,
                        expiresAt);
            case HVAC_TARGET_TEMPERATURE:
                return DesiredStateRecord.ofDecimal(
                        VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                        target.getDecimalValue(),
                        "celsius",
                        target.getArea(),
                        requestedAt,
                        expiresAt);
            case HVAC_FAN_LEVEL:
                return DesiredStateRecord.ofInteger(
                        VehicleSignalPath.HVAC_FAN_LEVEL,
                        target.getIntegerValue(),
                        "level",
                        target.getArea(),
                        requestedAt,
                        expiresAt);
            default:
                throw new IllegalArgumentException("CB_SIM_HVAC: capability is unsupported");
        }
    }

    private static SignalValue signalValue(
            HvacTarget target,
            SignalTimestamp timestamp,
            long revision) {
        switch (target.getCapabilityId()) {
            case HVAC_POWER:
                return SignalValue.ofBoolean(
                        VehicleSignalPath.HVAC_ACTIVE,
                        target.getBooleanValue(),
                        "",
                        target.getArea(),
                        timestamp,
                        SignalQuality.VALID,
                        SignalSource.SIMULATED,
                        revision);
            case HVAC_TARGET_TEMPERATURE:
                return SignalValue.ofDecimal(
                        VehicleSignalPath.HVAC_TARGET_TEMPERATURE,
                        target.getDecimalValue(),
                        "celsius",
                        target.getArea(),
                        timestamp,
                        SignalQuality.VALID,
                        SignalSource.SIMULATED,
                        revision);
            case HVAC_FAN_LEVEL:
                return SignalValue.ofInteger(
                        VehicleSignalPath.HVAC_FAN_LEVEL,
                        target.getIntegerValue(),
                        "level",
                        target.getArea(),
                        timestamp,
                        SignalQuality.VALID,
                        SignalSource.SIMULATED,
                        revision);
            default:
                throw new IllegalArgumentException("CB_SIM_HVAC: capability is unsupported");
        }
    }

    private HvacTarget mismatchFor(HvacTarget target) {
        switch (target.getCapabilityId()) {
            case HVAC_POWER:
                return HvacTarget.power(!target.getBooleanValue());
            case HVAC_TARGET_TEMPERATURE:
                double value = target.getDecimalValue();
                return HvacTarget.targetTemperature(
                        target.getArea(),
                        value < 30.0 ? value + 0.5 : value - 0.5);
            case HVAC_FAN_LEVEL:
                long level = target.getIntegerValue();
                return HvacTarget.fanLevel(
                        target.getArea(),
                        level < 7 ? level + 1 : level - 1);
            default:
                throw new IllegalArgumentException("CB_SIM_HVAC: capability is unsupported");
        }
    }

    private long nextSignalRevision() {
        if (signalRevision == Long.MAX_VALUE) {
            throw new IllegalStateException("CB_SIM_HVAC: signal revision exhausted");
        }
        signalRevision++;
        return signalRevision;
    }

    private static long safeAdd(long left, long right) {
        if (left < 0 || right < 0 || left > Long.MAX_VALUE - right) {
            throw new IllegalArgumentException("CB_SIM_HVAC: simulated time overflow");
        }
        return left + right;
    }
}
