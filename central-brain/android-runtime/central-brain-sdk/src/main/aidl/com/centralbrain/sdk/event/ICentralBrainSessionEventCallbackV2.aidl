package com.centralbrain.sdk.event;

import com.centralbrain.sdk.event.RuntimeEvent;

// Notification-only callback. Each event carries the exact cursor that may be ACKed.
// Authoritative recovery remains cursor replay through ICentralBrainSessionEventsV2.
oneway interface ICentralBrainSessionEventCallbackV2 {
    void onEvent(in RuntimeEvent event, String resumeCursor);
    void onOverflow(String resumeCursor, long droppedCount);
    void onClosed(int reasonCode, String resumeCursor);
}
