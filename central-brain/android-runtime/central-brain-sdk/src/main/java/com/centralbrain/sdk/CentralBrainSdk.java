package com.centralbrain.sdk;

/**
 * Build-time identity for the Stage 2 Android workflow boundary.
 *
 * Req IDs: APP-004, XSC-001, XSC-006, NV-P-002, DEL-001.
 */
public final class CentralBrainSdk {
    public static final String SDK_NAME = "central-brain-sdk";
    public static final String SDK_VERSION = "0.2.0";
    public static final String EVOLUTION_STAGE = "R4_DURABLE_WORKFLOW";
    public static final String MATURITY = "android_integrated";
    public static final String STAGE2_IMPLEMENTATION = "P1-W07_RUNTIME_CONTRACT_V2";
    public static final String ACTION_SESSION_RUNTIME =
            "com.centralbrain.runtime.action.SESSION_RUNTIME";
    public static final String ACTION_SESSION_EVENTS =
            "com.centralbrain.runtime.action.SESSION_EVENTS";
    public static final String ACTION_SESSION_EVENTS_V2 =
            "com.centralbrain.runtime.action.SESSION_EVENTS_V2";
    public static final String ACTION_ORCHESTRATION =
            "com.centralbrain.runtime.action.ORCHESTRATION";

    private CentralBrainSdk() {
    }
}
