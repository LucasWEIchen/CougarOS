package com.centralbrain.client2;

import com.centralbrain.sdk.event.RuntimeEvent;
import com.centralbrain.sdk.session.SessionHandle;
import com.centralbrain.sdk.session.SessionSnapshot;

/**
 * Client2-facing Session/Event observer.
 *
 * <p>The typed methods are the primary Stage 2 contract. The three onBridge methods remain
 * default compatibility hooks for the Stage 1 smali controller and must not be used by new HMI
 * code as an authoritative session state source.</p>
 */
public interface ScenarioCallback {
    default void onSessionConnectionChanged(boolean connected, boolean reconnected) {}

    default void onSessionOpened(SessionHandle handle, String scenarioId) {}

    default void onSessionSnapshot(SessionSnapshot snapshot) {}

    default void onSessionEvent(RuntimeEvent event) {}

    default void onSessionReplayComplete(SessionHandle handle, long lastSequence) {}

    default void onSessionOverflow(SessionHandle handle, String resumeCursor) {}

    default void onSessionClosed(
            SessionHandle handle,
            int reasonCode,
            String resumeCursor) {}

    default void onSessionError(SessionHandle handle, String code, String message) {}

    /** @deprecated Stage 1 text projection only. */
    @Deprecated
    default void onBridgeStatus(String status) {}

    /** @deprecated Stage 1 text projection only. */
    @Deprecated
    default void onBridgeReply(String reply) {}

    /** @deprecated Stage 1 text projection only. */
    @Deprecated
    default void onBridgeFailure(String reason) {}
}
