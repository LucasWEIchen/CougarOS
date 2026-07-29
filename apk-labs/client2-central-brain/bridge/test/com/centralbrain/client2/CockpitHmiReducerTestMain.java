package com.centralbrain.client2;

import com.centralbrain.sdk.event.ActionEvent;
import com.centralbrain.sdk.event.EventContract;
import com.centralbrain.sdk.event.MessageEvent;
import com.centralbrain.sdk.event.ObservationEvent;
import com.centralbrain.sdk.event.RuntimeEvent;
import com.centralbrain.sdk.session.ICentralBrainSessionRuntime;
import com.centralbrain.sdk.session.SessionHandle;
import com.centralbrain.sdk.session.SessionSnapshot;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

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
        check(state.getTextInputVisibility()
                        == CockpitHmiState.TextInputVisibility.HIDDEN,
                "initial free-form input must be hidden");
        check(state.getSurfaceStage() == CockpitHmiState.SurfaceStage.INTENT,
                "intent must be the initial surface");
        check(state.getPresentationMode() == PanelPresentationMode.MOVING_RESTRICTED,
                "missing driving evidence must default to restricted presentation");
        verifyDisplayPolicy();
        verifyScenarioControlSynchronization();
        verifyDrivingUxPolicy();
        verifyEngineerSimulationReduction();
        verifyHvacReduction();
        verifySeatReduction();
        verifyExecutionTimeline();
        verifyRecoveryUxReduction();
        verifySimulatedScenarioReduction();

        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.textInputVisibility(true));
        check(state.getTextInputVisibility()
                        == CockpitHmiState.TextInputVisibility.VISIBLE
                        && state.getPanelVisibility()
                        == CockpitHmiState.PanelVisibility.HIDDEN,
                "phone input must exclude the task panel");
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.panelVisibility(true));
        check(state.getTextInputVisibility()
                        == CockpitHmiState.TextInputVisibility.HIDDEN,
                "navigation panel must exclude free-form input");
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
        check(state.getExecutionTimeline()
                        .getStage(CockpitExecutionTimeline.Phase.INTENT).getStatus()
                        == CockpitExecutionTimeline.Status.SESSION_ACCEPTED,
                "replayed ScenarioRequested must not downgrade Session admission");
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
        System.out.println("cockpit_scenario_control_state_reducer_owned=true");
        System.out.println("cockpit_scenario_catalog_normalized=true");
        System.out.println("cockpit_scenario_manual_shared_client=true");
        System.out.println("cockpit_scenario_device_session_synchronized=true");
        System.out.println("cockpit_scenario_plan_publication_inferred=false");
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
        System.out.println("cockpit_execution_timeline_reducer_owned=true");
        System.out.println("cockpit_execution_fail_closed_projection_verified=true");
        System.out.println("cockpit_execution_typed_event_projection_verified=true");
        System.out.println("cockpit_execution_trace_bounded_verified=true");
        System.out.println("cockpit_recovery_state_reducer_owned=true");
        System.out.println("cockpit_approval_details_fail_closed_verified=true");
        System.out.println("cockpit_partial_outcome_projection_verified=true");
        System.out.println("cockpit_compensation_projection_verified=true");
        System.out.println("cockpit_approval_response_service_published=false");
        System.out.println("cockpit_retry_service_published=false");
        System.out.println("cockpit_undo_service_published=false");
        System.out.println("cockpit_recovery_commands_enabled=false");
        System.out.println("cockpit_driving_ux_policy_verified=true");
        System.out.println("cockpit_unknown_driving_restricted_verified=true");
        System.out.println("cockpit_moving_long_text_hidden_verified=true");
        System.out.println("cockpit_high_risk_controls_disabled_verified=true");
        System.out.println("cockpit_runtime_policy_authority_independent=true");
        System.out.println("cockpit_display_matrix_defined=true");
        System.out.println("cockpit_display_profile_count=3");
        System.out.println("cockpit_display_large_text_1_3_verified=true");
        System.out.println("cockpit_display_unsupported_fail_closed=true");
        System.out.println("cockpit_display_effect_authorization_source=false");
        System.out.println("cockpit_simulated_scenario_projection_reducer_owned=true");
        System.out.println("cockpit_simulated_scenario_seven_stage_projection_verified=true");
        System.out.println("cockpit_simulated_scenario_partial_projection_verified=true");
        System.out.println("cockpit_simulated_graph_revision_bound_independent=true");
        System.out.println("cockpit_simulated_approval_input_explicit=true");
        System.out.println("cockpit_simulated_hardware_effect_dispatch_enabled=false");
        System.out.println("scenario_execution_enabled=false");
        System.out.println("hardware_accessed=false");
    }

    private static void verifySimulatedScenarioReduction() {
        CockpitHmiState state = CockpitHmiReducer.reduce(
                CockpitHmiState.initial(),
                CockpitHmiReducer.Event.simulatedRuntimeAvailability(true, ""));
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.scenarioSubmitted("care.cold", "PARKED"));
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.simulatedScenarioSnapshot(
                        simulationProjection(
                                "care.cold",
                                CockpitSimulatedScenarioState.Lifecycle.COMPLETED,
                                CockpitSimulatedScenarioState.PendingStage.NONE,
                                "",
                                3, 3, 3, 0, 0)));

        CockpitSimulatedScenarioState cold = state.getSimulatedScenarioState();
        check(cold.isRuntimeAvailable()
                        && cold.getLifecycle()
                        == CockpitSimulatedScenarioState.Lifecycle.COMPLETED
                        && cold.getEffectDispatchCount() == 3
                        && cold.getReadbackMatchCount() == 3
                        && cold.isSimulatedOnly()
                        && !cold.isHardwareAccessed(),
                "cold simulation must preserve count evidence and false hardware authority");
        check(state.getSurfaceStage() == CockpitHmiState.SurfaceStage.EXECUTION,
                "simulated metadata must reveal the automatic execution chain");
        check(state.getExecutionTimeline()
                        .getStage(CockpitExecutionTimeline.Phase.CONTEXT).getStatus()
                        == CockpitExecutionTimeline.Status.CAPTURED
                        && state.getExecutionTimeline()
                        .getStage(CockpitExecutionTimeline.Phase.PLAN).getStatus()
                        == CockpitExecutionTimeline.Status.PUBLISHED
                        && state.getExecutionTimeline()
                        .getStage(CockpitExecutionTimeline.Phase.GRAPH).getStatus()
                        == CockpitExecutionTimeline.Status.VERIFIED
                        && state.getExecutionTimeline()
                        .getStage(CockpitExecutionTimeline.Phase.EFFECT).getStatus()
                        == CockpitExecutionTimeline.Status.APPLIED
                        && state.getExecutionTimeline()
                        .getStage(CockpitExecutionTimeline.Phase.READBACK).getStatus()
                        == CockpitExecutionTimeline.Status.VERIFIED,
                "simulated metadata must project Context through readback without payloads");

        CockpitHmiState fatigue = CockpitHmiReducer.reduce(
                CockpitHmiState.initial(),
                CockpitHmiReducer.Event.simulatedRuntimeAvailability(true, ""));
        fatigue = CockpitHmiReducer.reduce(
                fatigue,
                CockpitHmiReducer.Event.scenarioSubmitted("care.fatigue", "PARKED"));
        fatigue = CockpitHmiReducer.reduce(
                fatigue,
                CockpitHmiReducer.Event.simulatedScenarioSnapshot(
                        simulationProjection(
                                "care.fatigue",
                                CockpitSimulatedScenarioState.Lifecycle.WAITING_APPROVAL,
                                CockpitSimulatedScenarioState.PendingStage.APPROVAL,
                                "vehicle.seat.recline",
                                0, 0, 0, 0, 0)));
        check(fatigue.getSimulatedScenarioState().isApprovalInputEnabled()
                        && fatigue.getExecutionTimeline()
                        .getStage(CockpitExecutionTimeline.Phase.POLICY).getStatus()
                        == CockpitExecutionTimeline.Status.APPROVAL_REQUIRED
                        && fatigue.getExecutionTimeline()
                        .getStage(CockpitExecutionTimeline.Phase.EFFECT).getStatus()
                        == CockpitExecutionTimeline.Status.NOT_DISPATCHED,
                "parked fatigue must expose explicit debug approval before Effect dispatch");

        fatigue = CockpitHmiReducer.reduce(
                fatigue,
                CockpitHmiReducer.Event.simulatedScenarioSnapshot(
                        simulationProjection(
                                "care.fatigue",
                                CockpitSimulatedScenarioState.Lifecycle.PARTIAL,
                                CockpitSimulatedScenarioState.PendingStage.NONE,
                                "",
                                4, 2, 2, 1, 0)));
        check(fatigue.getSimulatedScenarioState().getLifecycle()
                        == CockpitSimulatedScenarioState.Lifecycle.PARTIAL
                        && !fatigue.getSimulatedScenarioState().isApprovalInputEnabled()
                        && fatigue.getExecutionTimeline()
                        .getStage(CockpitExecutionTimeline.Phase.GRAPH).getStatus()
                        == CockpitExecutionTimeline.Status.SKIPPED,
                "skipped approval must project Partial without retaining command authority");
        CockpitHmiState detached = CockpitHmiReducer.reduce(
                fatigue, CockpitHmiReducer.Event.detached());
        check(!detached.getSimulatedScenarioState().isRuntimeAvailable()
                        && !detached.getSimulatedScenarioState().isApprovalInputEnabled(),
                "lifecycle detach must disable debug approval input");
    }

    private static CockpitSimulatedScenarioState.Projection simulationProjection(
            String uiScenarioId,
            CockpitSimulatedScenarioState.Lifecycle lifecycle,
            CockpitSimulatedScenarioState.PendingStage pendingStage,
            String pendingCapabilityId,
            int effectCount,
            int readbackAttemptCount,
            int readbackMatchCount,
            int approvalInputCount,
            int failureCount) {
        return CockpitSimulatedScenarioState.Projection.create(
                uiScenarioId,
                "care.cold".equals(uiScenarioId)
                        ? "scene.comfort.cold.v1" : "scene.fatigue.assist.v1",
                "PARKED",
                lifecycle,
                pendingStage,
                pendingCapabilityId,
                1,
                96,
                3,
                effectCount,
                readbackAttemptCount,
                readbackMatchCount,
                approvalInputCount,
                failureCount,
                "",
                "",
                false,
                0L);
    }

    private static void verifyDisplayPolicy() {
        CockpitDisplayPolicy compact = CockpitDisplayPolicy.resolve(
                1280, 720, 107, 1.0f);
        CockpitDisplayPolicy.Bounds compactBounds = compact.getPanelBoundsPixels();
        check(compact.isSupported()
                        && compact.getProfile()
                        == CockpitDisplayPolicy.Profile.COMPACT_1280_720
                        && compactBounds.getLeft() == 858
                        && compactBounds.getTop() == 134
                        && compactBounds.getRight() == 1259
                        && compactBounds.getBottom() == 642
                        && compact.getMinimumTouchTargetPixels() == 33,
                "compact display profile must use the defined density and safe frame");

        CockpitDisplayPolicy standard = CockpitDisplayPolicy.resolve(
                1920, 1080, 160, 1.0f);
        CockpitDisplayPolicy.Bounds standardBounds = standard.getPanelBoundsPixels();
        check(standard.isSupported()
                        && standard.getProfile()
                        == CockpitDisplayPolicy.Profile.STANDARD_1920_1080
                        && standardBounds.getLeft() == 1288
                        && standardBounds.getTop() == 200
                        && standardBounds.getRight() == 1888
                        && standardBounds.getBottom() == 960,
                "standard profile must preserve the approved 1920x1080 frame");

        CockpitDisplayPolicy large = CockpitDisplayPolicy.resolve(
                2560, 1440, 213, 1.0f);
        CockpitDisplayPolicy.Bounds largeBounds = large.getPanelBoundsPixels();
        check(large.isSupported()
                        && large.getProfile()
                        == CockpitDisplayPolicy.Profile.LARGE_2560_1440
                        && largeBounds.getLeft() == 1718
                        && largeBounds.getTop() == 266
                        && largeBounds.getRight() == 2517
                        && largeBounds.getBottom() == 1278,
                "large display profile must use the defined density and safe frame");

        CockpitDisplayPolicy largeText = CockpitDisplayPolicy.resolve(
                1920, 1080, 160, 1.30f);
        check(largeText.isSupported() && largeText.isLargeTextProfile(),
                "1.3 font scale must remain inside the approved matrix");
        check(!compact.isEffectAuthorizationSource()
                        && !standard.isEffectAuthorizationSource()
                        && !large.isEffectAuthorizationSource(),
                "display policy must never authorize Effect dispatch");
        check(compact.panelFitsDisplay()
                        && standard.panelFitsDisplay()
                        && large.panelFitsDisplay(),
                "all approved panel bounds must stay inside their display");

        check(!CockpitDisplayPolicy.resolve(720, 1280, 160, 1.0f).isSupported(),
                "portrait display must fail closed");
        check(!CockpitDisplayPolicy.resolve(1920, 1080, 240, 1.0f).isSupported(),
                "unapproved density must fail closed");
        check(!CockpitDisplayPolicy.resolve(1920, 1080, 161, 1.0f).isSupported(),
                "nearby density must not be rounded into an approved profile");
        check(!CockpitDisplayPolicy.resolve(1920, 1080, 160, 1.31f).isSupported(),
                "font scale above the approved maximum must fail closed");
        check(!CockpitDisplayPolicy.resolve(1366, 768, 114, 1.0f).isSupported(),
                "unlisted landscape size must fail closed");
    }

    private static void verifyScenarioControlSynchronization() {
        CockpitHmiState state = CockpitHmiReducer.reduce(
                CockpitHmiState.initial(),
                CockpitHmiReducer.Event.scenarioSubmitted("care.cold"));
        CockpitScenarioControlState control = state.getScenarioControlState();
        check(control.getOrigin() == CockpitScenarioControlState.Origin.NATURAL
                        && control.getHvacRole()
                        == CockpitScenarioControlState.DeviceRole.CATALOG_REQUIRED
                        && control.getSeatRole()
                        == CockpitScenarioControlState.DeviceRole.CATALOG_OPTIONAL,
                "cold intent must normalize to bounded catalog device roles");
        check(control.getLifecycle() == CockpitScenarioControlState.Lifecycle.REQUESTED,
                "scenario submission must synchronize the control projection");

        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.sessionOpened(handle(), "scene.comfort.cold.v1"));
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.snapshot(snapshot(
                        ICentralBrainSessionRuntime.SESSION_STATE_EXECUTING,
                        "scene.comfort.cold.v1",
                        0,
                        "Runtime has not published a Plan")));
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.runtimeEvent(scenarioRequested()));
        control = state.getScenarioControlState();
        check(control.isCatalogMatched()
                        && control.getLifecycle()
                        == CockpitScenarioControlState.Lifecycle.EXECUTING
                        && control.getLastEventSequence() == state.getLastEventSequence(),
                "the same Session event must synchronize shell and device detail state");
        check(!control.isPlanPublished()
                        && !control.isEffectDispatchEnabled()
                        && !control.isReadbackAvailable(),
                "catalog participation must not synthesize Plan, Effect or readback");
        check(state.getHvacState().getDesiredRevision() == 0
                        && state.getSeatState().getDesiredRevision() == 0,
                "natural intent must not invent typed device targets");

        CockpitHmiState fatigue = CockpitHmiReducer.reduce(
                CockpitHmiState.initial(),
                CockpitHmiReducer.Event.scenarioSubmitted("care.fatigue"));
        check(fatigue.getScenarioControlState().getHvacRole()
                        == CockpitScenarioControlState.DeviceRole.CATALOG_REQUIRED
                        && fatigue.getScenarioControlState().getSeatRole()
                        == CockpitScenarioControlState.DeviceRole.CATALOG_OPTIONAL,
                "fatigue intent must expose bounded HVAC and seat catalog participation");
        CockpitHmiState rest = CockpitHmiReducer.reduce(
                CockpitHmiState.initial(),
                CockpitHmiReducer.Event.scenarioSubmitted("skill.nap"));
        check(rest.getScenarioControlState().getHvacRole()
                        == CockpitScenarioControlState.DeviceRole.CATALOG_REQUIRED
                        && rest.getScenarioControlState().getSeatRole()
                        == CockpitScenarioControlState.DeviceRole.CATALOG_REQUIRED,
                "rest intent must expose both required device roles without dispatch");

        HvacControlIntent changed = HvacControlIntent.defaults().stepTemperature(1);
        CockpitHmiState manual = CockpitHmiReducer.reduce(
                CockpitHmiState.initial(),
                CockpitHmiReducer.Event.hvacDesiredChanged(changed));
        manual = CockpitHmiReducer.reduce(
                manual,
                CockpitHmiReducer.Event.hvacManualSubmitted(1));
        check(manual.getScenarioControlState().getOrigin()
                        == CockpitScenarioControlState.Origin.MANUAL_HVAC
                        && manual.getScenarioControlState().getHvacRole()
                        == CockpitScenarioControlState.DeviceRole.MANUAL_TARGET,
                "manual HVAC must enter the same scenario control state");
        manual = CockpitHmiReducer.reduce(
                manual,
                CockpitHmiReducer.Event.sessionOpened(handle(), "scene.manual.hvac.adjust.v1"));
        check(manual.getScenarioControlState().getLifecycle()
                        == CockpitScenarioControlState.Lifecycle.SESSION_ACCEPTED
                        && manual.getHvacState().getRequestState()
                        == CockpitHvacState.RequestState.ACCEPTED,
                "one Session admission must synchronize manual and scenario projections");

        CockpitHmiState mismatch = CockpitHmiReducer.reduce(
                CockpitHmiState.initial(),
                CockpitHmiReducer.Event.scenarioSubmitted("care.cold"));
        mismatch = CockpitHmiReducer.reduce(
                mismatch,
                CockpitHmiReducer.Event.sessionOpened(handle(), "scene.fatigue.assist.v1"));
        check(mismatch.getConnectionState() == CockpitHmiState.ConnectionState.FAILED
                        && "CB_HMI_SCENARIO_MISMATCH".equals(mismatch.getErrorCode())
                        && mismatch.getScenarioControlState().getHvacRole()
                        == CockpitScenarioControlState.DeviceRole.NOT_INVOLVED,
                "canonical mismatch must remove device claims and fail closed");
        expectRejected(() -> CockpitHmiReducer.reduce(
                CockpitHmiState.initial(),
                CockpitHmiReducer.Event.scenarioSubmitted("unsupported.scene")));
    }

    private static void verifyDrivingUxPolicy() {
        check(DrivingUxPolicy.modeFor(null) == PanelPresentationMode.MOVING_RESTRICTED,
                "missing Context must fail closed");
        check(DrivingUxPolicy.modeFor(CockpitSeatState.SafetyContext.unavailable())
                        == PanelPresentationMode.MOVING_RESTRICTED,
                "unknown Context must be treated as moving restricted");
        check(!PanelPresentationMode.MOVING_RESTRICTED.isLongTextVisible()
                        && !PanelPresentationMode.MOVING_RESTRICTED.isParameterEditingEnabled()
                        && !PanelPresentationMode.MOVING_RESTRICTED.isHighRiskScenarioEnabled(),
                "restricted presentation must hide detail and disable risky editing");
        check(!PanelPresentationMode.PARKED_FULL.isEffectAuthorizationSource()
                        && !PanelPresentationMode.MOVING_RESTRICTED.isEffectAuthorizationSource(),
                "presentation mode must never authorize Effects");
        check(DrivingUxPolicy.isHighRiskScenario("skill.nap")
                        && !DrivingUxPolicy.isHighRiskScenario("care.fatigue"),
                "only the bounded rest scenario is high risk in this HMI catalog");
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
                        "scene.manual.hvac.adjust.v1",
                        0,
                        "Older Session remains accepted")));
        check(state.getHvacState().getRequestState()
                        == CockpitHvacState.RequestState.DEBOUNCING,
                "an old Session snapshot must not cancel a pending HVAC debounce");
    }

    private static void verifyEngineerSimulationReduction() {
        CockpitHmiState state = CockpitHmiState.initial();
        check(!state.getEngineerState().isAvailable()
                        && !state.getEngineerState().isProductionAvailable()
                        && !state.getEngineerState().isEffectAuthorizationSource(),
                "engineer simulation must start unavailable and non-authoritative");

        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.engineerConnecting());
        check(state.getEngineerState().getConnectionState()
                        == CockpitEngineerState.ConnectionState.CONNECTING,
                "debug controller connection must be reducer-owned");
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.engineerConnected(
                        0,
                        CockpitSeatState.DrivingState.UNKNOWN_RESTRICTED));
        check(state.getEngineerState().isAvailable()
                        && state.getPresentationMode()
                        == PanelPresentationMode.MOVING_RESTRICTED,
                "connected unknown Context must remain restricted");

        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.engineerDrivingApplied(
                        CockpitSeatState.DrivingState.PARKED,
                        1));
        check(state.getPresentationMode() == PanelPresentationMode.PARKED_FULL,
                "acknowledged debug PARKED Context must enable the full preview");
        check(state.getSeatState().getSafetyContext().getSource()
                        == CockpitSeatState.EvidenceSource.SIMULATED
                        && state.getSeatState().getSafetyContext().getRevision() == 1,
                "debug Context must be visibly simulated and revisioned");

        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.engineerOccupancyApplied(
                        CockpitSeatState.OccupancyState.OCCUPIED,
                        2));
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.engineerBeltApplied(
                        CockpitSeatState.BeltState.UNBELTED,
                        3));
        check(state.getSeatState().getSafetyContext().getOccupancyState()
                        == CockpitSeatState.OccupancyState.OCCUPIED
                        && state.getSeatState().getSafetyContext().getBeltState()
                        == CockpitSeatState.BeltState.UNBELTED
                        && state.getSeatState().getSafetyContext().getRevision() == 3,
                "occupancy and belt acknowledgements must update the same Context revision");

        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.engineerAdapterSelected(
                        CockpitEngineerState.AdapterTarget.SEAT));
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.engineerFaultApplied(
                        CockpitEngineerState.FaultMode.DELAY,
                        4));
        check(state.getEngineerState().getAdapterTarget()
                        == CockpitEngineerState.AdapterTarget.SEAT
                        && state.getEngineerState().getFaultMode()
                        == CockpitEngineerState.FaultMode.DELAY
                        && state.getSeatState().getSafetyContext().getRevision() == 4,
                "fault acknowledgement must retain target and advance controller Context revision");

        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.engineerDrivingApplied(
                        CockpitSeatState.DrivingState.MOVING,
                        5));
        check(state.getPresentationMode() == PanelPresentationMode.MOVING_RESTRICTED,
                "debug MOVING Context must immediately restore restricted presentation");
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.engineerResetApplied(6));
        check(state.getEngineerState().getDrivingState()
                        == CockpitSeatState.DrivingState.UNKNOWN_RESTRICTED
                        && state.getSeatState().getSafetyContext().getSource()
                        == CockpitSeatState.EvidenceSource.UNAVAILABLE,
                "reset must clear simulated Context and fail closed");
        CockpitHmiState resetState = state;
        expectRejected(() -> CockpitHmiReducer.reduce(
                resetState,
                CockpitHmiReducer.Event.engineerFaultApplied(
                        CockpitEngineerState.FaultMode.TIMEOUT,
                        6)));

        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.engineerFailure("CB_SIM_BINDING_DIED"));
        check(!state.getEngineerState().isAvailable()
                        && state.getEngineerState().getControllerRevision() == 0
                        && state.getDeviceDrawer() == CockpitHmiState.DeviceDrawer.CLOSED
                        && state.getPresentationMode()
                        == PanelPresentationMode.MOVING_RESTRICTED,
                "controller loss must hide engineering controls and fail closed");
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
                        "scene.manual.seat.adjust.v1",
                        0,
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
        check(parkedState.getPresentationMode() == PanelPresentationMode.PARKED_FULL,
                "trusted parked Context must enable full presentation only");
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
        check(movingState.getPresentationMode() == PanelPresentationMode.MOVING_RESTRICTED,
                "moving Context must select restricted presentation");
        movingState = CockpitHmiReducer.reduce(
                movingState,
                CockpitHmiReducer.Event.seatDesiredChanged(reclined));
        check(movingState.getSeatState().getSafetyDecision()
                        == CockpitSeatState.SafetyDecision.DENIED_MOVING_DRIVER,
                "moving driver recline must fail closed");
    }

    private static void verifyExecutionTimeline() {
        CockpitExecutionTimeline timeline = CockpitExecutionTimeline.initial();
        check(timeline.getStage(CockpitExecutionTimeline.Phase.PLAN).getStatus()
                        == CockpitExecutionTimeline.Status.NOT_PUBLISHED,
                "initial plan must not be presented as published");
        check(timeline.getStage(CockpitExecutionTimeline.Phase.GRAPH).getStatus()
                        == CockpitExecutionTimeline.Status.NOT_WIRED,
                "initial graph must remain unwired");
        check(timeline.getStage(CockpitExecutionTimeline.Phase.EFFECT).getStatus()
                        == CockpitExecutionTimeline.Status.NOT_DISPATCHED,
                "initial effect must remain undispatched");
        check(timeline.getStage(CockpitExecutionTimeline.Phase.READBACK).getStatus()
                        == CockpitExecutionTimeline.Status.UNAVAILABLE,
                "initial readback must remain unavailable");

        timeline = timeline.scenarioRequested("care.fatigue")
                .sessionOpened("scene.care.fatigue.v1")
                .snapshot(ICentralBrainSessionRuntime.SESSION_STATE_EXECUTING, 0);
        check(timeline.getStage(CockpitExecutionTimeline.Phase.INTENT).getStatus()
                        == CockpitExecutionTimeline.Status.SESSION_ACCEPTED,
                "Session admission must be visible on the intent stage");
        check(timeline.getStage(CockpitExecutionTimeline.Phase.PLAN).getStatus()
                        == CockpitExecutionTimeline.Status.NOT_PUBLISHED,
                "Session admission must not synthesize a plan");

        RuntimeEvent[] events = new RuntimeEvent[] {
                typedEvent(1, "ContextCaptured", EventContract.SOURCE_RUNTIME),
                typedEvent(2, "PlanCompiled", EventContract.SOURCE_SCENARIO),
                actionEvent(3, "ActionProposed", EventContract.ACTION_PROPOSED, true),
                actionEvent(4, "ActionAuthorized", EventContract.ACTION_AUTHORIZED, true),
                typedEvent(5, "EffectPrepared", EventContract.SOURCE_GRAPH),
                typedEvent(6, "EffectDispatched", EventContract.SOURCE_ADAPTER),
                observationEvent(7, "EffectObserved", EventContract.SUBJECT_EFFECT,
                        EventContract.OUTCOME_OBSERVED, EventContract.QUALITY_FRESH),
                observationEvent(8, "EffectVerified", EventContract.SUBJECT_EFFECT,
                        EventContract.OUTCOME_VERIFIED, EventContract.QUALITY_FRESH),
                actionEvent(9, "ActionRejected", EventContract.ACTION_REJECTED, false),
                typedEvent(10, "CompensationStarted", EventContract.SOURCE_GOVERNANCE),
                observationEvent(11, "CompensationObserved",
                        EventContract.SUBJECT_COMPENSATION,
                        EventContract.OUTCOME_VERIFIED, EventContract.QUALITY_FRESH)
        };
        for (RuntimeEvent event : events) {
            timeline = timeline.runtimeEvent(
                    CockpitExecutionTimeline.ProjectedEvent.from(event));
        }
        check(timeline.getStage(CockpitExecutionTimeline.Phase.PLAN).getStatus()
                        == CockpitExecutionTimeline.Status.COMPILED,
                "typed plan event must project COMPILED");
        check(timeline.getStage(CockpitExecutionTimeline.Phase.POLICY).getStatus()
                        == CockpitExecutionTimeline.Status.SKIPPED,
                "optional rejection must project SKIPPED");
        check(timeline.getStage(CockpitExecutionTimeline.Phase.EFFECT).getStatus()
                        == CockpitExecutionTimeline.Status.COMPENSATED,
                "fresh compensation observation must project COMPENSATED");
        check("media.stop".equals(
                        timeline.getStage(CockpitExecutionTimeline.Phase.EFFECT).getTarget()),
                "payload-free effect lifecycle must inherit the typed action capability");
        check(timeline.getStage(CockpitExecutionTimeline.Phase.READBACK).getStatus()
                        == CockpitExecutionTimeline.Status.COMPENSATED,
                "compensation readback must be visible");
        check(timeline.getTraceItems().size() == CockpitExecutionTimeline.MAX_TRACE_ITEMS,
                "typed trace must remain bounded");
        check(timeline.getTraceItems().get(0).getSequence() == 4,
                "bounded trace must retain the newest eight events");

        CockpitExecutionTimeline conflict = CockpitExecutionTimeline.initial()
                .runtimeEvent(CockpitExecutionTimeline.ProjectedEvent.from(
                        observationEvent(1, "EffectVerified", EventContract.SUBJECT_EFFECT,
                                EventContract.OUTCOME_VERIFIED,
                                EventContract.QUALITY_CONFLICT)));
        check(conflict.getStage(CockpitExecutionTimeline.Phase.READBACK).getStatus()
                        == CockpitExecutionTimeline.Status.MISMATCH,
                "conflicting verification evidence must not project VERIFIED");
    }

    private static void verifyRecoveryUxReduction() {
        CockpitHmiState state = CockpitHmiReducer.reduce(
                CockpitHmiState.initial(),
                CockpitHmiReducer.Event.panelVisibility(true));
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.scenarioSubmitted("care.fatigue"));
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.sessionOpened(
                        handle(), "scene.fatigue.assist.v1"));
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.snapshot(snapshot(
                        ICentralBrainSessionRuntime.SESSION_STATE_WAITING_FOR_CONFIRMATION,
                        "scene.fatigue.assist.v1",
                        0,
                        "Waiting for confirmation")));

        CockpitRecoveryState recovery = state.getRecoveryState();
        check(recovery.getApprovalStatus() == CockpitRecoveryState.ApprovalStatus.REQUESTED,
                "waiting snapshot must expose an approval request");
        check("UNAVAILABLE".equals(recovery.getApprovalReasonCode())
                        && "UNAVAILABLE".equals(recovery.getApprovalTarget())
                        && recovery.getApprovalExpiresAtEpochMs() == 0,
                "missing ApprovalPrompt details must remain explicitly unavailable");
        check(!recovery.isApproveEnabled()
                        && !recovery.isRejectEnabled()
                        && !recovery.isRetryEnabled()
                        && !recovery.isUndoEnabled(),
                "unpublished recovery command services must stay disabled");

        RuntimeEvent[] events = new RuntimeEvent[] {
                actionEvent(1, "ActionProposed", EventContract.ACTION_PROPOSED, true),
                typedEvent(2, "ApprovalRequested", EventContract.SOURCE_GOVERNANCE),
                typedEvent(3, "ApprovalResolved", EventContract.SOURCE_GOVERNANCE),
                observationEvent(4, "EffectVerified", EventContract.SUBJECT_EFFECT,
                        EventContract.OUTCOME_VERIFIED, EventContract.QUALITY_FRESH),
                observationEvent(5, "EffectFailed", EventContract.SUBJECT_EFFECT,
                        EventContract.OUTCOME_FAILED, EventContract.QUALITY_FRESH),
                typedEvent(6, "CompensationStarted", EventContract.SOURCE_GOVERNANCE),
                observationEvent(7, "CompensationObserved",
                        EventContract.SUBJECT_COMPENSATION,
                        EventContract.OUTCOME_VERIFIED, EventContract.QUALITY_FRESH)
        };
        for (RuntimeEvent event : events) {
            state = CockpitHmiReducer.reduce(
                    state,
                    CockpitHmiReducer.Event.runtimeEvent(event));
        }
        recovery = state.getRecoveryState();
        check(recovery.getApprovalStatus() == CockpitRecoveryState.ApprovalStatus.RESOLVED
                        && "media.stop".equals(recovery.getApprovalTarget()),
                "approval state must inherit only the validated capability target");
        check(recovery.getVerifiedCount() == 1
                        && recovery.getFailedCount() == 1
                        && recovery.getInconclusiveCount() == 0,
                "terminal typed effect evidence must be counted without payload text");
        check(recovery.getAggregateStatus()
                        == CockpitRecoveryState.AggregateStatus.PARTIALLY_COMPLETED,
                "mixed verified/failed evidence must project partial completion");
        check(recovery.getCompensationStatus()
                        == CockpitRecoveryState.CompensationStatus.COMPENSATED,
                "fresh compensation observation must project compensated");
        check(!recovery.isRetryEnabled() && !recovery.isUndoEnabled(),
                "effect failure or compensation must not synthesize retry/undo authority");

        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.snapshot(snapshot(
                        ICentralBrainSessionRuntime.SESSION_STATE_PARTIALLY_COMPLETED,
                        "Partial")));
        check(state.getRecoveryState().getAggregateStatus()
                        == CockpitRecoveryState.AggregateStatus.PARTIALLY_COMPLETED,
                "Session partial aggregate must remain visible");
        CockpitRecoveryState beforeHide = state.getRecoveryState();
        state = CockpitHmiReducer.reduce(
                state,
                CockpitHmiReducer.Event.panelVisibility(false));
        check(state.getRecoveryState() == beforeHide,
                "outside dismiss must preserve recovery and approval state");
    }

    private static RuntimeEvent typedEvent(long sequence, String type, int source) {
        RuntimeEvent event = chainedEvent(sequence, type);
        event.source = source;
        return event;
    }

    private static RuntimeEvent actionEvent(
            long sequence,
            String type,
            int state,
            boolean required) {
        ActionEvent action = new ActionEvent();
        action.actionId = uuid("action-" + sequence);
        action.nodeId = "comfort-node";
        action.capabilityId = "media.stop";
        action.state = state;
        action.actionDigest = DIGEST;
        action.required = required;
        RuntimeEvent event = chainedEvent(sequence, type);
        event.source = EventContract.SOURCE_GOVERNANCE;
        event.payloadKind = EventContract.PAYLOAD_ACTION;
        event.action = action;
        return event;
    }

    private static RuntimeEvent observationEvent(
            long sequence,
            String type,
            int subjectType,
            int outcome,
            int quality) {
        ObservationEvent observation = new ObservationEvent();
        observation.observationId = uuid("observation-" + sequence + '-' + type);
        observation.subjectType = subjectType;
        observation.subjectId = uuid("subject-" + sequence + '-' + type);
        observation.outcome = outcome;
        observation.quality = quality;
        observation.evidenceDigest = DIGEST;
        RuntimeEvent event = chainedEvent(sequence, type);
        event.source = EventContract.SOURCE_ADAPTER;
        event.payloadKind = EventContract.PAYLOAD_OBSERVATION;
        event.observation = observation;
        return event;
    }

    private static RuntimeEvent chainedEvent(long sequence, String type) {
        return baseEvent(
                uuid("timeline-event-" + sequence),
                sequence,
                sequence == 1 ? "" : uuid("timeline-event-" + (sequence - 1)),
                sequence == 1 ? 0 : sequence - 1,
                type);
    }

    private static String uuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString();
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
        return snapshot(state, "scene.comfort.cold.v1", 0, summary);
    }

    private static SessionSnapshot snapshot(
            int state,
            String scenarioId,
            int activePlanRevision,
            String summary) {
        SessionSnapshot snapshot = new SessionSnapshot();
        snapshot.sessionId = SESSION_ID;
        snapshot.requestId = REQUEST_ID;
        snapshot.scenarioId = scenarioId;
        snapshot.state = state;
        snapshot.activePlanRevision = activePlanRevision;
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
