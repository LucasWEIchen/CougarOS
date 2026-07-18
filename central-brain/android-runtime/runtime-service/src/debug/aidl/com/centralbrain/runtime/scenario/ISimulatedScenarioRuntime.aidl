package com.centralbrain.runtime.scenario;

import com.centralbrain.runtime.scenario.SimulatedScenarioBinderSnapshot;

// Debug-only fixed-scenario metadata endpoint. Absent from release builds.
// Req IDs: S2-SCN-001, S2-GRF-001, S2-EVT-001, S2-HMI-003/006, APP-004.
interface ISimulatedScenarioRuntime {
    const int INTERFACE_VERSION = 2;
    const String INTERFACE_HASH = "acfd1cffb193433425bd173477b826c9a0dca66d87488348fd4a513b895ee9c6";

    const int SCENARIO_COLD = 1;
    const int SCENARIO_FATIGUE = 2;

    const int DRIVING_PARKED = 1;
    const int DRIVING_MOVING = 2;

    const int OUTCOME_SUCCEEDED = 1;
    const int OUTCOME_FAILED = 2;
    const int OUTCOME_SKIPPED = 3;

    const int SESSION_WAITING_APPROVAL = 1;
    const int SESSION_WAITING_EFFECT = 2;
    const int SESSION_WAITING_READBACK = 3;
    const int SESSION_COMPLETED = 4;
    const int SESSION_FAILED = 5;
    const int SESSION_CANCELLED = 6;
    const int SESSION_PARTIAL = 7;
    const int SESSION_STUCK = 8;

    const int PENDING_NONE = 0;
    const int PENDING_APPROVAL = 1;
    const int PENDING_EFFECT = 2;
    const int PENDING_READBACK = 3;

    int getProtocolVersion();
    String getProtocolHash();
    SimulatedScenarioBinderSnapshot startScenario(int scenario, int drivingState);
    SimulatedScenarioBinderSnapshot getSnapshot(String runId);
    SimulatedScenarioBinderSnapshot supplyPendingOutcome(String runId, int outcome);
    SimulatedScenarioBinderSnapshot cancel(String runId);
}
