package com.centralbrain.client2;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.centralbrain.sdk.event.RuntimeEvent;
import com.centralbrain.sdk.session.SessionContract;
import com.centralbrain.sdk.session.SessionHandle;
import com.centralbrain.sdk.session.SessionSnapshot;

import java.lang.ref.WeakReference;

/** Maintained Java owner for Client2 overlay state, rendering and Session lifecycle. */
public final class CockpitControlCoordinator implements
        View.OnClickListener,
        ScenarioCallback,
        Application.ActivityLifecycleCallbacks {
    private static final String TAG = "CbClient2Hmi";
    private static final String MENU_TAG = "central_brain_menu_toggle";
    private static final String PANEL_CLOSE_TAG = "central_brain_panel_close";
    private static final String DRAWER_CLOSE_TAG = "central_brain_drawer_close";
    private static final String INTENT_STAGE_TAG = "central_brain_stage_intent";
    private static final String PLAN_STAGE_TAG = "central_brain_stage_plan";
    private static final String EXECUTION_STAGE_TAG = "central_brain_stage_execution";
    private static final String RESULT_STAGE_TAG = "central_brain_stage_result";
    private static final String HVAC_DETAIL_TAG = "central_brain_detail_hvac";
    private static final String SEAT_DETAIL_TAG = "central_brain_detail_seat";
    private static final String HVAC_TAG_PREFIX = "central_brain_hvac_";
    private static final String SEAT_TAG_PREFIX = "central_brain_seat_";
    private static final String RECOVERY_TAG_PREFIX = "central_brain_recovery_";
    private static final long HVAC_DEBOUNCE_MS = 300L;
    private static final long SEAT_DEBOUNCE_MS = 300L;
    private static final String PREFS_NAME = "central_brain_hmi_state_v1";
    private static final int CHECKPOINT_SCHEMA = 1;
    private static final Object ACTIVE_LOCK = new Object();

    private static WeakReference<CockpitControlCoordinator> active = new WeakReference<>(null);
    private static CockpitHmiState retainedState = CockpitHmiState.initial();

    private final Activity activity;
    private final Application application;
    private final SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable submitHvacRunnable = this::submitPendingHvac;
    private final Runnable submitSeatRunnable = this::submitPendingSeat;

    private CockpitHmiState state;
    private Client2ScenarioBridge.SessionConnection connection;
    private TextView replyView;
    private TextView sourceView;
    private TextView drivingView;
    private TextView restrictionView;
    private TextView connectionView;
    private TextView intentPreviewView;
    private TextView intentChainView;
    private TextView contextView;
    private TextView planSummaryView;
    private TextView planChainView;
    private TextView executionSummaryView;
    private TextView executionChainView;
    private TextView executionActionsView;
    private TextView approvalStateView;
    private TextView partialStateView;
    private TextView compensationStateView;
    private TextView timelineIntentView;
    private TextView timelineContextView;
    private TextView timelinePlanView;
    private TextView timelinePolicyView;
    private TextView timelineGraphView;
    private TextView timelineEffectView;
    private TextView timelineReadbackView;
    private TextView resultSummaryView;
    private TextView resultEvidenceView;
    private TextView sessionStripTitleView;
    private TextView drawerTitleView;
    private TextView hvacDesiredView;
    private TextView hvacTemperatureView;
    private TextView hvacFanView;
    private TextView hvacModesView;
    private TextView hvacEvidenceView;
    private TextView hvacRequestView;
    private TextView seatDesiredView;
    private TextView seatHeatView;
    private TextView seatVentilationView;
    private TextView seatReclineView;
    private TextView seatModesView;
    private TextView seatSafetyView;
    private TextView seatEvidenceView;
    private TextView seatRequestView;
    private View panelOverlay;
    private View intentSurface;
    private View planSurface;
    private View executionSurface;
    private View resultSurface;
    private View intentTab;
    private View planTab;
    private View executionTab;
    private View resultTab;
    private View deviceDrawer;
    private View hvacSurface;
    private View seatSurface;
    private Button approveButton;
    private Button rejectButton;
    private Button retryButton;
    private Button undoButton;
    private Button napButton;
    private View[] seatPositionControls = new View[0];
    private boolean detached;

    private CockpitControlCoordinator(Activity activity) {
        this.activity = activity;
        this.application = activity.getApplication();
        this.preferences = activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        synchronized (ACTIVE_LOCK) {
            state = retainedState.getRevision() > 0
                    ? retainedState
                    : restoreCheckpoint();
        }
    }

    /** Called by the one-line MainActivity smali hook after setContentView. */
    public static void install(Activity activity) {
        if (activity == null) {
            return;
        }
        CockpitControlCoordinator coordinator = new CockpitControlCoordinator(activity);
        CockpitControlCoordinator previous;
        synchronized (ACTIVE_LOCK) {
            previous = active.get();
            active = new WeakReference<>(coordinator);
        }
        if (previous != null) {
            previous.detach(false);
        }
        coordinator.attach();
    }

    private void attach() {
        application.registerActivityLifecycleCallbacks(this);
        bindViews();
        render();
        boolean resume = state.hasResumableSession(System.currentTimeMillis());
        Log.i(TAG, markers()
                + " client2_hmi_coordinator_installed=true"
                + " client2_hmi_state_restored=" + (state.getRevision() > 0)
                + " client2_hmi_resume_available=" + resume
                + " client2_hmi_checkpoint_text_persisted=false");
        if (resume) {
            resumeSession();
        }
    }

    private void bindViews() {
        View panel = findView("centralBrainPanel");
        if (panel != null) {
            bindButtons(panel);
        }
        replyView = findTextView("centralBrainReplyText");
        sourceView = findTextView("centralBrainSourceText");
        drivingView = findTextView("centralBrainDrivingText");
        restrictionView = findTextView("centralBrainRestrictionText");
        connectionView = findTextView("centralBrainConnectionText");
        intentPreviewView = findTextView("centralBrainIntentPreviewText");
        intentChainView = findTextView("centralBrainIntentChainText");
        contextView = findTextView("centralBrainContextText");
        planSummaryView = findTextView("centralBrainPlanSummaryText");
        planChainView = findTextView("centralBrainPlanChainText");
        executionSummaryView = findTextView("centralBrainExecutionSummaryText");
        executionChainView = findTextView("centralBrainExecutionChainText");
        executionActionsView = findTextView("centralBrainExecutionActionsText");
        approvalStateView = findTextView("centralBrainApprovalStateText");
        partialStateView = findTextView("centralBrainPartialStateText");
        compensationStateView = findTextView("centralBrainCompensationStateText");
        timelineIntentView = findTextView("centralBrainTimelineIntentText");
        timelineContextView = findTextView("centralBrainTimelineContextText");
        timelinePlanView = findTextView("centralBrainTimelinePlanText");
        timelinePolicyView = findTextView("centralBrainTimelinePolicyText");
        timelineGraphView = findTextView("centralBrainTimelineGraphText");
        timelineEffectView = findTextView("centralBrainTimelineEffectText");
        timelineReadbackView = findTextView("centralBrainTimelineReadbackText");
        resultSummaryView = findTextView("centralBrainResultSummaryText");
        resultEvidenceView = findTextView("centralBrainResultEvidenceText");
        sessionStripTitleView = findTextView("centralBrainSessionStripTitle");
        drawerTitleView = findTextView("centralBrainDrawerTitle");
        hvacDesiredView = findTextView("centralBrainHvacDesiredText");
        hvacTemperatureView = findTextView("centralBrainHvacTemperatureText");
        hvacFanView = findTextView("centralBrainHvacFanText");
        hvacModesView = findTextView("centralBrainHvacModesText");
        hvacEvidenceView = findTextView("centralBrainHvacEvidenceText");
        hvacRequestView = findTextView("centralBrainHvacRequestText");
        seatDesiredView = findTextView("centralBrainSeatDesiredText");
        seatHeatView = findTextView("centralBrainSeatHeatText");
        seatVentilationView = findTextView("centralBrainSeatVentilationText");
        seatReclineView = findTextView("centralBrainSeatReclineText");
        seatModesView = findTextView("centralBrainSeatModesText");
        seatSafetyView = findTextView("centralBrainSeatSafetyText");
        seatEvidenceView = findTextView("centralBrainSeatEvidenceText");
        seatRequestView = findTextView("centralBrainSeatRequestText");
        intentSurface = findView("centralBrainIntentSurface");
        planSurface = findView("centralBrainPlanSurface");
        executionSurface = findView("centralBrainExecutionSurface");
        resultSurface = findView("centralBrainResultSurface");
        intentTab = findView("centralBrainIntentTab");
        planTab = findView("centralBrainPlanTab");
        executionTab = findView("centralBrainExecutionTab");
        resultTab = findView("centralBrainResultTab");
        deviceDrawer = findView("centralBrainDeviceDrawer");
        hvacSurface = findView("centralBrainHvacSurface");
        seatSurface = findView("centralBrainSeatSurface");
        approveButton = findButton("centralBrainApproveButton");
        rejectButton = findButton("centralBrainRejectButton");
        retryButton = findButton("centralBrainRetryButton");
        undoButton = findButton("centralBrainUndoButton");
        napButton = findButton("centralBrainNapButton");
        seatPositionControls = new View[] {
                findView("centralBrainSeatReclineDownButton"),
                findView("centralBrainSeatReclineUpButton"),
                findView("centralBrainSeatUprightPresetButton"),
                findView("centralBrainSeatComfortPresetButton"),
                findView("centralBrainSeatRestPresetButton")
        };
        panelOverlay = findView("centralBrainPanelOverlay");
        if (panelOverlay != null) {
            panelOverlay.setOnClickListener(this);
        }
        View navigationTrigger = findView("centralBrainNavigationTrigger");
        if (navigationTrigger != null) {
            navigationTrigger.setOnClickListener(this);
        }
    }

    private View findView(String name) {
        int id = activity.getResources().getIdentifier(
                name,
                "id",
                activity.getPackageName());
        return id == 0 ? null : activity.findViewById(id);
    }

    private TextView findTextView(String name) {
        View view = findView(name);
        return view instanceof TextView ? (TextView) view : null;
    }

    private Button findButton(String name) {
        View view = findView(name);
        return view instanceof Button ? (Button) view : null;
    }

    private void bindButtons(View view) {
        if (view instanceof Button) {
            Button button = (Button) view;
            button.setBackgroundTintList(null);
            button.setOnClickListener(this);
            return;
        }
        if (!(view instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            bindButtons(group.getChildAt(index));
        }
    }

    @Override
    public void onClick(View view) {
        if (view == null || detached) {
            return;
        }
        if (view == panelOverlay) {
            setPanelVisible(false);
            return;
        }
        Object tag = view.getTag();
        String tagValue = tag == null ? "" : tag.toString();
        if (MENU_TAG.equals(tag)) {
            setPanelVisible(
                    state.getPanelVisibility() != CockpitHmiState.PanelVisibility.VISIBLE);
            return;
        }
        if (PANEL_CLOSE_TAG.equals(tag)) {
            setPanelVisible(false);
            return;
        }
        if (DRAWER_CLOSE_TAG.equals(tag)) {
            accept(CockpitHmiReducer.Event.drawerSelected(
                    CockpitHmiState.DeviceDrawer.CLOSED));
            return;
        }
        if (INTENT_STAGE_TAG.equals(tag)) {
            selectSurface(CockpitHmiState.SurfaceStage.INTENT);
            return;
        }
        if (PLAN_STAGE_TAG.equals(tag)) {
            selectSurface(CockpitHmiState.SurfaceStage.PLAN);
            return;
        }
        if (EXECUTION_STAGE_TAG.equals(tag)) {
            selectSurface(CockpitHmiState.SurfaceStage.EXECUTION);
            return;
        }
        if (RESULT_STAGE_TAG.equals(tag)) {
            selectSurface(CockpitHmiState.SurfaceStage.RESULT);
            return;
        }
        if (HVAC_DETAIL_TAG.equals(tag)) {
            accept(CockpitHmiReducer.Event.drawerSelected(
                    CockpitHmiState.DeviceDrawer.HVAC));
            return;
        }
        if (SEAT_DETAIL_TAG.equals(tag)) {
            accept(CockpitHmiReducer.Event.drawerSelected(
                    CockpitHmiState.DeviceDrawer.SEAT));
            return;
        }
        if ((tagValue.startsWith(HVAC_TAG_PREFIX)
                || tagValue.startsWith(SEAT_TAG_PREFIX))
                && !state.getPresentationMode().isParameterEditingEnabled()) {
            Log.w(TAG, markers()
                    + " cockpit_driving_restriction_blocked=true"
                    + " restricted_control=parameter_edit");
            return;
        }
        if (tagValue.startsWith(HVAC_TAG_PREFIX)) {
            handleHvacControl(tagValue);
            return;
        }
        if (tagValue.startsWith(SEAT_TAG_PREFIX)) {
            handleSeatControl(tagValue);
            return;
        }
        if (tagValue.startsWith(RECOVERY_TAG_PREFIX)) {
            Log.w(TAG, markers()
                    + " client2_hmi_recovery_command_available=false"
                    + " recovery_command=" + tagValue);
            return;
        }
        if (!(view instanceof TextView)) {
            return;
        }
        String scenarioId = tagValue;
        if (DrivingUxPolicy.isHighRiskScenario(scenarioId)
                && !state.getPresentationMode().isHighRiskScenarioEnabled()) {
            Log.w(TAG, markers()
                    + " cockpit_driving_restriction_blocked=true"
                    + " restricted_control=high_risk_scenario");
            return;
        }
        CharSequence text = ((TextView) view).getText();
        startScenario(scenarioId, text == null ? "" : text.toString());
    }

    private void selectSurface(CockpitHmiState.SurfaceStage surfaceStage) {
        accept(CockpitHmiReducer.Event.surfaceSelected(surfaceStage));
        Log.i(TAG, markers()
                + " client2_hmi_surface_selected=true"
                + " hmi_surface=" + surfaceStage);
    }

    private void setPanelVisible(boolean visible) {
        accept(CockpitHmiReducer.Event.panelVisibility(visible));
        if (!visible) {
            Log.i(TAG, markers()
                    + " client2_hmi_hidden_state_preserved=true");
        }
    }

    private void startScenario(String scenarioId, String userText) {
        if (scenarioId == null || scenarioId.isEmpty()) {
            return;
        }
        accept(CockpitHmiReducer.Event.scenarioSubmitted(scenarioId));
        Client2ScenarioBridge.SessionConnection opened =
                Client2ScenarioBridge.openSession(activity, scenarioId, userText, this);
        replaceConnection(opened);
    }

    private void handleHvacControl(String tag) {
        HvacControlIntent current = state.getHvacState().getDesired();
        HvacControlIntent changed;
        switch (tag) {
            case "central_brain_hvac_power":
                changed = current.withPower(!current.isPowerOn());
                break;
            case "central_brain_hvac_zone_driver":
                changed = current.withZone(HvacControlIntent.Zone.DRIVER);
                break;
            case "central_brain_hvac_zone_passenger":
                changed = current.withZone(HvacControlIntent.Zone.FRONT_PASSENGER);
                break;
            case "central_brain_hvac_zone_cabin":
                changed = current.withZone(HvacControlIntent.Zone.CABIN);
                break;
            case "central_brain_hvac_temp_down":
                changed = current.stepTemperature(-1);
                break;
            case "central_brain_hvac_temp_up":
                changed = current.stepTemperature(1);
                break;
            case "central_brain_hvac_fan_down":
                changed = current.stepFan(-1);
                break;
            case "central_brain_hvac_fan_up":
                changed = current.stepFan(1);
                break;
            case "central_brain_hvac_auto":
                changed = current.withAutoMode(!current.isAutoMode());
                break;
            case "central_brain_hvac_ac":
                changed = current.withAcEnabled(!current.isAcEnabled());
                break;
            case "central_brain_hvac_sync":
                changed = current.withSyncEnabled(!current.isSyncEnabled());
                break;
            case "central_brain_hvac_airflow":
                changed = current.nextAirflow();
                break;
            case "central_brain_hvac_preset_warm":
                changed = current.applyPreset(HvacControlIntent.Preset.WARM);
                break;
            case "central_brain_hvac_preset_cool":
                changed = current.applyPreset(HvacControlIntent.Preset.COOL);
                break;
            case "central_brain_hvac_preset_clear":
                changed = current.applyPreset(HvacControlIntent.Preset.CLEAR);
                break;
            default:
                return;
        }
        long beforeRevision = state.getHvacState().getDesiredRevision();
        accept(CockpitHmiReducer.Event.hvacDesiredChanged(changed));
        if (state.getHvacState().getDesiredRevision() != beforeRevision) {
            mainHandler.removeCallbacks(submitHvacRunnable);
            mainHandler.postDelayed(submitHvacRunnable, HVAC_DEBOUNCE_MS);
            Log.i(TAG, markers()
                    + " cockpit_hvac_desired_changed=true"
                    + " hvac_desired_revision=" + state.getHvacState().getDesiredRevision()
                    + " hvac_debounce_scheduled=true");
        }
    }

    private void submitPendingHvac() {
        if (detached) {
            return;
        }
        CockpitHvacState hvac = state.getHvacState();
        if (hvac.getRequestState() != CockpitHvacState.RequestState.DEBOUNCING
                || hvac.getDesiredRevision() <= 0) {
            return;
        }
        HvacControlIntent intent = hvac.getDesired();
        long revision = hvac.getDesiredRevision();
        accept(CockpitHmiReducer.Event.hvacManualSubmitted(revision));
        Client2ScenarioBridge.SessionConnection opened =
                Client2ScenarioBridge.openHvacSession(activity, intent, this);
        replaceConnection(opened);
        Log.i(TAG, markers()
                + " cockpit_hvac_manual_session_submitted=true"
                + " hvac_desired_revision=" + revision
                + " hvac_parameter_logged=false");
    }

    private void handleSeatControl(String tag) {
        SeatControlIntent current = state.getSeatState().getDesired();
        SeatControlIntent changed;
        switch (tag) {
            case "central_brain_seat_zone_driver":
                changed = current.withZone(SeatControlIntent.Zone.DRIVER);
                break;
            case "central_brain_seat_zone_passenger":
                changed = current.withZone(SeatControlIntent.Zone.FRONT_PASSENGER);
                break;
            case "central_brain_seat_zone_rear_left":
                changed = current.withZone(SeatControlIntent.Zone.REAR_LEFT);
                break;
            case "central_brain_seat_zone_rear_right":
                changed = current.withZone(SeatControlIntent.Zone.REAR_RIGHT);
                break;
            case "central_brain_seat_heat_down":
                changed = current.stepHeat(-1);
                break;
            case "central_brain_seat_heat_up":
                changed = current.stepHeat(1);
                break;
            case "central_brain_seat_vent_down":
                changed = current.stepVentilation(-1);
                break;
            case "central_brain_seat_vent_up":
                changed = current.stepVentilation(1);
                break;
            case "central_brain_seat_massage":
                changed = current.nextMassage();
                break;
            case "central_brain_seat_recline_down":
                changed = current.stepRecline(-1);
                break;
            case "central_brain_seat_recline_up":
                changed = current.stepRecline(1);
                break;
            case "central_brain_seat_preset_upright":
                changed = current.applyPreset(SeatControlIntent.Preset.UPRIGHT);
                break;
            case "central_brain_seat_preset_comfort":
                changed = current.applyPreset(SeatControlIntent.Preset.COMFORT);
                break;
            case "central_brain_seat_preset_rest":
                changed = current.applyPreset(SeatControlIntent.Preset.REST);
                break;
            default:
                return;
        }
        long beforeRevision = state.getSeatState().getDesiredRevision();
        accept(CockpitHmiReducer.Event.seatDesiredChanged(changed));
        CockpitSeatState seat = state.getSeatState();
        if (seat.getDesiredRevision() != beforeRevision
                && seat.getRequestState() == CockpitSeatState.RequestState.DEBOUNCING) {
            mainHandler.removeCallbacks(submitSeatRunnable);
            mainHandler.postDelayed(submitSeatRunnable, SEAT_DEBOUNCE_MS);
            Log.i(TAG, markers()
                    + " cockpit_seat_desired_changed=true"
                    + " seat_desired_revision=" + seat.getDesiredRevision()
                    + " seat_debounce_scheduled=true");
        } else if (seat.getRequestState() == CockpitSeatState.RequestState.BLOCKED
                || seat.getRequestState() == CockpitSeatState.RequestState.WAITING_APPROVAL) {
            mainHandler.removeCallbacks(submitSeatRunnable);
            Log.i(TAG, markers()
                    + " cockpit_seat_position_request_blocked=true"
                    + " seat_safety_decision=" + seat.getSafetyDecision()
                    + " seat_dispatch_triggered=false");
        }
    }

    private void submitPendingSeat() {
        if (detached) {
            return;
        }
        CockpitSeatState seat = state.getSeatState();
        if (seat.getRequestState() != CockpitSeatState.RequestState.DEBOUNCING
                || seat.getDesiredRevision() <= 0) {
            return;
        }
        SeatControlIntent intent = seat.getDesired();
        long revision = seat.getDesiredRevision();
        accept(CockpitHmiReducer.Event.seatManualSubmitted(revision));
        Client2ScenarioBridge.SessionConnection opened =
                Client2ScenarioBridge.openSeatSession(activity, intent, this);
        replaceConnection(opened);
        Log.i(TAG, markers()
                + " cockpit_seat_manual_session_submitted=true"
                + " seat_desired_revision=" + revision
                + " seat_parameter_logged=false");
    }

    private void resumeSession() {
        SessionHandle handle = state.toSessionHandle();
        if (handle == null || state.getUiScenarioId().isEmpty()) {
            return;
        }
        Log.i(TAG, markers()
                + " client2_hmi_session_resume_requested=true"
                + " resume_cursor_present=" + !state.getResumeCursor().isEmpty());
        Client2ScenarioBridge.SessionConnection resumed =
                Client2ScenarioBridge.resumeSession(
                        activity,
                        state.getUiScenarioId(),
                        handle,
                        state.getResumeCursor(),
                        this);
        attachConnection(resumed);
    }

    private synchronized void attachConnection(
            Client2ScenarioBridge.SessionConnection candidate) {
        if (detached) {
            if (candidate != null) {
                candidate.close();
            }
            return;
        }
        connection = candidate;
    }

    private void replaceConnection(Client2ScenarioBridge.SessionConnection candidate) {
        Client2ScenarioBridge.SessionConnection previous;
        synchronized (this) {
            if (detached) {
                if (candidate != null) {
                    candidate.close();
                }
                return;
            }
            previous = connection;
            connection = candidate;
        }
        if (previous != null && previous != candidate) {
            boolean cancelled = previous.cancel();
            previous.close();
            Log.i(TAG, markers()
                    + " client2_hmi_session_replaced=true"
                    + " client2_hmi_replacement_bind_first=true"
                    + " client2_hmi_replaced_session_cancelled=" + cancelled);
        }
    }

    @Override
    public void onSessionConnectionChanged(boolean connected, boolean reconnected) {
        accept(CockpitHmiReducer.Event.connectionChanged(connected, reconnected));
        Log.i(TAG, markers()
                + " client2_hmi_connection_changed=true"
                + " connected=" + connected
                + " reconnected=" + reconnected);
    }

    @Override
    public void onSessionOpened(SessionHandle handle, String scenarioId) {
        accept(CockpitHmiReducer.Event.sessionOpened(handle, scenarioId));
    }

    @Override
    public void onSessionSnapshot(SessionSnapshot snapshot) {
        accept(CockpitHmiReducer.Event.snapshot(snapshot));
    }

    @Override
    public void onSessionEvent(RuntimeEvent event) {
        CockpitHmiState before;
        synchronized (this) {
            before = state;
        }
        accept(CockpitHmiReducer.Event.runtimeEvent(event));
        synchronized (this) {
            if (state == before) {
                Log.i(TAG, markers()
                        + " client2_hmi_duplicate_event_suppressed=true");
            }
        }
    }

    @Override
    public void onSessionReplayComplete(SessionHandle handle, long lastSequence) {
        accept(CockpitHmiReducer.Event.replayComplete(handle, lastSequence));
        Log.i(TAG, markers()
                + " client2_hmi_replay_projected=true");
    }

    @Override
    public void onSessionOverflow(SessionHandle handle, String resumeCursor) {
        accept(CockpitHmiReducer.Event.overflow(handle, resumeCursor));
    }

    @Override
    public void onSessionClosed(
            SessionHandle handle,
            int reasonCode,
            String resumeCursor) {
        accept(CockpitHmiReducer.Event.streamClosed(handle, reasonCode, resumeCursor));
    }

    @Override
    public void onSessionError(SessionHandle handle, String code, String message) {
        accept(CockpitHmiReducer.Event.failure(handle, code, message));
    }

    private void accept(CockpitHmiReducer.Event event) {
        CockpitHmiState next;
        synchronized (this) {
            if (detached) {
                return;
            }
            next = CockpitHmiReducer.reduce(state, event);
            if (next == state) {
                return;
            }
            state = next;
            synchronized (ACTIVE_LOCK) {
                retainedState = next;
            }
        }
        persistCheckpoint(next.checkpoint());
        render();
        Log.i(TAG, markers()
                + " client2_hmi_state_reduced=true"
                + " hmi_revision=" + next.getRevision()
                + " hmi_connection=" + next.getConnectionState()
                + " hmi_surface=" + next.getSurfaceStage()
                + " hmi_drawer=" + next.getDeviceDrawer()
                + " hmi_last_event_sequence=" + next.getLastEventSequence());
    }

    private void render() {
        if (detached) {
            return;
        }
        activity.runOnUiThread(() -> {
            if (detached) {
                return;
            }
            CockpitHmiState current;
            synchronized (CockpitControlCoordinator.this) {
                current = state;
            }
            if (panelOverlay != null) {
                panelOverlay.setVisibility(
                        current.getPanelVisibility() == CockpitHmiState.PanelVisibility.VISIBLE
                                ? View.VISIBLE : View.GONE);
            }
            if (replyView != null) {
                replyView.setText(current.renderText());
            }
            renderSurface(current);
        });
    }

    private void renderSurface(CockpitHmiState current) {
        PanelPresentationMode presentationMode = current.getPresentationMode();
        renderPresentation(current, presentationMode);
        CockpitHmiState.SurfaceStage selected = current.getSurfaceStage();
        setVisible(intentSurface, selected == CockpitHmiState.SurfaceStage.INTENT);
        setVisible(planSurface, selected == CockpitHmiState.SurfaceStage.PLAN);
        setVisible(executionSurface, selected == CockpitHmiState.SurfaceStage.EXECUTION);
        setVisible(resultSurface, selected == CockpitHmiState.SurfaceStage.RESULT);
        setActivated(intentTab, selected == CockpitHmiState.SurfaceStage.INTENT);
        setActivated(planTab, selected == CockpitHmiState.SurfaceStage.PLAN);
        setActivated(executionTab, selected == CockpitHmiState.SurfaceStage.EXECUTION);
        setActivated(resultTab, selected == CockpitHmiState.SurfaceStage.RESULT);

        setText(connectionView, connectionLabel(current.getConnectionState()));
        String phrase = phraseForScenario(current.getUiScenarioId());
        setText(intentPreviewView, phrase.isEmpty() ? "选择一个场景意图" : phrase);
        if (current.hasSession()) {
            String canonical = current.getCanonicalScenarioId().isEmpty()
                    ? "正在确认场景" : current.getCanonicalScenarioId();
            setText(planSummaryView, "已受理：" + (phrase.isEmpty() ? canonical : phrase));
            setText(planChainView,
                    "01  意图：" + canonical
                            + "\n02  Context：未接入车辆数据"
                            + "\n03  Plan：未发布"
                            + "\n04  Policy：仅 Session admission"
                            + "\n05  Effect：未调度");
            setText(sessionStripTitleView, "软件 Session 已受理");
        } else {
            setText(planSummaryView, "等待场景意图");
            setText(planChainView,
                    "01  意图：等待"
                            + "\n02  Context：未接入"
                            + "\n03  Plan：未发布"
                            + "\n04  Policy：等待"
                            + "\n05  Effect：未调度");
            setText(sessionStripTitleView, "等待场景输入");
        }
        renderExecutionTimeline(current, presentationMode);
        setText(resultSummaryView, "暂无可验证车辆结果");
        CockpitHvacState hvac = current.getHvacState();
        CockpitSeatState seat = current.getSeatState();
        String resultEvidence = "Desired：UNAVAILABLE"
                + "\nReported：UNAVAILABLE"
                + "\nSource：UNAVAILABLE"
                + "\nQuality：NO EVIDENCE";
        if ("manual.seat".equals(current.getUiScenarioId())
                || (hvac.getDesiredRevision() == 0 && seat.getDesiredRevision() > 0)) {
            resultEvidence = "Desired：" + seat.getDesired().desiredSummary()
                    + "\nReported：UNAVAILABLE"
                    + "\nSource：UNAVAILABLE"
                    + "\nQuality：NO EVIDENCE"
                    + "\nRevision：" + seat.getDesiredRevision()
                    + "\nEffect：" + seat.getEffectState();
        } else if (hvac.getDesiredRevision() > 0) {
            resultEvidence = "Desired：" + hvac.getDesired().desiredSummary()
                    + "\nReported：UNAVAILABLE"
                    + "\nSource：UNAVAILABLE"
                    + "\nQuality：NO EVIDENCE"
                    + "\nRevision：" + hvac.getDesiredRevision()
                    + "\nEffect：" + hvac.getEffectState();
        }
        setText(resultEvidenceView, resultEvidence);
        renderDrawer(current.getDeviceDrawer(), presentationMode);
    }

    private void renderPresentation(
            CockpitHmiState current,
            PanelPresentationMode presentationMode) {
        CockpitSeatState.SafetyContext context = current.getSeatState().getSafetyContext();
        setText(sourceView, context.getSource().name());
        String drivingLabel;
        String restrictionLabel;
        if (presentationMode == PanelPresentationMode.PARKED_FULL) {
            drivingLabel = "PARKED · 完整";
            restrictionLabel = "驻车完整模式 · Runtime Policy 与 Safety 仍独立复验";
        } else if (context.getDrivingState() == CockpitSeatState.DrivingState.MOVING) {
            drivingLabel = "MOVING · 受限";
            restrictionLabel = "行驶简要模式 · 长详情与参数编辑已关闭";
        } else {
            drivingLabel = "UNKNOWN · 受限";
            restrictionLabel = "驾驶状态不可确认 · 按行驶态限制，高风险控件关闭";
        }
        setText(drivingView, drivingLabel);
        setText(restrictionView, restrictionLabel);

        boolean showLongText = presentationMode.isLongTextVisible();
        setVisible(intentChainView, showLongText);
        setVisible(contextView, showLongText);
        setVisible(planChainView, showLongText);
        setVisible(executionChainView, showLongText);
        setVisible(resultEvidenceView, showLongText);
        if (replyView != null) {
            replyView.setSingleLine(!showLongText);
            replyView.setMaxLines(showLongText ? 2 : 1);
        }
        setEnabled(napButton, presentationMode.isHighRiskScenarioEnabled());
    }

    private void renderExecutionTimeline(
            CockpitHmiState current,
            PanelPresentationMode presentationMode) {
        CockpitExecutionTimeline timeline = current.getExecutionTimeline();
        setText(executionSummaryView,
                current.hasSession()
                        ? "可观察执行时间线 · Session 已受理"
                        : "可观察执行时间线 · 等待意图");
        boolean concise = !presentationMode.isLongTextVisible();
        renderTimelineStage(timelineIntentView, "01 Intent",
                timeline.getStage(CockpitExecutionTimeline.Phase.INTENT), concise);
        renderTimelineStage(timelineContextView, "02 Context",
                timeline.getStage(CockpitExecutionTimeline.Phase.CONTEXT), concise);
        renderTimelineStage(timelinePlanView, "03 Plan",
                timeline.getStage(CockpitExecutionTimeline.Phase.PLAN), concise);
        renderTimelineStage(timelinePolicyView, "04 Policy",
                timeline.getStage(CockpitExecutionTimeline.Phase.POLICY), concise);
        renderTimelineStage(timelineGraphView, "05 Graph",
                timeline.getStage(CockpitExecutionTimeline.Phase.GRAPH), concise);
        renderTimelineStage(timelineEffectView, "06 Effect",
                timeline.getStage(CockpitExecutionTimeline.Phase.EFFECT), concise);
        renderTimelineStage(timelineReadbackView, "07 Readback",
                timeline.getStage(CockpitExecutionTimeline.Phase.READBACK), concise);

        String media = "UNAVAILABLE";
        String navigation = "UNAVAILABLE";
        StringBuilder trace = new StringBuilder("Typed event trace");
        for (CockpitExecutionTimeline.TraceItem item : timeline.getTraceItems()) {
            String status = statusLabel(item.getStatus());
            String target = item.getTarget();
            if (target.startsWith("media.")) {
                media = status;
            } else if (target.startsWith("navigation.") || target.startsWith("nav.")) {
                navigation = status;
            }
            trace.append("\n#").append(item.getSequence())
                    .append(' ').append(item.getEventType())
                    .append(" · ").append(status)
                    .append("\nTarget：").append(target)
                    .append(" · Source：").append(item.getSource())
                    .append(" · Result：").append(item.getResult());
        }
        if (timeline.getTraceItems().isEmpty()) {
            trace.append("\n暂无 Runtime typed event");
        }
        setText(executionActionsView,
                "Media STOP：" + media + " · Navigation CANCEL：" + navigation);
        renderRecoveryState(current.getRecoveryState(), concise);
        setText(executionChainView, trace.toString());
    }

    private void renderRecoveryState(CockpitRecoveryState recovery, boolean concise) {
        String expiry = recovery.getApprovalExpiresAtEpochMs() > 0
                ? Long.toString(recovery.getApprovalExpiresAtEpochMs())
                : "UNAVAILABLE";
        setText(approvalStateView, concise
                ? "Approval：" + statusLabel(recovery.getApprovalStatus())
                        + " · 行驶呈现不授予权限"
                : "Approval：" + statusLabel(recovery.getApprovalStatus())
                        + "\nReason：" + recovery.getApprovalReasonCode()
                        + " · Target：" + recovery.getApprovalTarget()
                        + "\nExpiry：" + expiry
                        + " · Response service：NOT PUBLISHED");
        setText(partialStateView, concise
                ? "Outcome：" + statusLabel(recovery.getAggregateStatus())
                        + " · V" + recovery.getVerifiedCount()
                        + "/F" + recovery.getFailedCount()
                        + "/I" + recovery.getInconclusiveCount()
                : "Outcome evidence：" + statusLabel(recovery.getAggregateStatus())
                        + "\nVERIFIED " + recovery.getVerifiedCount()
                        + " · FAILED " + recovery.getFailedCount()
                        + " · INCONCLUSIVE " + recovery.getInconclusiveCount());
        setText(compensationStateView,
                "Compensation：" + statusLabel(recovery.getCompensationStatus())
                        + " · Undo handle：NOT PUBLISHED");
        setEnabled(approveButton, recovery.isApproveEnabled());
        setEnabled(rejectButton, recovery.isRejectEnabled());
        setEnabled(retryButton, recovery.isRetryEnabled());
        setEnabled(undoButton, recovery.isUndoEnabled());
    }

    private static String statusLabel(Enum<?> status) {
        return status.name().replace('_', ' ');
    }

    private static void renderTimelineStage(
            TextView view,
            String label,
            CockpitExecutionTimeline.Stage stage,
            boolean concise) {
        setText(view, concise
                ? label + "：" + statusLabel(stage.getStatus())
                : label + "：" + statusLabel(stage.getStatus())
                        + "\nTarget：" + stage.getTarget()
                        + " · Source：" + stage.getSource()
                        + "\nResult：" + stage.getResult());
    }

    private static String statusLabel(CockpitExecutionTimeline.Status status) {
        switch (status) {
            case NOT_PUBLISHED:
                return "NOT PUBLISHED";
            case NOT_WIRED:
                return "NOT WIRED";
            case NOT_DISPATCHED:
                return "NOT DISPATCHED";
            case NO_EVIDENCE:
                return "NO EVIDENCE";
            case SESSION_ACCEPTED:
                return "SESSION ACCEPTED";
            case APPROVAL_REQUIRED:
                return "APPROVAL REQUIRED";
            case APPROVAL_RESOLVED:
                return "APPROVAL RESOLVED";
            default:
                return status.name();
        }
    }

    private static void setEnabled(View view, boolean enabled) {
        if (view != null) {
            view.setEnabled(enabled);
            view.setAlpha(enabled ? 1.0f : 0.55f);
        }
    }

    private void renderDrawer(
            CockpitHmiState.DeviceDrawer drawer,
            PanelPresentationMode presentationMode) {
        setVisible(deviceDrawer, drawer != CockpitHmiState.DeviceDrawer.CLOSED);
        setVisible(hvacSurface, drawer == CockpitHmiState.DeviceDrawer.HVAC);
        setVisible(seatSurface, drawer == CockpitHmiState.DeviceDrawer.SEAT);
        if (drawer == CockpitHmiState.DeviceDrawer.HVAC) {
            setText(drawerTitleView, "空调 Effect");
            CockpitHvacState hvac = state.getHvacState();
            HvacControlIntent desired = hvac.getDesired();
            setText(hvacDesiredView,
                    "Desired · rev " + hvac.getDesiredRevision()
                            + "\n" + desired.desiredSummary());
            setText(hvacTemperatureView, desired.temperatureLabel());
            setText(hvacFanView, "FAN " + desired.getFanLevel());
            setText(hvacModesView,
                    "POWER " + onOff(desired.isPowerOn())
                            + "   AUTO " + onOff(desired.isAutoMode())
                            + "   A/C " + onOff(desired.isAcEnabled())
                            + "\nSYNC " + onOff(desired.isSyncEnabled())
                            + "   ZONE " + desired.getZone()
                            + "\nAIRFLOW " + desired.getAirflow()
                            + "   PRESET " + desired.getPreset());
            setText(hvacEvidenceView,
                    "Reported：UNAVAILABLE"
                            + "\nSource：" + hvac.getSource()
                            + "\nQuality：" + hvac.getQuality()
                            + "\nEffect：" + hvac.getEffectState());
            setText(hvacRequestView,
                    "Governed request：" + requestLabel(hvac.getRequestState())
                            + "\n300 ms 合并 · 不直接调用 Adapter");
        } else if (drawer == CockpitHmiState.DeviceDrawer.SEAT) {
            setText(drawerTitleView, "座椅 Effect");
            CockpitSeatState seat = state.getSeatState();
            SeatControlIntent desired = seat.getDesired();
            CockpitSeatState.SafetyContext safety = seat.getSafetyContext();
            setText(seatDesiredView,
                    "Desired · rev " + seat.getDesiredRevision()
                            + "\n" + desired.desiredSummary());
            setText(seatHeatView, "HEAT " + desired.getHeatLevel());
            setText(seatVentilationView, "VENT " + desired.getVentilationLevel());
            setText(seatReclineView, desired.getReclineDegrees() + " deg");
            setText(seatModesView,
                    "ZONE " + desired.getZone()
                            + "   MASSAGE " + desired.getMassage()
                            + "\nPRESET " + desired.getPreset()
                            + "   HEAT/VENT MUTEX");
            setText(seatSafetyView,
                    "Driving：" + safety.getDrivingState()
                            + "\nOccupancy：" + safety.getOccupancyState()
                            + "   Belt：" + safety.getBeltState()
                            + "\nContext source：" + safety.getSource()
                            + "   Quality：" + safety.getQuality()
                            + "\nDecision：" + seat.getSafetyDecision());
            setText(seatEvidenceView,
                    "Reported：UNAVAILABLE"
                            + "\nSource：" + seat.getSource()
                            + "\nQuality：" + seat.getQuality()
                            + "\nEffect：" + seat.getEffectState());
            setText(seatRequestView,
                    "Governed request：" + requestLabel(seat.getRequestState())
                            + "\n300 ms 合并 · 位置动作失败关闭");
        }
        boolean parameterEditingEnabled = presentationMode.isParameterEditingEnabled();
        setButtonsEnabled(hvacSurface, parameterEditingEnabled);
        setButtonsEnabled(seatSurface, parameterEditingEnabled);
        CockpitSeatState.SafetyContext safety = state.getSeatState().getSafetyContext();
        boolean safePositionPreview = parameterEditingEnabled
                && safety.getDrivingState() == CockpitSeatState.DrivingState.PARKED
                && safety.getOccupancyState() == CockpitSeatState.OccupancyState.OCCUPIED
                && safety.getBeltState() == CockpitSeatState.BeltState.UNBELTED;
        for (View control : seatPositionControls) {
            setEnabled(control, safePositionPreview);
        }
    }

    private static void setButtonsEnabled(View root, boolean enabled) {
        if (root instanceof Button) {
            setEnabled(root, enabled);
            return;
        }
        if (!(root instanceof ViewGroup)) {
            return;
        }
        ViewGroup group = (ViewGroup) root;
        for (int index = 0; index < group.getChildCount(); index++) {
            setButtonsEnabled(group.getChildAt(index), enabled);
        }
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private static String requestLabel(CockpitHvacState.RequestState state) {
        switch (state) {
            case DEBOUNCING:
                return "等待合并";
            case SUBMITTING:
                return "正在受理";
            case ACCEPTED:
                return "SESSION ACCEPTED";
            case FAILED:
                return "FAILED";
            case DIRTY:
                return "待提交";
            case IDLE:
            default:
                return "IDLE";
        }
    }

    private static String requestLabel(CockpitSeatState.RequestState state) {
        switch (state) {
            case DEBOUNCING:
                return "等待合并";
            case SUBMITTING:
                return "正在受理";
            case ACCEPTED:
                return "SESSION ACCEPTED";
            case WAITING_APPROVAL:
                return "WAITING APPROVAL";
            case BLOCKED:
                return "BLOCKED";
            case FAILED:
                return "FAILED";
            case IDLE:
            default:
                return "IDLE";
        }
    }

    private static void setVisible(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private static void setActivated(View view, boolean activated) {
        if (view != null) {
            view.setActivated(activated);
        }
    }

    private static void setText(TextView view, String value) {
        if (view != null) {
            view.setText(value);
        }
    }

    private static String connectionLabel(CockpitHmiState.ConnectionState state) {
        switch (state) {
            case CONNECTING:
                return "正在连接";
            case CONNECTED:
                return "已连接";
            case RECONNECTING:
                return "正在恢复";
            case FAILED:
                return "连接失败";
            case CLOSED:
                return "已关闭";
            case DISCONNECTED:
            default:
                return "未连接";
        }
    }

    private static String phraseForScenario(String scenarioId) {
        if ("care.fatigue".equals(scenarioId)) {
            return "我有些疲惫";
        }
        if ("care.cold".equals(scenarioId)) {
            return "车里有点冷";
        }
        if ("skill.nap".equals(scenarioId)) {
            return "我想休息一会";
        }
        if ("task.home".equals(scenarioId)) {
            return "准备回家";
        }
        if ("manual.hvac".equals(scenarioId)) {
            return "手动空调调整";
        }
        if ("manual.seat".equals(scenarioId)) {
            return "手动座椅调整";
        }
        return "";
    }

    private CockpitHmiState restoreCheckpoint() {
        if (preferences.getInt("schema", 0) != CHECKPOINT_SCHEMA) {
            return CockpitHmiState.initial();
        }
        try {
            CockpitHmiState.Checkpoint checkpoint = new CockpitHmiState.Checkpoint(
                    preferences.getBoolean("panel_visible", false),
                    preferences.getString("ui_scenario", ""),
                    preferences.getString("canonical_scenario", ""),
                    preferences.getInt("handle_schema", SessionContract.SCHEMA_VERSION),
                    preferences.getString("session_id", ""),
                    preferences.getLong("accepted_at", 0),
                    preferences.getLong("expires_at", 0),
                    preferences.getLong("last_sequence", 0),
                    preferences.getString("resume_cursor", ""));
            if (checkpoint.hasSession()) {
                SessionHandle handle = new SessionHandle();
                handle.schemaVersion = checkpoint.handleSchemaVersion;
                handle.sessionId = checkpoint.sessionId;
                handle.acceptedAtEpochMs = checkpoint.acceptedAtEpochMs;
                handle.expiresAtEpochMs = checkpoint.expiresAtEpochMs;
                SessionContract.validateHandle(handle);
                if (checkpoint.uiScenarioId.isEmpty()
                        || checkpoint.canonicalScenarioId.isEmpty()
                        || handle.expiresAtEpochMs < System.currentTimeMillis()) {
                    throw new IllegalArgumentException("stale HMI checkpoint");
                }
            }
            return CockpitHmiReducer.reduce(
                    CockpitHmiState.initial(),
                    CockpitHmiReducer.Event.restored(checkpoint));
        } catch (RuntimeException invalid) {
            preferences.edit().clear().apply();
            return CockpitHmiState.initial();
        }
    }

    private void persistCheckpoint(CockpitHmiState.Checkpoint checkpoint) {
        preferences.edit()
                .putInt("schema", CHECKPOINT_SCHEMA)
                .putBoolean("panel_visible", checkpoint.panelVisible)
                .putString("ui_scenario", checkpoint.uiScenarioId)
                .putString("canonical_scenario", checkpoint.canonicalScenarioId)
                .putInt("handle_schema", checkpoint.handleSchemaVersion)
                .putString("session_id", checkpoint.sessionId)
                .putLong("accepted_at", checkpoint.acceptedAtEpochMs)
                .putLong("expires_at", checkpoint.expiresAtEpochMs)
                .putLong("last_sequence", checkpoint.lastEventSequence)
                .putString("resume_cursor", checkpoint.resumeCursor)
                .apply();
    }

    private void detach(boolean lifecycleDestroy) {
        Client2ScenarioBridge.SessionConnection previous;
        CockpitHmiState detachedState;
        synchronized (this) {
            if (detached) {
                return;
            }
            detachedState = CockpitHmiReducer.reduce(
                    state,
                    CockpitHmiReducer.Event.detached());
            state = detachedState;
            detached = true;
            mainHandler.removeCallbacks(submitHvacRunnable);
            mainHandler.removeCallbacks(submitSeatRunnable);
            previous = connection;
            connection = null;
        }
        synchronized (ACTIVE_LOCK) {
            retainedState = detachedState;
            if (active.get() == this) {
                active = new WeakReference<>(null);
            }
        }
        persistCheckpoint(detachedState.checkpoint());
        if (previous != null) {
            previous.close();
        }
        application.unregisterActivityLifecycleCallbacks(this);
        Log.i(TAG, markers()
                + " client2_hmi_lifecycle_detached=true"
                + " lifecycle_destroy=" + lifecycleDestroy
                + " client2_hmi_state_retained=true");
    }

    private String markers() {
        return "cockpit_hmi_state_reducer_implemented=true"
                + " cockpit_hmi_lifecycle_owner_java=true"
                + " cockpit_hmi_four_stage_shell_implemented=true"
                + " cockpit_hmi_intent_first_primary=true"
                + " cockpit_hmi_device_drawer_scaffolded=true"
                + " cockpit_hvac_surface_implemented=true"
                + " cockpit_hvac_reducer_owned=true"
                + " cockpit_hvac_debounce_ms=300"
                + " cockpit_hvac_governed_manual_session=true"
                + " cockpit_hvac_reported_readback_available=false"
                + " cockpit_seat_surface_implemented=true"
                + " cockpit_seat_reducer_owned=true"
                + " cockpit_seat_debounce_ms=300"
                + " cockpit_seat_governed_manual_session=true"
                + " cockpit_seat_unknown_restricted_fail_closed=true"
                + " cockpit_seat_reported_readback_available=false"
                + " cockpit_execution_timeline_implemented=true"
                + " cockpit_execution_timeline_reducer_owned=true"
                + " cockpit_execution_typed_event_projection=true"
                + " cockpit_execution_plan_published=false"
                + " cockpit_execution_effect_dispatch_enabled=false"
                + " cockpit_execution_readback_available=false"
                + " cockpit_recovery_state_reducer_owned=true"
                + " cockpit_approval_response_service_published=false"
                + " cockpit_retry_service_published=false"
                + " cockpit_undo_service_published=false"
                + " cockpit_recovery_commands_enabled=false"
                + " cockpit_driving_ux_policy_implemented=true"
                + " cockpit_unknown_driving_restricted=true"
                + " cockpit_runtime_policy_authority_independent=true"
                + " legacy_text_callback_authoritative=false"
                + " scenario_execution_enabled=false"
                + " service_dispatch_triggered=false"
                + " production_effect_dispatch_enabled=false"
                + " hardware_accessed=false";
    }

    @Override
    public void onActivityDestroyed(Activity destroyedActivity) {
        if (destroyedActivity == activity) {
            detach(true);
        }
    }

    @Override
    public void onActivitySaveInstanceState(Activity savedActivity, Bundle outState) {
        if (savedActivity == activity) {
            persistCheckpoint(state.checkpoint());
        }
    }

    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityResumed(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
}
