package com.centralbrain.sdk.effect;

// Stage 2 P1-W04 immutable governed Effect input. Raw vehicle/model payloads are forbidden.
// Req IDs: S2-EFF-001, S2-SAF-001, S2-UX-003, FW-U-004, NV-F-003, NV-F-009, NV-G-005.
parcelable EffectIntent {
    int schemaVersion = 1;
    String effectId = "";
    String sessionId = "";
    String planId = "";
    String nodeId = "";
    String actionId = "";
    String capabilityId = "";
    String targetArea = "";
    int valueKind = 0;
    boolean booleanValue = false;
    long integerValue = 0;
    double decimalValue = 0.0;
    String textValue = "";
    String unit = "";
    String targetValueDigest = "";
    String idempotencyKey = "";
    String planDigest = "";
    String contextDigest = "";
    long contextVersion = 0;
    int riskClass = 0;
    boolean required = true;
    int verificationPolicy = 0;
    double verificationTolerance = 0.0;
    boolean reversible = false;
    String compensationDigest = "";
    long createdAtEpochMs = 0;
    long deadlineEpochMs = 0;
}
