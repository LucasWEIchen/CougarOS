package com.centralbrain.sdk.event;

import com.centralbrain.sdk.event.RuntimeEvent;

// Notification-only callback. Clients recover authoritatively through cursor replay.
// Req IDs: S2-SES-001, S2-EVT-001, FW-U-003, NV-F-009, NV-G-004, XSC-001, XSC-006.
oneway interface ICentralBrainSessionEventCallback {
    void onEvent(in RuntimeEvent event);
    void onOverflow(String resumeCursor);
    void onClosed(int reasonCode, String resumeCursor);
}
