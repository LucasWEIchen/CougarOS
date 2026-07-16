package com.centralbrain.sdk.event;

import com.centralbrain.sdk.event.EventPage;
import com.centralbrain.sdk.event.ICentralBrainSessionEventCallback;

// Stage 2 P1-W03 contract only. P1-W05 owns Service publication and client lifecycle.
// Req IDs: S2-SES-001, S2-EVT-001, FW-U-003, NV-F-009, NV-G-004, XSC-001, XSC-006.
interface ICentralBrainSessionEvents {
    const int INTERFACE_VERSION = 1;
    const String INTERFACE_HASH = "bb3618ca5f5818ce70b3a889a928b54ad83c70f0e439db5b62c67eb3234957d5";
    const int MAX_PAGE_SIZE = 100;

    int getProtocolVersion();
    String getProtocolHash();
    EventPage getEvents(String sessionId, String cursor, int limit);
    boolean registerSessionCallback(
            String sessionId,
            String cursor,
            ICentralBrainSessionEventCallback callback);
    boolean unregisterSessionCallback(
            String sessionId,
            ICentralBrainSessionEventCallback callback);
}
