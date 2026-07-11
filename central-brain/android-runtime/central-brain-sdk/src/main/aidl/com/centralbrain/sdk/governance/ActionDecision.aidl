package com.centralbrain.sdk.governance;

parcelable ActionDecision {
    int schemaVersion = 1;
    String clientRequestId = "";
    String actionId = "";
    int riskClass = 0;
    int outcome = 0;
    String reasonCode = "";
    String stateSourceId = "";
    long stateRevision = 0;
    int safetyState = 0;
    int motionState = 0;
    boolean driverAvailable = false;
    boolean sourceHardwareBacked = false;
    boolean sourceProductionTrusted = false;
    boolean dispatchAllowed = false;
}
