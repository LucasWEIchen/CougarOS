package com.centralbrain.sdk.session;

import com.centralbrain.sdk.session.SessionHandle;
import com.centralbrain.sdk.session.SessionPage;
import com.centralbrain.sdk.session.SessionQuery;
import com.centralbrain.sdk.session.SessionRequest;
import com.centralbrain.sdk.session.SessionSnapshot;

// Stage 2 P1-W01 contract only. Runtime publication and SDK facade are later work packages.
// Req IDs: S2-SES-001, S2-UX-001, APP-004, XSC-001, XSC-006, NV-G-003.
interface ICentralBrainSessionRuntime {
    const int INTERFACE_VERSION = 1;
    const String INTERFACE_HASH = "f4b3ac677b3294e7cb20382652d37ef995432a2e5ca335d131295d6a43d4024c";

    const int SOURCE_HMI_BUTTON = 1;
    const int SOURCE_VOICE = 2;
    const int SOURCE_TRIGGER = 3;
    const int SOURCE_API = 4;

    const int SEAT_ZONE_UNSPECIFIED = 0;
    const int SEAT_ZONE_DRIVER = 1;
    const int SEAT_ZONE_FRONT_PASSENGER = 2;
    const int SEAT_ZONE_REAR_LEFT = 3;
    const int SEAT_ZONE_REAR_RIGHT = 4;
    const int SEAT_ZONE_CABIN = 5;

    const int SESSION_STATE_ANY = -1;
    const int SESSION_STATE_UNKNOWN = 0;
    const int SESSION_STATE_CREATED = 1;
    const int SESSION_STATE_PLANNING = 2;
    const int SESSION_STATE_WAITING_FOR_CONFIRMATION = 3;
    const int SESSION_STATE_EXECUTING = 4;
    const int SESSION_STATE_PARTIALLY_COMPLETED = 5;
    const int SESSION_STATE_COMPENSATING = 6;
    const int SESSION_STATE_STUCK = 7;
    const int SESSION_STATE_COMPLETED = 8;
    const int SESSION_STATE_FAILED = 9;
    const int SESSION_STATE_CANCELLED = 10;

    const int CANCEL_REASON_USER = 1;
    const int CANCEL_REASON_DEADLINE = 2;
    const int CANCEL_REASON_POLICY = 3;
    const int CANCEL_REASON_CALLER_GONE = 4;

    int getProtocolVersion();
    String getProtocolHash();
    SessionHandle openSession(in SessionRequest request);
    SessionSnapshot getSession(in SessionHandle handle);
    SessionPage listSessions(in SessionQuery query);
    boolean cancelSession(in SessionHandle handle, int reasonCode);
}
