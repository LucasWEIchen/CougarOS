package com.centralbrain.sdk.governance;

import com.centralbrain.sdk.governance.ActionDecision;
import com.centralbrain.sdk.governance.ActionRequest;
import com.centralbrain.sdk.governance.ApprovalHandle;
import com.centralbrain.sdk.governance.ApprovalStatus;

// Fast policy and pending-approval control only. There is intentionally no grant method.
interface ICentralBrainGovernance {
    const int INTERFACE_VERSION = 1;
    const String INTERFACE_HASH = "9cb2f421c5950ebfa67604d35a1cfcdadb95e2510aafac80aaadb248ad93f150";

    const String ACTION_VEHICLE_STATE_READ = "vehicle.state.read";
    const String ACTION_CABIN_TEMPERATURE_SET = "cabin.temperature.set";
    const String ACTION_DRIVER_VIDEO_PLAY = "driver.display.video.play";
    const String ACTION_DIAGNOSTIC_WRITE = "vehicle.diagnostics.write";
    const String ACTION_OTA_INSTALL = "system.ota.install";

    const int RISK_UNKNOWN = 0;
    const int RISK_READ_ONLY = 1;
    const int RISK_COMFORT_CONTROL = 2;
    const int RISK_DRIVER_DISTRACTION = 3;
    const int RISK_DIAGNOSTIC_WRITE = 4;
    const int RISK_OTA = 5;

    const int DECISION_DENY = 0;
    const int DECISION_ALLOW_POLICY_ONLY = 1;
    const int DECISION_APPROVAL_REQUIRED = 2;

    const int SAFETY_UNKNOWN = 0;
    const int SAFETY_NORMAL = 1;
    const int SAFETY_DEGRADED = 2;
    const int SAFETY_EMERGENCY = 3;

    const int MOTION_UNKNOWN = 0;
    const int MOTION_PARKED = 1;
    const int MOTION_MOVING = 2;

    const int APPROVAL_STATUS_UNKNOWN = 0;
    const int APPROVAL_STATUS_PENDING = 1;
    const int APPROVAL_STATUS_CANCELLED = 2;
    const int APPROVAL_STATUS_EXPIRED = 3;

    int getProtocolVersion();
    String getProtocolHash();
    ActionDecision evaluateAction(in ActionRequest request);
    ApprovalHandle requestApproval(in ActionRequest request);
    ApprovalStatus getApprovalStatus(in ApprovalHandle handle);
    boolean cancelApproval(in ApprovalHandle handle);
}
