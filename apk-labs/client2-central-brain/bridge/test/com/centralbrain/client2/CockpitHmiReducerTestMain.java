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
        verifyHvacReduction();
        verifySeatReduction();

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
        System.out.println("cockpit_hvac_intent_roundtrip_verified=true");
        System.out.println("cockpit_hvac_desired_reducer_verified=true");
        System.out.println("cockpit_hvac_reported_readback_available=false");
        System.out.println("cockpit_hvac_verified_before_readback=false");
        System.out.println("cockpit_seat_intent_roundtrip_verified=true");
        System.out.println("cockpit_seat_heat_vent_mutex_verified=true");
        System.out.println("cockpit_seat_unknown_restricted_fail_closed=true");
        System.out.println("cockpit_seat_parked_rest_approval_required=true");
        System.out.println("cockpit_seat_desired_reducer_verified=true");
        System.out.println("cockpit_seat_reported_readback_available=false");
        System.out.println("cockpit_seat_verified_before_readback=false");
        System.out.println("scenario_execution_enabled=false");
        System.out.println("hardware_accessed=false");
    }

    private static void verifyHvacReduction() {
        HvacControlIntent initialIntent = HvacControlIntent.defaults();
        String wire = initialIntent.toWireValue();
        check(initialIntent.equals(HvacControlIntent.parseWireValue(wire)),
                "HVAC wire value must round trip exactly");
        check(initialIntent.stepTemperature(-100).getTemperatureDeciC()
                        == HvacControlIntent.MIN_TEMP_DECI_C,
                "HVAC temperature step must clamp to the lower bound");
        check(initialIntent.stepFan(100).getFanLevel() == HvacControlIntent.MAX_FAN_LEVEL,
                "HVAC fan step must clamp to the upper bound");
        expectRejected(() -> HvacControlIntent.parseWireValue(
                wire.replace("temp_deci_c=225", "temp_deci_c=226")));

        CockpitHmiState state = CockpitHmiState.initial();
        HvacControlIntent changed = initialIntent.stepTemperature(1).stepFan(1);
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.hvacDesiredChanged(changed));
        check(state.getHvacState().getDesiredRevision() == 1,
                "HVAC desired revision must increment once");
        check(state.getHvacState().getRequestState()
                        == CockpitHvacState.RequestState.DEBOUNCING,
                "HVAC desired change must enter debounce state");
        check(state.getDeviceDrawer() == CockpitHmiState.DeviceDrawer.HVAC,
                "HVAC desired change must keep the HVAC drawer visible");

        CockpitHmiState duplicate = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.hvacDesiredChanged(changed));
        check(duplicate == state, "equal HVAC desired state must not create a revision");

        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.hvacManualSubmitted(1));
        check("manual.hvac".equals(state.getUiScenarioId()),
                "manual HVAC must use the governed scenario alias");
        check(state.getHvacState().getRequestState()
                        == CockpitHvacState.RequestState.SUBMITTING,
                "manual HVAC request must be submitting");
        check(state.getDeviceDrawer() == CockpitHmiState.DeviceDrawer.HVAC,
                "manual HVAC submission must not close the drawer");
        check(!state.getHvacState().hasReportedEvidence(),
                "manual request must not synthesize HVAC readback");
        check(state.getHvacState().getEffectState()
                        == CockpitHvacState.EffectState.NOT_DISPATCHED,
                "manual request must not claim Effect dispatch");

        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.sessionOpened(
                        handle(), "scene.manual.hvac.adjust.v1"));
        check(state.getHvacState().getRequestState()
                        == CockpitHvacState.RequestState.ACCEPTED,
                "Session admission must be distinct from vehicle execution");
        check(state.getHvacState().getEffectState()
                        == CockpitHvacState.EffectState.REQUESTED,
                "accepted manual Session may only project REQUESTED");
        check(!state.getHvacState().hasReportedEvidence(),
                "Session admission must not create reported HVAC state");

        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.hvacDesiredChanged(changed.stepTemperature(1)));
        check(state.getHvacState().getRequestState()
                        == CockpitHvacState.RequestState.DEBOUNCING,
                "a new HVAC edit must supersede accepted request state");
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.snapshot(snapshot(
                        ICentralBrainSessionRuntime.SESSION_STATE_EXECUTING,
                        "Older Session remains accepted")));
        check(state.getHvacState().getRequestState()
                        == CockpitHvacState.RequestState.DEBOUNCING,
                "an old Session snapshot must not cancel a pending HVAC debounce");
    }

    private static void verifySeatReduction() {
        SeatControlIntent initialIntent = SeatControlIntent.defaults();
        String wire = initialIntent.toWireValue();
        check(initialIntent.equals(SeatControlIntent.parseWireValue(wire)),
                "seat wire value must round trip exactly");
        check(initialIntent.stepHeat(100).getHeatLevel()
                        == SeatControlIntent.MAX_COMFORT_LEVEL,
                "seat heat step must clamp to the upper bound");
        SeatControlIntent ventilated = initialIntent.stepHeat(1).stepVentilation(1);
        check(ventilated.getHeatLevel() == 0 && ventilated.getVentilationLevel() == 1,
                "seat ventilation must clear heat");
        SeatControlIntent heated = ventilated.stepHeat(1);
        check(heated.getHeatLevel() == 1 && heated.getVentilationLevel() == 0,
                "seat heat must clear ventilation");
        expectRejected(() -> SeatControlIntent.parseWireValue(
                wire.replace("heat=0", "heat=4")));

        CockpitHmiState state = CockpitHmiState.initial();
        SeatControlIntent reclined = initialIntent.stepRecline(1);
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.seatDesiredChanged(reclined));
        check(state.getSeatState().getDesiredRevision() == 0
                        && state.getSeatState().getDesired().getReclineDegrees() == 0,
                "unknown driving context must not change driver recline desired state");
        check(state.getSeatState().getRequestState() == CockpitSeatState.RequestState.BLOCKED
                        && state.getSeatState().getSafetyDecision()
                        == CockpitSeatState.SafetyDecision.DENIED_UNKNOWN_CONTEXT,
                "unknown driving context must fail closed");

        SeatControlIntent lowRisk = initialIntent.stepHeat(1);
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.seatDesiredChanged(lowRisk));
        check(state.getSeatState().getDesiredRevision() == 1
                        && state.getSeatState().getRequestState()
                        == CockpitSeatState.RequestState.DEBOUNCING,
                "low-risk seat comfort change must enter debounce");
        check(state.getDeviceDrawer() == CockpitHmiState.DeviceDrawer.SEAT,
                "seat desired change must keep the seat drawer visible");

        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.seatManualSubmitted(1));
        check("manual.seat".equals(state.getUiScenarioId()),
                "manual seat must use the governed scenario alias");
        check(state.getSeatState().getRequestState()
                        == CockpitSeatState.RequestState.SUBMITTING,
                "manual seat request must be submitting");
        check(!state.getSeatState().hasReportedEvidence()
                        && state.getSeatState().getEffectState()
                        == CockpitSeatState.EffectState.NOT_DISPATCHED,
                "manual seat request must not synthesize dispatch or readback");

        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.sessionOpened(
                        handle(), "scene.manual.seat.adjust.v1"));
        check(state.getSeatState().getRequestState()
                        == CockpitSeatState.RequestState.ACCEPTED
                        && state.getSeatState().getEffectState()
                        == CockpitSeatState.EffectState.REQUESTED,
                "seat Session admission may only project REQUESTED");

        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.seatDesiredChanged(lowRisk.stepVentilation(1)));
        check(state.getSeatState().getRequestState()
                        == CockpitSeatState.RequestState.DEBOUNCING,
                "a new seat edit must supersede accepted request state");
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.snapshot(snapshot(
                        ICentralBrainSessionRuntime.SESSION_STATE_EXECUTING,
                        "Older Session remains accepted")));
        check(state.getSeatState().getRequestState()
                        == CockpitSeatState.RequestState.DEBOUNCING,
                "an old Session snapshot must not cancel a pending seat debounce");

        CockpitSeatState.SafetyContext parked = CockpitSeatState.SafetyContext.observed(
                CockpitSeatState.DrivingState.PARKED,
                CockpitSeatState.OccupancyState.OCCUPIED,
                CockpitSeatState.BeltState.UNBELTED,
                CockpitSeatState.EvidenceSource.TARGET,
                7);
        CockpitHmiState parkedState = CockpitHmiReducer.reduce(
                CockpitHmiState.initial(),
                CockpitHmiReducer.Event.seatSafetyContextChanged(parked));
        parkedState = CockpitHmiReducer.reduce(
                parkedState,
                CockpitHmiReducer.Event.seatDesiredChanged(
                        SeatControlIntent.defaults().applyPreset(SeatControlIntent.Preset.REST)));
        check(parkedState.getSeatState().getRequestState()
                        == CockpitSeatState.RequestState.WAITING_APPROVAL
                        && parkedState.getSeatState().getSafetyDecision()
                        == CockpitSeatState.SafetyDecision.APPROVAL_REQUIRED,
                "parked rest preset must wait for approval rather than submit");

        CockpitSeatState.SafetyContext moving = CockpitSeatState.SafetyContext.observed(
                CockpitSeatState.DrivingState.MOVING,
                CockpitSeatState.OccupancyState.OCCUPIED,
                CockpitSeatState.BeltState.UNBELTED,
                CockpitSeatState.EvidenceSource.TARGET,
                8);
        CockpitHmiState movingState = CockpitHmiReducer.reduce(
                CockpitHmiState.initial(),
                CockpitHmiReducer.Event.seatSafetyContextChanged(moving));
        movingState = CockpitHmiReducer.reduce(
                movingState,
                CockpitHmiReducer.Event.seatDesiredChanged(reclined));
        check(movingState.getSeatState().getSafetyDecision()
                        == CockpitSeatState.SafetyDecision.DENIED_MOVING_DRIVER,
                "moving driver recline must fail closed");
    }

    private static void expectRejected(Runnable action) {
        try {
            action.run();
            throw new AssertionError("invalid control value must be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected fail-closed validation.
        }
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
