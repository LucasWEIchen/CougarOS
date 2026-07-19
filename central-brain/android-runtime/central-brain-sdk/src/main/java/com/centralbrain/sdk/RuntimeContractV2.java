package com.centralbrain.sdk;

import com.centralbrain.sdk.event.EventContract;
import com.centralbrain.sdk.event.ICentralBrainSessionEvents;
import com.centralbrain.sdk.event.ICentralBrainSessionEventsV2;
import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;
import com.centralbrain.sdk.session.SessionContract;

/**
 * Aggregate Stage 2 contract identity over frozen V1 and independent Event V2 surfaces.
 *
 * <p>Version 2 identifies the compatible Session/Plan/Event/Effect/SDK/Room capability set. It
 * does not change a V1 AIDL transaction or publish Plan/Effect execution.
 */
public final class RuntimeContractV2 {
    public static final int AGGREGATE_VERSION = 2;
    public static final int SESSION_WIRE_VERSION = ICentralBrainSessionRuntime.INTERFACE_VERSION;
    public static final int EVENT_WIRE_VERSION = ICentralBrainSessionEvents.INTERFACE_VERSION;
    public static final int EVENT_V2_WIRE_VERSION =
            ICentralBrainSessionEventsV2.INTERFACE_VERSION;
    public static final int PLAN_DTO_VERSION = 1;
    public static final int EFFECT_DTO_VERSION = 1;

    public static final int SESSION_PAGE_ITEMS = SessionContract.MAX_PAGE_SIZE;
    public static final int EVENT_PAGE_ITEMS = EventContract.MAX_PAGE_SIZE;
    public static final int CURSOR_CHARS = EventContract.MAX_CURSOR_CHARS;
    public static final int REPLAY_PAGE_LIMIT = 64;

    public static final String ERROR_NOT_CONNECTED = "NOT_CONNECTED";
    public static final String ERROR_PROTOCOL_MISMATCH = "PROTOCOL_MISMATCH";
    public static final String ERROR_TRANSPORT = "TRANSPORT";
    public static final String ERROR_SUBSCRIPTION = "SUBSCRIPTION";
    public static final String ERROR_CLOSED = "CLOSED";

    public static final boolean EVENT_V1_TERMINAL_RESUME_CURSOR = false;
    public static final boolean EVENT_V2_CURSOR_ACK_REQUIRED = true;
    public static final boolean EVENT_V2_INTERFACE_PUBLISHED = true;
    public static final boolean SCENARIO_EXECUTION_ENABLED = false;

    private RuntimeContractV2() {
    }
}
