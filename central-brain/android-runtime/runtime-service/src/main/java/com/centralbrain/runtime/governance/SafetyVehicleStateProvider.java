package com.centralbrain.runtime.governance;

/** Supplies Safety/Vehicle State without accepting caller-provided policy assertions. */
public interface SafetyVehicleStateProvider {
    SafetyVehicleStateSnapshot currentSnapshot();
}
