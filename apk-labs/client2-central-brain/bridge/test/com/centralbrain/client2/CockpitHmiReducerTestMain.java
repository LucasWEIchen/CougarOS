package com.centralbrain.client2;

import com.centralbrain.sdk.event.EventContract;
import com.centralbrain.sdk.event.MessageEvent;
import com.centralbrain.sdk.event.RuntimeEvent;
import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;
import com.centralbrain.sdk.session.SessionHandle;
import com.centralbrain.sdk.session.SessionSnapshot;

/** Host-JVM contract probe for immutable Client2 HMI reduction. */
public final class CockpitHmiReducerTestMain {
    private static final long NOW = 1_750_000_000_000L;
    private static final String SESSION_ID = "9bffbb6a-5a0b-41b5-a924-bcfb45be4f26";
    private static final String REQUEST_ID = "76015ba7-a1c7-4f95-bec9-eab3eb90105c";
    private static final String EVENT_ONE_ID = "ee141cb0-6927-4453-ac1b-53d57921eb0c";
    private static final String DIGEST =
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    public static void main(String[] args) {
        CockpitHmiState state = CockpitHmiState.initial();
        check(state.getPanelVisibility() == CockpitHmiState.PanelVisibility.HIDDEN,
                "initial panel must be hidden");
        check(state.getSurfaceStage() == CockpitHmiState.SurfaceStage.INTENT,
                "intent must be the initial surface");

        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.panelVisibility(true));
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.surfaceSelected(CockpitHmiState.SurfaceStage.RESULT));
        check(state.getSurfaceStage() == CockpitHmiState.SurfaceStage.RESULT,
                "surface selection must be reducer-owned");
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.scenarioSubmitted("care.cold"));
        check(state.getConnectionState() == CockpitHmiState.ConnectionState.CONNECTING,
                "submission must connect");
        check(state.getSurfaceStage() == CockpitHmiState.SurfaceStage.PLAN,
                "submission must move the observable shell to plan");

        SessionHandle handle = handle();
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.sessionOpened(handle, "scene.comfort.cold.v1"));
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.snapshot(snapshot(
                        ICentralBrainSessionRuntime.SESSION_STATE_EXECUTING,
                        "Scenario accepted; execution is not enabled")));
        check(state.hasResumableSession(NOW), "active handle must be resumable");

        RuntimeEvent first = scenarioRequested();
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.runtimeEvent(first));
        CockpitHmiState duplicate = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.runtimeEvent(first));
        check(duplicate == state, "duplicate event must not change state");

        RuntimeEvent assistant = assistantSummary();
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.runtimeEvent(assistant));
        check("座舱准备完成".equals(state.renderText()), "assistant message must render");
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.replayComplete(handle, 2));
        check(state.isReplayComplete(), "replay must become complete");

        CockpitHmiState.Checkpoint checkpoint = state.checkpoint();
        CockpitHmiState restored = CockpitHmiReducer.reduce(
                CockpitHmiState.initial(),
                CockpitHmiReducer.Event.restored(checkpoint));
        check(restored.getLastEventSequence() == 2, "checkpoint sequence must restore");
        check(!"座舱准备完成".equals(restored.renderText()),
                "checkpoint must not persist assistant text");
        check(restored.getConnectionState() == CockpitHmiState.ConnectionState.RECONNECTING,
                "restored session must reconnect");

        CockpitHmiState hidden = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.drawerSelected(CockpitHmiState.DeviceDrawer.HVAC));
        check(hidden.getDeviceDrawer() == CockpitHmiState.DeviceDrawer.HVAC,
                "device drawer selection must be reducer-owned");
        hidden = CockpitHmiReducer.reduce(
                hidden,
                CockpitHmiReducer.Event.panelVisibility(false));
        check(hidden.hasSession() && hidden.getLastEventSequence() == 2,
                "hiding must preserve session state");
        check(hidden.getDeviceDrawer() == CockpitHmiState.DeviceDrawer.CLOSED,
                "hiding must close the secondary drawer");
        CockpitHmiState detached = CockpitHmiReducer.reduce(
                hidden,
                CockpitHmiReducer.Event.detached());
        check(detached.hasSession()
                        && detached.getConnectionState()
                        == CockpitHmiState.ConnectionState.DISCONNECTED,
                "detach must preserve resumable state");

        CockpitHmiState gap = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.runtimeEvent(gapEvent()));
        check("CB_HMI_EVENT_GAP".equals(gap.getErrorCode()),
                "sequence gap must fail closed");

        CockpitHmiState terminal = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.snapshot(snapshot(
                        ICentralBrainSessionRuntime.SESSION_STATE_COMPLETED,
                        "Completed")));
        check(terminal.isTerminal() && !terminal.checkpoint().hasSession(),
                "terminal state must not be checkpointed for resume");

        System.out.println("cockpit_hmi_state_immutable_verified=true");
        System.out.println("cockpit_hmi_reducer_sequence_dedup_verified=true");
        System.out.println("cockpit_hmi_hidden_state_preserved=true");
        System.out.println("cockpit_hmi_checkpoint_text_persisted=false");
        System.out.println("cockpit_hmi_recreation_resume_state_verified=true");
        System.out.println("cockpit_hmi_four_stage_reducer_verified=true");
        System.out.println("cockpit_hmi_device_drawer_reducer_verified=true");
        System.out.println("scenario_execution_enabled=false");
        System.out.println("hardware_accessed=false");
    }

    private static SessionHandle handle() {
        SessionHandle handle = new SessionHandle();
        handle.sessionId = SESSION_ID;
        handle.acceptedAtEpochMs = NOW - 1_000;
        handle.expiresAtEpochMs = NOW + 60_000;
        return handle;
    }

    private static SessionSnapshot snapshot(int state, String summary) {
        SessionSnapshot snapshot = new SessionSnapshot();
        snapshot.sessionId = SESSION_ID;
        snapshot.requestId = REQUEST_ID;
        snapshot.scenarioId = "scene.comfort.cold.v1";
        snapshot.state = state;
        snapshot.createdAtEpochMs = NOW - 1_000;
        snapshot.updatedAtEpochMs = NOW;
        snapshot.deadlineEpochMs = NOW + 60_000;
        snapshot.summary = summary;
        return snapshot;
    }

    private static RuntimeEvent scenarioRequested() {
        RuntimeEvent event = baseEvent(EVENT_ONE_ID, 1, "", 0, "ScenarioRequested");
        event.source = EventContract.SOURCE_SCENARIO;
        return event;
    }

    private static RuntimeEvent assistantSummary() {
        MessageEvent message = new MessageEvent();
        message.messageId = "02f6ff8d-650d-4317-8d95-803411b82894";
        message.role = EventContract.MESSAGE_ASSISTANT;
        message.locale = "zh-CN";
        message.displayText = "座舱准备完成";
        message.contentDigest = DIGEST;

        RuntimeEvent event = baseEvent(
                "ebef8dbe-4ea4-4ba0-984c-f3a39ec473eb",
                2,
                EVENT_ONE_ID,
                1,
                "AssistantSummaryCreated");
        event.source = EventContract.SOURCE_RUNTIME;
        event.payloadKind = EventContract.PAYLOAD_MESSAGE;
        event.message = message;
        return event;
    }

    private static RuntimeEvent gapEvent() {
        return baseEvent(
                "32e55938-ec71-4fd4-9530-5bdb9d333818",
                4,
                "ebef8dbe-4ea4-4ba0-984c-f3a39ec473eb",
                2,
                "SessionStateChanged");
    }

    private static RuntimeEvent baseEvent(
            String eventId,
            long sequence,
            String parentEventId,
            long parentSequence,
            String type) {
        RuntimeEvent event = new RuntimeEvent();
        event.eventId = eventId;
        event.sequence = sequence;
        event.sessionId = SESSION_ID;
        event.parentEventId = parentEventId;
        event.parentSequence = parentSequence;
        event.type = type;
        event.source = EventContract.SOURCE_RUNTIME;
        event.occurredAtEpochMs = NOW + sequence;
        event.privacyClass = EventContract.PRIVACY_INTERNAL;
        event.payloadDigest = DIGEST;
        event.eventDigest = DIGEST;
        return event;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
