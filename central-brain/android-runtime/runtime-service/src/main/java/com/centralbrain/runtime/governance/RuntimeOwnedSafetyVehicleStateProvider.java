package com.centralbrain.runtime.governance;

import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.MotionState;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.SafetyState;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot.SourceAssurance;

import java.util.Objects;
import java.util.function.LongSupplier;

/** Hardware-free policy fixture owned by the Runtime, never by the Binder request. */
public final class RuntimeOwnedSafetyVehicleStateProvider
        implements SafetyVehicleStateProvider {
    public static final String SOURCE_ID = "runtime-owned-state-stub";

    private final LongSupplier elapsedRealtimeMs;

    public RuntimeOwnedSafetyVehicleStateProvider(LongSupplier elapsedRealtimeMs) {
        this.elapsedRealtimeMs = Objects.requireNonNull(elapsedRealtimeMs, "elapsedRealtimeMs");
    }

    @Override
    public SafetyVehicleStateSnapshot currentSnapshot() {
        return new SafetyVehicleStateSnapshot(
                SOURCE_ID,
                1,
                elapsedRealtimeMs.getAsLong(),
                SafetyState.NORMAL,
                MotionState.PARKED,
                true,
                SourceAssurance.RUNTIME_OWNED_STUB,
                false);
    }
}
