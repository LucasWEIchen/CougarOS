package com.centralbrain.sdk.event;

import com.centralbrain.sdk.event.EventAckRequest;
import com.centralbrain.sdk.event.EventAckResult;
import com.centralbrain.sdk.event.EventPageV2;
import com.centralbrain.sdk.event.EventSubscriptionHandle;
import com.centralbrain.sdk.event.EventSubscriptionRequest;
import com.centralbrain.sdk.event.ICentralBrainSessionEventCallbackV2;

// Independent Event V2 surface. Frozen Event V1 transactions and hash remain unchanged.
// Req IDs: S2-EVT-001, FW-U-003, NV-G-004/006/007, XSC-001/005/006.
interface ICentralBrainSessionEventsV2 {
    const int INTERFACE_VERSION = 2;
    const String INTERFACE_HASH = "97cbff7810efcece0c90d1ad6ae0587af96ee78afe2b21fc7b55b617e66466ca";
    const int MAX_PAGE_SIZE = 100;
    const int MAX_QUEUE_CAPACITY = 256;

    const int SUBSCRIPTION_STATE_ACTIVE = 1;
    const int SUBSCRIPTION_STATE_RESYNC_REQUIRED = 2;
    const int SUBSCRIPTION_STATE_CANCELLED = 3;

    const int ACK_APPLIED = 1;
    const int ACK_REPLAYED = 2;
    const int ACK_NOT_FOUND = 3;
    const int ACK_NOT_ACTIVE = 4;
    const int ACK_RESYNC_REQUIRED = 5;
    const int ACK_STALE = 6;
    const int ACK_FUTURE = 7;
    const int ACK_SOURCE_REGRESSION = 8;

    int getProtocolVersion();
    String getProtocolHash();
    EventPageV2 getEvents(String sessionId, String cursor, int limit);
    EventSubscriptionHandle registerSessionCallback(
            in EventSubscriptionRequest request,
            ICentralBrainSessionEventCallbackV2 callback);
    EventAckResult acknowledge(in EventAckRequest request);
    boolean unregisterSessionCallback(
            in EventSubscriptionHandle handle,
            ICentralBrainSessionEventCallbackV2 callback);
    boolean cancelSubscription(in EventSubscriptionHandle handle);
}
