package com.centralbrain.client2;

/** Minimal callback implemented by the APK-level Client2 panel controller. */
public interface ScenarioCallback {
    void onBridgeStatus(String status);

    void onBridgeReply(String reply);

    void onBridgeFailure(String reason);
}
