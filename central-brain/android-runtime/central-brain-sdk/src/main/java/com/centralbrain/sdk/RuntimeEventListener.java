package com.centralbrain.sdk;

import com.centralbrain.sdk.event.RuntimeEvent;
import com.centralbrain.sdk.session.SessionSnapshot;

/** UI-facing Session Event observer without Binder primitives. */
public interface RuntimeEventListener {
    default void onSnapshot(SessionSnapshot snapshot) {}

    default void onEvent(RuntimeEvent event) {}

    default void onReplayComplete(long lastSequence) {}

    default void onOverflow(String resumeCursor) {}

    default void onClosed(int reasonCode, String resumeCursor) {}

    default void onError(String code, String message) {}
}
