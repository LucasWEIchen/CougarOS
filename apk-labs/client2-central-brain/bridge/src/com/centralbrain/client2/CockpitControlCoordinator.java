package com.centralbrain.client2;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.InputDevice;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.centralbrain.sdk.event.RuntimeEvent;
import com.centralbrain.sdk.model.DevelopmentModelInputReceipt;
import com.centralbrain.sdk.model.DevelopmentModelProjection;
import com.centralbrain.sdk.session.SessionContract;
import com.centralbrain.sdk.session.SessionHandle;
import com.centralbrain.sdk.session.SessionSnapshot;

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Maintained Java owner for Client2 overlay state, rendering and Session lifecycle. */
public final class CockpitControlCoordinator implements
        View.OnClickListener,
        ScenarioCallback,
        DebugSimulationControllerClient.Callback,
        OrchestrationRuntimeClient.Callback,
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
    private static final String ENGINEER_DETAIL_TAG = "central_brain_detail_engineer";
    private static final String HVAC_TAG_PREFIX = "central_brain_hvac_";
    private static final String SEAT_TAG_PREFIX = "central_brain_seat_";
    private static final String RECOVERY_TAG_PREFIX = "central_brain_recovery_";
    private static final String RECOVERY_APPROVE_TAG = "central_brain_recovery_approve";
    private static final String RECOVERY_REJECT_TAG = "central_brain_recovery_reject";
    private static final String ENGINEER_TAG_PREFIX = "central_brain_engineer_";
    private static final long HVAC_DEBOUNCE_MS = 300L;
    private static final long SEAT_DEBOUNCE_MS = 300L;
    private static final String PREFS_NAME = "central_brain_hmi_state_v1";
    private static final int CHECKPOINT_SCHEMA = 1;
    private static final int MAX_LIVE_TRACE_LINES = 32;
    private static final long LIVE_TRACE_INTERVAL_MS = 360L;
    private static final float UNITY_REFERENCE_WIDTH = 1920.0f;
    private static final float UNITY_REFERENCE_HEIGHT = 1080.0f;
    private static final float UNITY_DRIVER_TEMP_DECREASE_X = 268.54f;
    private static final float UNITY_DRIVER_TEMP_INCREASE_X = 463.10f;
    private static final float UNITY_PASSENGER_TEMP_DECREASE_RIGHT = 536.46f;
    private static final float UNITY_PASSENGER_TEMP_INCREASE_RIGHT = 341.90f;
    private static final float UNITY_TEMP_BUTTON_BOTTOM = 47.10f;
    private static final Object ACTIVE_LOCK = new Object();

    private static WeakReference<CockpitControlCoordinator> active = new WeakReference<>(null);
    private static CockpitHmiState retainedState = CockpitHmiState.initial();

    private final Activity activity;
    private final Application application;
    private final SharedPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable submitHvacRunnable = this::submitPendingHvac;
    private final Runnable submitSeatRunnable = this::submitPendingSeat;
    private final Runnable traceDrainRunnable = this::drainNextTrace;
    private final ArrayDeque<String> pendingTraceLines = new ArrayDeque<>();
    private final ArrayList<String> displayedTraceLines = new ArrayList<>();
    private final DebugSimulationControllerClient debugSimulationClient;
    private final OrchestrationRuntimeClient orchestrationClient;
    private final CockpitDisplayPolicy displayPolicy;

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
    private TextView engineerStatusView;
    private TextView engineerContextView;
    private TextView engineerFaultView;
    private TextView liveTraceView;
    private TextView actuatorTitleView;
    private TextView actuatorStateView;
    private TextView actuatorHvacTemperatureView;
    private TextView actuatorHvacFanView;
    private TextView actuatorSeatAngleView;
    private TextView actuatorMediaView;
    private TextView shoppingOccupancyView;
    private TextView shoppingProductView;
    private TextView shoppingMerchantView;
    private TextView shoppingOrderView;
    private TextView shoppingRouteView;
    private TextView shoppingBoundaryView;
    private TextView modelInputTextView;
    private ImageView modelInputThumbnail;
    private ImageView imagePreview;
    private ScrollView liveTraceScroll;
    private ProgressBar actuatorFanProgress;
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
    private View engineerSurface;
    private View panelView;
    private View navigationTrigger;
    private View actuatorOverlay;
    private View hvacFeedbackRegion;
    private View seatFeedbackRegion;
    private View shoppingFeedbackRegion;
    private View seatBackView;
    private View modelInputSurface;
    private View imagePreviewOverlay;
    private View approvalControls;
    private Button engineerDetailButton;
    private Button approveButton;
    private Button rejectButton;
    private Button retryButton;
    private Button undoButton;
    private Button napButton;
    private View[] seatPositionControls = new View[0];
    private int traceSequence;
    private boolean coldHvacAnimated;
    private boolean fatigueHvacAnimated;
    private boolean fatigueSeatAnimated;
    private int shoppingFeedbackStage = -1;
    private boolean traceDrainScheduled;
    private String displayedModelReply = "";
    private CockpitMultimodalInput pendingMultimodalInput;
    private Bitmap modelInputBitmap;
    private final Set<String> admittedModelActions = new LinkedHashSet<>();
    private boolean multimodalConsumptionProved;
    private boolean detached;

    private CockpitControlCoordinator(Activity activity) {
        this.activity = activity;
        this.application = activity.getApplication();
        this.preferences = activity.getSharedPreferences(PREFS_NAME, Activity.MODE_PRIVATE);
        this.debugSimulationClient = new DebugSimulationControllerClient(activity, this);
        this.orchestrationClient = new OrchestrationRuntimeClient(activity, this);
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        this.displayPolicy = CockpitDisplayPolicy.resolve(
                metrics.widthPixels,
                metrics.heightPixels,
                metrics.densityDpi,
                activity.getResources().getConfiguration().fontScale);
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
                + " cockpit_display_profile=" + displayPolicy.getProfile()
                + " cockpit_display_supported=" + displayPolicy.isSupported()
                + " cockpit_display_rejection=" + safeDisplayToken(
                        displayPolicy.getRejectionCode())
                + " client2_hmi_checkpoint_text_persisted=false");
        if (resume) {
            resumeSession();
        }
        debugSimulationClient.connect();
        orchestrationClient.connect();
    }

    private void bindViews() {
        panelView = findView("centralBrainPanel");
        if (panelView != null) {
            bindButtons(panelView);
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
        engineerStatusView = findTextView("centralBrainEngineerStatusText");
        engineerContextView = findTextView("centralBrainEngineerContextText");
        engineerFaultView = findTextView("centralBrainEngineerFaultText");
        liveTraceView = findTextView("centralBrainLiveTraceText");
        actuatorTitleView = findTextView("centralBrainActuatorTitleText");
        actuatorStateView = findTextView("centralBrainActuatorStateText");
        actuatorHvacTemperatureView = findTextView(
                "centralBrainActuatorHvacTemperatureText");
        actuatorHvacFanView = findTextView("centralBrainActuatorHvacFanText");
        actuatorSeatAngleView = findTextView("centralBrainActuatorSeatAngleText");
        actuatorMediaView = findTextView("centralBrainActuatorMediaText");
        shoppingOccupancyView = findTextView("centralBrainShoppingOccupancyText");
        shoppingProductView = findTextView("centralBrainShoppingProductText");
        shoppingMerchantView = findTextView("centralBrainShoppingMerchantText");
        shoppingOrderView = findTextView("centralBrainShoppingOrderText");
        shoppingRouteView = findTextView("centralBrainShoppingRouteText");
        shoppingBoundaryView = findTextView("centralBrainShoppingBoundaryText");
        modelInputTextView = findTextView("centralBrainModelInputText");
        View thumbnail = findView("centralBrainModelInputThumbnail");
        modelInputThumbnail = thumbnail instanceof ImageView ? (ImageView) thumbnail : null;
        View preview = findView("centralBrainImagePreview");
        imagePreview = preview instanceof ImageView ? (ImageView) preview : null;
        modelInputSurface = findView("centralBrainModelInputSurface");
        imagePreviewOverlay = findView("centralBrainImagePreviewOverlay");
        approvalControls = findView("centralBrainApprovalControls");
        View traceScroll = findView("centralBrainLiveTraceScroll");
        liveTraceScroll = traceScroll instanceof ScrollView
                ? (ScrollView) traceScroll : null;
        View fanProgress = findView("centralBrainActuatorFanProgress");
        actuatorFanProgress = fanProgress instanceof ProgressBar
                ? (ProgressBar) fanProgress : null;
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
        engineerSurface = findView("centralBrainEngineerSurface");
        actuatorOverlay = findView("centralBrainActuatorOverlay");
        hvacFeedbackRegion = findView("centralBrainHvacFeedbackRegion");
        seatFeedbackRegion = findView("centralBrainSeatFeedbackRegion");
        shoppingFeedbackRegion = findView("centralBrainShoppingFeedbackRegion");
        seatBackView = findView("centralBrainSeatBack");
        engineerDetailButton = findButton("centralBrainEngineerDetailButton");
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
        if (modelInputThumbnail != null) {
            modelInputThumbnail.setOnClickListener(ignored -> showImagePreview());
        }
        if (imagePreviewOverlay != null) {
            imagePreviewOverlay.setOnClickListener(ignored -> hideImagePreview());
        }
        if (imagePreview != null) {
            imagePreview.setOnClickListener(ignored -> {
                // Consume the click so only the area outside the image closes the preview.
            });
        }
        navigationTrigger = findView("centralBrainNavigationTrigger");
        if (navigationTrigger != null) {
            navigationTrigger.setOnClickListener(this);
        }
        applyDisplayBounds();
        applyAccessibilityContract(panelView);
        applyAccessibilityContract(navigationTrigger);
        setEnabled(navigationTrigger, isDisplayReady());
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
        if (ENGINEER_DETAIL_TAG.equals(tag)) {
            if (state.getEngineerState().isAvailable()) {
                accept(CockpitHmiReducer.Event.drawerSelected(
                        CockpitHmiState.DeviceDrawer.ENGINEER));
            }
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
            if (state.getSimulatedScenarioState().isApprovalInputEnabled()) {
                if (RECOVERY_APPROVE_TAG.equals(tagValue)) {
                    orchestrationClient.approvePending();
                    return;
                }
                if (RECOVERY_REJECT_TAG.equals(tagValue)) {
                    orchestrationClient.rejectPending();
                    return;
                }
            }
            Log.w(TAG, markers()
                    + " client2_hmi_recovery_command_available=false"
                    + " recovery_command=" + tagValue);
            return;
        }
        if (tagValue.startsWith(ENGINEER_TAG_PREFIX)) {
            handleEngineerControl(tagValue);
            return;
        }
        if (!(view instanceof TextView)) {
            return;
        }
        String scenarioId = tagValue;
        boolean effectAnimationOnly = CockpitSimulatedScenarioState.isSupported(scenarioId);
        if (DrivingUxPolicy.isHighRiskScenario(scenarioId)
                && !state.getPresentationMode().isHighRiskScenarioEnabled()
                && !effectAnimationOnly) {
            Log.w(TAG, markers()
                    + " cockpit_driving_restriction_blocked=true"
                    + " restricted_control=high_risk_scenario");
            return;
        }
        if (DrivingUxPolicy.isHighRiskScenario(scenarioId) && effectAnimationOnly) {
            Log.i(TAG, markers()
                    + " cockpit_safety_interface_reserved=true"
                    + " effect_animation_only=true"
                    + " effect_authority_granted=false");
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
        if (visible && !isDisplayReady()) {
            accept(CockpitHmiReducer.Event.panelVisibility(false));
            Log.w(TAG, markers()
                    + " cockpit_display_matrix_rejected=true"
                    + " cockpit_display_rejection=" + safeDisplayToken(
                            displayPolicy.getRejectionCode()));
            return;
        }
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
        resetLiveTrace(scenarioId, userText);
        resetActuatorFeedback();
        clearPendingMultimodalInput();
        if (modelInputBitmap != null) {
            modelInputBitmap.recycle();
            modelInputBitmap = null;
        }
        if (modelInputThumbnail != null) {
            modelInputThumbnail.setImageDrawable(null);
        }
        if (imagePreview != null) {
            imagePreview.setImageDrawable(null);
        }
        admittedModelActions.clear();
        multimodalConsumptionProved = false;
        setVisible(modelInputSurface, false);
        hideImagePreview();
        if (CockpitMultimodalInput.UI_SCENARIO_ID.equals(scenarioId)) {
            try {
                pendingMultimodalInput = CockpitMultimodalInput.load(activity);
                modelInputBitmap = pendingMultimodalInput.decodePreview();
                if (modelInputThumbnail != null) {
                    modelInputThumbnail.setImageBitmap(modelInputBitmap);
                }
                if (imagePreview != null) {
                    imagePreview.setImageBitmap(modelInputBitmap);
                }
                setText(
                        modelInputTextView,
                        "模型输入 · “处理一下” + 座舱图像 · "
                                + pendingMultimodalInput.getImageByteCount() + " bytes");
                setVisible(modelInputSurface, true);
                appendLiveTrace(
                        "MODEL INPUT",
                        "STAGING",
                        "处理一下 + cabin frame · SHA-256 verified");
            } catch (RuntimeException failure) {
                appendLiveTrace(
                        "MODEL INPUT",
                        "FAILED",
                        "Controlled frame integrity or decode rejected");
                return;
            }
        }
        String simulatedDrivingProfile = "PARKED";
        accept(CockpitHmiReducer.Event.scenarioSubmitted(
                scenarioId, simulatedDrivingProfile));
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

    private void handleEngineerControl(String tag) {
        CockpitEngineerState engineer = state.getEngineerState();
        if (!engineer.isAvailable()) {
            return;
        }
        switch (tag) {
            case "central_brain_engineer_driving_unknown":
                debugSimulationClient.setDrivingState(
                        CockpitSeatState.DrivingState.UNKNOWN_RESTRICTED);
                break;
            case "central_brain_engineer_driving_parked":
                debugSimulationClient.setDrivingState(CockpitSeatState.DrivingState.PARKED);
                break;
            case "central_brain_engineer_driving_moving":
                debugSimulationClient.setDrivingState(CockpitSeatState.DrivingState.MOVING);
                break;
            case "central_brain_engineer_occupancy_empty":
                debugSimulationClient.setOccupancy(CockpitSeatState.OccupancyState.EMPTY);
                break;
            case "central_brain_engineer_occupancy_occupied":
                debugSimulationClient.setOccupancy(CockpitSeatState.OccupancyState.OCCUPIED);
                break;
            case "central_brain_engineer_belt_belted":
                debugSimulationClient.setBelt(CockpitSeatState.BeltState.BELTED);
                break;
            case "central_brain_engineer_belt_unbelted":
                debugSimulationClient.setBelt(CockpitSeatState.BeltState.UNBELTED);
                break;
            case "central_brain_engineer_adapter_hvac":
                accept(CockpitHmiReducer.Event.engineerAdapterSelected(
                        CockpitEngineerState.AdapterTarget.HVAC));
                break;
            case "central_brain_engineer_adapter_seat":
                accept(CockpitHmiReducer.Event.engineerAdapterSelected(
                        CockpitEngineerState.AdapterTarget.SEAT));
                break;
            case "central_brain_engineer_fault_none":
                debugSimulationClient.setAdapterFault(
                        engineer.getAdapterTarget(), CockpitEngineerState.FaultMode.NONE);
                break;
            case "central_brain_engineer_fault_delay":
                debugSimulationClient.setAdapterFault(
                        engineer.getAdapterTarget(), CockpitEngineerState.FaultMode.DELAY);
                break;
            case "central_brain_engineer_fault_timeout":
                debugSimulationClient.setAdapterFault(
                        engineer.getAdapterTarget(), CockpitEngineerState.FaultMode.TIMEOUT);
                break;
            case "central_brain_engineer_fault_failure":
                debugSimulationClient.setAdapterFault(
                        engineer.getAdapterTarget(),
                        CockpitEngineerState.FaultMode.RETRYABLE_FAILURE);
                break;
            case "central_brain_engineer_fault_terminal":
                debugSimulationClient.setAdapterFault(
                        engineer.getAdapterTarget(),
                        CockpitEngineerState.FaultMode.TERMINAL_FAILURE);
                break;
            case "central_brain_engineer_fault_mismatch":
                debugSimulationClient.setAdapterFault(
                        engineer.getAdapterTarget(),
                        CockpitEngineerState.FaultMode.READBACK_MISMATCH);
                break;
            case "central_brain_engineer_reset":
                debugSimulationClient.reset();
                break;
            default:
                return;
        }
        Log.i(TAG, markers()
                + " cockpit_engineer_command_submitted=true"
                + " command_payload_logged=false"
                + " effect_authorization_source=false");
    }

    @Override
    public void onConnecting() {
        accept(CockpitHmiReducer.Event.engineerConnecting());
    }

    @Override
    public void onConnected(long revision, CockpitSeatState.DrivingState drivingState) {
        accept(CockpitHmiReducer.Event.engineerConnected(revision, drivingState));
    }

    @Override
    public void onDrivingApplied(CockpitSeatState.DrivingState value, long revision) {
        accept(CockpitHmiReducer.Event.engineerDrivingApplied(value, revision));
    }

    @Override
    public void onOccupancyApplied(CockpitSeatState.OccupancyState value, long revision) {
        accept(CockpitHmiReducer.Event.engineerOccupancyApplied(value, revision));
    }

    @Override
    public void onBeltApplied(CockpitSeatState.BeltState value, long revision) {
        accept(CockpitHmiReducer.Event.engineerBeltApplied(value, revision));
    }

    @Override
    public void onFaultApplied(CockpitEngineerState.FaultMode value, long revision) {
        accept(CockpitHmiReducer.Event.engineerFaultApplied(value, revision));
    }

    @Override
    public void onResetApplied(long revision) {
        accept(CockpitHmiReducer.Event.engineerResetApplied(revision));
    }

    @Override
    public void onFailure(String code) {
        accept(CockpitHmiReducer.Event.engineerFailure(code));
    }

    @Override
    public void onSimulatedRuntimeAvailability(boolean available, String failureCode) {
        accept(CockpitHmiReducer.Event.simulatedRuntimeAvailability(
                available, failureCode));
        if (!available && !"CONNECTING".equals(failureCode)) {
            appendLiveTrace("RUNTIME", "FAILED", failureCode);
        }
    }

    @Override
    public void onSimulatedScenarioSnapshot(
            CockpitSimulatedScenarioState.Projection projection) {
        accept(CockpitHmiReducer.Event.simulatedScenarioSnapshot(projection));
        CockpitSimulatedScenarioState simulated = state.getSimulatedScenarioState();
        if (simulated.isModelInferenceCompleted()
                && !simulated.getAssistantDisplayText().equals(displayedModelReply)) {
            displayedModelReply = simulated.getAssistantDisplayText();
            appendLiveTrace(
                    "MODEL REPLY",
                    simulated.getModelProviderId(),
                    simulated.getAssistantDisplayText());
        }
        animateSimulatedEffects(simulated);
        if (simulated.getLifecycle() == CockpitSimulatedScenarioState.Lifecycle.COMPLETED) {
            appendLiveTrace(
                    "RESULT",
                    "COMPLETED",
                    "UI simulation only · vehicle bus not accessed");
        }
    }

    @Override
    public void onSimulatedScenarioFailure(String uiScenarioId, String failureCode) {
        accept(CockpitHmiReducer.Event.simulatedScenarioFailure(
                uiScenarioId, failureCode));
        appendLiveTrace("RESULT", "FAILED", failureCode);
    }

    @Override
    public void onPipelineMilestone(String stage, String status, String detail) {
        appendLiveTrace(stage, status, detail);
    }

    @Override
    public void onMultimodalInputAccepted(DevelopmentModelInputReceipt receipt) {
        if (receipt == null
                || !CockpitScenarioControlState.canonicalScenarioId(
                        CockpitMultimodalInput.UI_SCENARIO_ID)
                        .equals(receipt.scenarioId)) {
            appendLiveTrace("MODEL INPUT", "REJECTED", "Receipt binding mismatch");
            return;
        }
        appendLiveTrace(
                "MODEL INPUT",
                "BOUND",
                receipt.inputText + " + image · "
                        + receipt.imageByteCount + " bytes · "
                        + receipt.imageSha256.substring(0, 12));
    }

    @Override
    public void onModelExchange(
            String uiScenarioId, DevelopmentModelProjection projection) {
        if (projection == null) {
            return;
        }
        admittedModelActions.clear();
        admittedModelActions.addAll(Arrays.asList(projection.admittedActions));
        multimodalConsumptionProved =
                !CockpitMultimodalInput.UI_SCENARIO_ID.equals(uiScenarioId)
                        || projection.imageConsumed;
        appendLiveTrace(
                "AGENT ACTIONS",
                "ALLOWLISTED",
                TextUtils.join(", ", admittedModelActions));
        if (CockpitMultimodalInput.UI_SCENARIO_ID.equals(uiScenarioId)) {
            appendLiveTrace(
                    "MODEL OUTPUT",
                    projection.imageConsumed ? "IMAGE_CONSUMED" : "REJECTED",
                    projection.assistantDisplayText);
        }
    }

    private void resetLiveTrace(String scenarioId, String utterance) {
        mainHandler.removeCallbacks(traceDrainRunnable);
        pendingTraceLines.clear();
        displayedTraceLines.clear();
        traceDrainScheduled = false;
        traceSequence = 0;
        displayedModelReply = "";
        coldHvacAnimated = false;
        fatigueHvacAnimated = false;
        fatigueSeatAnimated = false;
        shoppingFeedbackStage = -1;
        if (liveTraceView != null) {
            liveTraceView.setText("");
        }
        appendLiveTrace("VOICE", "TRANSCRIBED", utterance);
        appendLiveTrace(
                "BOUNDARY",
                "SIMULATION",
                "Vehicle bus unavailable · safety interfaces reserved");
        Log.i(TAG, markers()
                + " voice_first_scenario_requested=true"
                + " ui_scenario_id=" + safeDisplayToken(scenarioId)
                + " raw_utterance_logged=false"
                + " effect_animation_only=true");
    }

    private void appendLiveTrace(String stage, String status, String detail) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> appendLiveTrace(stage, status, detail));
            return;
        }
        if (detached) {
            return;
        }
        String safeStage = boundedTraceValue(stage, 24);
        String safeStatus = boundedTraceValue(status, 32);
        String safeDetail = boundedTraceValue(detail, 180);
        traceSequence++;
        pendingTraceLines.addLast(
                String.format(Locale.ROOT, "%02d  %s / %s\n    %s",
                        traceSequence, safeStage, safeStatus, safeDetail));
        if (!traceDrainScheduled) {
            traceDrainScheduled = true;
            mainHandler.post(traceDrainRunnable);
        }
    }

    private void drainNextTrace() {
        if (detached) {
            return;
        }
        String line = pendingTraceLines.pollFirst();
        if (line == null) {
            traceDrainScheduled = false;
            return;
        }
        displayedTraceLines.add(line);
        while (displayedTraceLines.size() > MAX_LIVE_TRACE_LINES) {
            displayedTraceLines.remove(0);
        }
        if (liveTraceView != null) {
            liveTraceView.setText(TextUtils.join("\n\n", displayedTraceLines));
        }
        if (liveTraceScroll != null) {
            liveTraceScroll.post(() -> liveTraceScroll.fullScroll(View.FOCUS_DOWN));
        }
        mainHandler.postDelayed(traceDrainRunnable, LIVE_TRACE_INTERVAL_MS);
    }

    private static String boundedTraceValue(String value, int maximum) {
        String safe = value == null ? "UNAVAILABLE" : value.trim();
        if (safe.isEmpty()) {
            safe = "UNAVAILABLE";
        }
        safe = safe.replace('\n', ' ').replace('\r', ' ');
        return safe.length() <= maximum ? safe : safe.substring(0, maximum);
    }

    private void resetActuatorFeedback() {
        setText(actuatorHvacTemperatureView, "26.5°C");
        setText(actuatorHvacFanView, "风量 1");
        setText(actuatorSeatAngleView, "靠背 15°");
        setText(actuatorMediaView, "媒体 播放中");
        setText(shoppingOccupancyView, "等待多模态观察");
        setText(shoppingProductView, "商品候选等待中");
        setText(shoppingMerchantView, "商户候选等待中");
        setText(shoppingOrderView, "订单预览等待中");
        setText(shoppingRouteView, "路线预览等待中");
        setText(
                shoppingBoundaryView,
                "SYNTHETIC · 外部购物、支付和地图接口未接入");
        if (actuatorFanProgress != null) {
            actuatorFanProgress.setProgress(1);
        }
        if (seatBackView != null) {
            seatBackView.animate().cancel();
            seatBackView.setRotation(0.0f);
        }
        if (actuatorOverlay != null) {
            actuatorOverlay.animate().cancel();
            actuatorOverlay.setVisibility(View.GONE);
            actuatorOverlay.setAlpha(0.0f);
            actuatorOverlay.setTranslationX(-80.0f);
        }
    }

    private void animateSimulatedEffects(CockpitSimulatedScenarioState simulated) {
        if (CockpitMultimodalInput.UI_SCENARIO_ID.equals(simulated.getUiScenarioId())) {
            animateShoppingJourney(simulated);
            return;
        }
        if (!simulated.hasSnapshot() || simulated.getEffectDispatchCount() == 0) {
            return;
        }
        showActuatorOverlay(simulated.getUiScenarioId());
        if ("care.cold".equals(simulated.getUiScenarioId()) && !coldHvacAnimated) {
            coldHvacAnimated = true;
            setText(actuatorStateView, "HVAC 白名单动作执行中 · SIMULATED");
            animateTemperature(26.5f, 28.0f);
            appendLiveTrace(
                    "UI EFFECT",
                    "ANIMATING",
                    "HVAC target 26.5°C → 28.0°C · simulated");
            finishActuatorAnimation(1900L);
            return;
        }
        if ("care.fatigue".equals(simulated.getUiScenarioId())) {
            if (!fatigueHvacAnimated) {
                fatigueHvacAnimated = true;
                setText(actuatorStateView, "HVAC 通风动作执行中 · SIMULATED");
                animateFan(1, 3);
                appendLiveTrace(
                        "UI EFFECT",
                        "ANIMATING",
                        "HVAC fan 1 → 3 · simulated");
            }
            if (simulated.getApprovalInputCount() > 0 && !fatigueSeatAnimated) {
                fatigueSeatAnimated = true;
                setText(actuatorStateView, "座椅舒展动作执行中 · SIMULATED");
                animateSeat(15.0f, 30.0f);
                appendLiveTrace(
                        "UI EFFECT",
                        "ANIMATING",
                        "Driver seat recline 15° → 30° · simulated");
                finishActuatorAnimation(2100L);
            }
            return;
        }
    }

    private void animateShoppingJourney(CockpitSimulatedScenarioState simulated) {
        if (!simulated.hasSnapshot() || !multimodalConsumptionProved) {
            return;
        }
        showActuatorOverlay(simulated.getUiScenarioId());
        int nextStage = Math.min(
                CockpitSimulatedScenarioState.MAX_APPROVAL_COUNT,
                simulated.getApprovalInputCount());
        if (simulated.getLifecycle() == CockpitSimulatedScenarioState.Lifecycle.COMPLETED) {
            nextStage = CockpitSimulatedScenarioState.MAX_APPROVAL_COUNT;
        }
        if (nextStage == shoppingFeedbackStage) {
            return;
        }
        shoppingFeedbackStage = nextStage;
        setText(
                shoppingOccupancyView,
                "3 个座位区域有人 · 后排右侧可见饮水容器");
        if (nextStage >= 1) {
            setText(shoppingProductView, "饮用水候选 3 项 · SYNTHETIC");
            setText(shoppingMerchantView, "便利店/服务区候选 3 项 · SYNTHETIC");
            setText(shoppingOrderView, "饮用水 500ml ×1 · 价格待确认");
            setText(shoppingRouteView, "最近候选 2.4 km · 预计 4 min");
        }
        if (nextStage >= 2) {
            setText(
                    shoppingOrderView,
                    "订单已确认 · 外部购物接口未接入 · NOT_DISPATCHED");
        }
        if (nextStage >= 3) {
            setText(
                    shoppingRouteView,
                    "购物路线已启动 · UI SIMULATION ONLY");
        }
        switch (nextStage) {
            case 0:
                setText(actuatorStateView, "等待购物与路径规划服务确认");
                break;
            case 1:
                setText(actuatorStateView, "商品、商户和路线预览完成 · 等待订单确认");
                break;
            case 2:
                setText(actuatorStateView, "订单未外发 · 等待启动购物路线");
                break;
            default:
                setText(actuatorStateView, "购物编排仿真完成 · EXTERNAL ADAPTERS NOT ACCESSED");
                break;
        }
        if (shoppingFeedbackRegion != null) {
            shoppingFeedbackRegion.animate().cancel();
            shoppingFeedbackRegion.setAlpha(0.35f);
            shoppingFeedbackRegion.animate()
                    .alpha(1.0f)
                    .setDuration(420L)
                    .start();
        }
    }

    private void showActuatorOverlay(String scenarioId) {
        boolean fatigue = "care.fatigue".equals(scenarioId);
        boolean multimodal = CockpitMultimodalInput.UI_SCENARIO_ID.equals(scenarioId);
        setText(actuatorTitleView,
                fatigue
                        ? "疲劳关怀执行反馈"
                        : multimodal
                                ? "购物与路径规划服务"
                                : "温度关怀执行反馈");
        setVisible(hvacFeedbackRegion, !multimodal);
        setVisible(seatFeedbackRegion, fatigue);
        setVisible(shoppingFeedbackRegion, multimodal);
        if (actuatorOverlay != null) {
            ViewGroup.LayoutParams params = actuatorOverlay.getLayoutParams();
            int targetHeight = Math.round((fatigue || multimodal ? 520.0f : 300.0f)
                    * activity.getResources().getDisplayMetrics().density);
            if (params != null && params.height != targetHeight) {
                params.height = targetHeight;
                actuatorOverlay.setLayoutParams(params);
            }
        }
        if (actuatorOverlay == null || actuatorOverlay.getVisibility() == View.VISIBLE) {
            return;
        }
        actuatorOverlay.setVisibility(View.VISIBLE);
        actuatorOverlay.setAlpha(0.0f);
        actuatorOverlay.setTranslationX(-80.0f);
        actuatorOverlay.animate()
                .alpha(1.0f)
                .translationX(0.0f)
                .setDuration(420L)
                .start();
    }

    private void animateTemperature(float from, float to) {
        ValueAnimator animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(1800L);
        animator.addUpdateListener(value -> {
            float current = (float) value.getAnimatedValue();
            String label = String.format(Locale.ROOT, "%.1f°C", current);
            setText(actuatorHvacTemperatureView, label);
        });
        animator.start();
        mainHandler.postDelayed(() -> setUnityTemperatureState(true), 900L);
    }

    private void animateFan(int from, int to) {
        ValueAnimator animator = ValueAnimator.ofInt(from, to);
        animator.setDuration(1600L);
        animator.addUpdateListener(value -> {
            int current = (int) value.getAnimatedValue();
            setText(actuatorHvacFanView, "风量 " + current);
            if (actuatorFanProgress != null) {
                actuatorFanProgress.setProgress(current);
            }
        });
        animator.start();
    }

    private void animateSeat(float from, float to) {
        ValueAnimator animator = ValueAnimator.ofFloat(from, to);
        animator.setDuration(2000L);
        animator.addUpdateListener(value -> {
            float current = (float) value.getAnimatedValue();
            setText(actuatorSeatAngleView,
                    String.format(Locale.ROOT, "靠背 %.0f°", current));
            if (seatBackView != null) {
                seatBackView.setPivotX(seatBackView.getWidth() * 0.5f);
                seatBackView.setPivotY(seatBackView.getHeight());
                seatBackView.setRotation(-(current - from) * 1.2f);
            }
        });
        animator.start();
    }

    private void setUnityTemperatureState(boolean warm) {
        View renderView = resolveUnityRenderView();
        if (renderView == null || renderView.getWidth() <= 0 || renderView.getHeight() <= 0) {
            Log.w(TAG, markers()
                    + " unity_hvac_native_dispatch=false"
                    + " unity_hvac_native_reason=render_view_unavailable");
            return;
        }
        float widthScale = renderView.getWidth() / UNITY_REFERENCE_WIDTH;
        float heightScale = renderView.getHeight() / UNITY_REFERENCE_HEIGHT;
        float driverX = (warm
                ? UNITY_DRIVER_TEMP_INCREASE_X
                : UNITY_DRIVER_TEMP_DECREASE_X) * widthScale;
        float passengerX = renderView.getWidth() - (warm
                ? UNITY_PASSENGER_TEMP_INCREASE_RIGHT
                : UNITY_PASSENGER_TEMP_DECREASE_RIGHT) * widthScale;
        float buttonY = renderView.getHeight()
                - UNITY_TEMP_BUTTON_BOTTOM * heightScale;
        dispatchUnityTap(renderView, driverX, buttonY);
        mainHandler.postDelayed(
                () -> dispatchUnityTap(renderView, passengerX, buttonY),
                520L);
        Log.i(TAG, markers()
                + " unity_hvac_native_dispatch=true"
                + " unity_hvac_native_state=" + (warm ? "28_0" : "26_5")
                + " unity_hvac_native_zones=driver_passenger"
                + " unity_hvac_render_size=" + renderView.getWidth()
                + "x" + renderView.getHeight());
    }

    private View resolveUnityRenderView() {
        return findUnityRenderDescendant(findView("view1"));
    }

    private static View findUnityRenderDescendant(View view) {
        if (view == null) {
            return null;
        }
        if ("com.unity3d.renderservice.client.TuanjieView".equals(
                view.getClass().getName())) {
            return view;
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            View match = findUnityRenderDescendant(group.getChildAt(index));
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private void dispatchUnityTap(View renderView, float localX, float localY) {
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = createUnityTouchEvent(
                downTime, downTime, MotionEvent.ACTION_DOWN, localX, localY);
        boolean downHandled;
        try {
            downHandled = renderView.dispatchTouchEvent(down);
        } finally {
            down.recycle();
        }
        mainHandler.postDelayed(() -> {
            long upTime = SystemClock.uptimeMillis();
            MotionEvent up = createUnityTouchEvent(
                    downTime, downTime, MotionEvent.ACTION_UP, localX, localY);
            boolean upHandled;
            try {
                upHandled = renderView.dispatchTouchEvent(up);
            } finally {
                up.recycle();
            }
            Log.i(TAG, markers()
                    + " unity_hvac_native_tap=true"
                    + " unity_hvac_native_down_handled=" + downHandled
                    + " unity_hvac_native_up_handled=" + upHandled
                    + " unity_hvac_native_x=" + Math.round(localX)
                    + " unity_hvac_native_y=" + Math.round(localY)
                    + " unity_hvac_native_press_ms=" + (upTime - downTime));
        }, 96L);
    }

    private static MotionEvent createUnityTouchEvent(
            long downTime,
            long eventTime,
            int action,
            float localX,
            float localY) {
        MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties();
        properties.id = 0;
        properties.toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords coordinates = new MotionEvent.PointerCoords();
        coordinates.x = localX;
        coordinates.y = localY;
        coordinates.pressure = action == MotionEvent.ACTION_UP ? 0.0f : 1.0f;
        coordinates.size = 1.0f;
        return MotionEvent.obtain(
                downTime,
                eventTime,
                action,
                1,
                new MotionEvent.PointerProperties[]{properties},
                new MotionEvent.PointerCoords[]{coordinates},
                0,
                0,
                1.0f,
                1.0f,
                -1,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0);
    }

    private void finishActuatorAnimation(long delayMs) {
        mainHandler.postDelayed(() -> {
            if (!detached) {
                setText(actuatorStateView,
                        "仿真动作完成 · VEHICLE BUS NOT ACCESSED");
            }
        }, delayMs);
    }

    private void showImagePreview() {
        if (modelInputBitmap == null) {
            return;
        }
        CockpitSeatState.DrivingState drivingState =
                state.getSeatState().getSafetyContext().getDrivingState();
        CockpitSimulatedScenarioState simulated = state.getSimulatedScenarioState();
        boolean simulatedParked = drivingState
                == CockpitSeatState.DrivingState.UNKNOWN_RESTRICTED
                && simulated.isRuntimeAvailable()
                && simulated.hasSnapshot()
                && CockpitMultimodalInput.UI_SCENARIO_ID.equals(
                        simulated.getUiScenarioId())
                && "PARKED".equals(simulated.getDrivingProfile());
        if (drivingState != CockpitSeatState.DrivingState.PARKED
                && !simulatedParked) {
            appendLiveTrace(
                    "MODEL INPUT",
                    "PREVIEW_BLOCKED",
                    "Driving state " + drivingState
                            + " does not allow full-screen preview");
            return;
        }
        setVisible(imagePreviewOverlay, true);
        if (imagePreviewOverlay != null) {
            imagePreviewOverlay.bringToFront();
            imagePreviewOverlay.setFocusableInTouchMode(true);
            imagePreviewOverlay.setOnKeyListener((view, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK
                        && event.getAction() == KeyEvent.ACTION_UP) {
                    hideImagePreview();
                    return true;
                }
                return false;
            });
            imagePreviewOverlay.requestFocus();
        }
    }

    private void hideImagePreview() {
        setVisible(imagePreviewOverlay, false);
    }

    private void clearPendingMultimodalInput() {
        if (pendingMultimodalInput != null) {
            pendingMultimodalInput.close();
            pendingMultimodalInput = null;
        }
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
        if (handle != null
                && CockpitSimulatedScenarioState.isSupported(
                        state.getUiScenarioId())) {
            CockpitMultimodalInput multimodal =
                    CockpitMultimodalInput.UI_SCENARIO_ID.equals(state.getUiScenarioId())
                            ? pendingMultimodalInput : null;
            pendingMultimodalInput = null;
            orchestrationClient.openOrResume(
                    handle.sessionId,
                    state.getUiScenarioId(),
                    state.getSeatState().getSafetyContext().getDrivingState(),
                    true,
                    multimodal);
        }
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
                        isDisplayReady()
                                && current.getPanelVisibility()
                                == CockpitHmiState.PanelVisibility.VISIBLE
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
        CockpitScenarioControlState scenarioControl = current.getScenarioControlState();
        CockpitSimulatedScenarioState simulated = current.getSimulatedScenarioState();
        setText(intentPreviewView, phrase.isEmpty() ? "选择一个场景意图" : phrase);
        if (current.hasSession()) {
            String canonical = current.getCanonicalScenarioId().isEmpty()
                    ? "正在确认场景" : current.getCanonicalScenarioId();
            setText(planSummaryView, "已受理：" + (phrase.isEmpty() ? canonical : phrase));
            setText(planChainView,
                    "01  意图：" + canonical
                            + "\n02  Context：未接入车辆数据"
                            + "\n03  Plan：" + planLabel(scenarioControl)
                            + "\n04  Policy：仅 Session admission"
                            + "\n05  Effect：未调度"
                            + "\n设备目录：HVAC " + deviceRoleLabel(scenarioControl.getHvacRole())
                            + " · Seat " + deviceRoleLabel(scenarioControl.getSeatRole())
                            + "\n同步：" + scenarioControl.getLifecycle()
                            + " · Event #" + scenarioControl.getLastEventSequence());
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
        if (simulated.hasScenario()) {
            String canonical = simulated.getCanonicalScenarioId().isEmpty()
                    ? CockpitScenarioControlState.canonicalScenarioId(
                            simulated.getUiScenarioId())
                    : simulated.getCanonicalScenarioId();
            setText(planSummaryView,
                    simulated.hasSnapshot()
                            ? "Debug 仿真 Plan 已发布"
                            : "Debug 仿真 Runtime 正在受理");
            setText(planChainView,
                    "01  意图：" + canonical
                            + "\n02  Context：SIMULATED · "
                            + simulated.getDrivingProfile()
                            + "\n03  Plan：" + simulationPlanLabel(simulated)
                            + "\n04  Policy：" + simulationPolicyLabel(simulated)
                            + "\n05  Graph：" + simulationGraphLabel(simulated)
                            + "\n固定目标：" + fixedTargetLabel(simulated.getUiScenarioId())
                            + "\n边界：DEBUG ONLY · HARDWARE NOT ACCESSED");
            setText(sessionStripTitleView, "AIOS Debug 仿真链路");
        }
        renderExecutionTimeline(current, presentationMode);
        setText(resultSummaryView, "暂无可验证车辆结果");
        CockpitHvacState hvac = current.getHvacState();
        CockpitSeatState seat = current.getSeatState();
        String resultEvidence = "Desired：UNAVAILABLE"
                + "\nReported：UNAVAILABLE"
                + "\nSource：UNAVAILABLE"
                + "\nQuality：NO EVIDENCE";
        if (simulated.hasSnapshot()) {
            setText(resultSummaryView, simulationResultLabel(simulated));
            resultEvidence = "Simulation：" + simulated.getLifecycle()
                    + "\nEffect dispatch：" + simulated.getEffectDispatchCount()
                    + " · SIMULATED"
                    + "\nReadback matched：" + simulated.getReadbackMatchCount()
                    + "/" + simulated.getReadbackAttemptCount()
                    + " · SIMULATED"
                    + "\nApproval input：" + simulated.getApprovalInputCount()
                    + " · Failure：" + simulated.getFailureCount()
                    + "\nHardware：NOT ACCESSED · Production：NOT READY";
        } else if (scenarioControl.getOrigin() == CockpitScenarioControlState.Origin.NATURAL) {
            resultEvidence = "Catalog target：HVAC "
                    + deviceRoleLabel(scenarioControl.getHvacRole())
                    + " · Seat " + deviceRoleLabel(scenarioControl.getSeatRole())
                    + "\nDesired parameters：NOT PUBLISHED"
                    + "\nPlan：" + planLabel(scenarioControl)
                    + "\nLifecycle：" + scenarioControl.getLifecycle()
                    + " · Event #" + scenarioControl.getLastEventSequence()
                    + "\nEffect：NOT DISPATCHED"
                    + "\nReported：UNAVAILABLE · Quality：NO EVIDENCE";
        } else if ("manual.seat".equals(current.getUiScenarioId())
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
        setVisible(engineerDetailButton, current.getEngineerState().isAvailable());
        refreshAccessibilityState(panelView);
    }

    private void renderPresentation(
            CockpitHmiState current,
            PanelPresentationMode presentationMode) {
        CockpitSeatState.SafetyContext context = current.getSeatState().getSafetyContext();
        CockpitSimulatedScenarioState simulated = current.getSimulatedScenarioState();
        setText(sourceView, simulated.hasSnapshot()
                ? "SIMULATED" : context.getSource().name());
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
        CockpitSimulatedScenarioState simulated = current.getSimulatedScenarioState();
        setText(executionSummaryView,
                simulated.hasSnapshot()
                        ? "AIOS 自动执行链 · " + simulated.getLifecycle()
                        : current.hasSession()
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
                simulated.hasSnapshot()
                        ? "固定目标：" + fixedTargetLabel(simulated.getUiScenarioId())
                                + "\nEffect " + simulated.getEffectDispatchCount()
                                + " · Readback " + simulated.getReadbackMatchCount()
                                + "/" + simulated.getReadbackAttemptCount()
                        : "Media STOP：" + media + " · Navigation CANCEL：" + navigation);
        renderRecoveryState(current, concise);
        if (simulated.hasSnapshot()) {
            trace.append("\nDebug metadata · Event ")
                    .append(simulated.getProjectedEventCount())
                    .append(" · Graph rev ").append(simulated.getGraphRevision())
                    .append("\nNo target payload · No vehicle evidence");
        }
        setText(executionChainView, trace.toString());
    }

    private void renderRecoveryState(CockpitHmiState current, boolean concise) {
        CockpitRecoveryState recovery = current.getRecoveryState();
        CockpitSimulatedScenarioState simulated = current.getSimulatedScenarioState();
        String expiry = recovery.getApprovalExpiresAtEpochMs() > 0
                ? Long.toString(recovery.getApprovalExpiresAtEpochMs())
                : "UNAVAILABLE";
        if (simulated.hasScenario()
                && (simulated.hasSnapshot()
                        || simulated.getLifecycle()
                                == CockpitSimulatedScenarioState.Lifecycle.FAILED)) {
            setText(approvalStateView, concise
                    ? "Debug approval：" + simulated.getLifecycle()
                            + " · 不授予生产权限"
                    : "Debug approval：" + simulated.getLifecycle()
                            + "\nTarget：" + (simulated.getPendingCapabilityId().isEmpty()
                                    ? "NONE" : simulated.getPendingCapabilityId())
                            + " · Input count：" + simulated.getApprovalInputCount()
                            + "\nAuthority：SIMULATION ONLY · Production：UNAVAILABLE");
        } else {
            setText(approvalStateView, concise
                    ? "Approval：" + statusLabel(recovery.getApprovalStatus())
                            + " · 行驶呈现不授予权限"
                    : "Approval：" + statusLabel(recovery.getApprovalStatus())
                            + "\nReason：" + recovery.getApprovalReasonCode()
                            + " · Target：" + recovery.getApprovalTarget()
                            + "\nExpiry：" + expiry
                            + " · Response service：NOT PUBLISHED");
        }
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
        boolean approvalInputEnabled =
                simulated.isApprovalInputEnabled() || recovery.isApproveEnabled();
        boolean rejectionInputEnabled =
                simulated.isApprovalInputEnabled() || recovery.isRejectEnabled();
        setVisible(approvalControls, approvalInputEnabled || rejectionInputEnabled);
        setEnabled(approveButton, approvalInputEnabled);
        setEnabled(rejectButton, rejectionInputEnabled);
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
            updateAccessibilityState(view);
        }
    }

    private void renderDrawer(
            CockpitHmiState.DeviceDrawer drawer,
            PanelPresentationMode presentationMode) {
        setVisible(deviceDrawer, drawer != CockpitHmiState.DeviceDrawer.CLOSED);
        setVisible(hvacSurface, drawer == CockpitHmiState.DeviceDrawer.HVAC);
        setVisible(seatSurface, drawer == CockpitHmiState.DeviceDrawer.SEAT);
        setVisible(engineerSurface, drawer == CockpitHmiState.DeviceDrawer.ENGINEER);
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
                            + "\nScenario role："
                            + deviceRoleLabel(state.getScenarioControlState().getHvacRole())
                            + " · " + state.getScenarioControlState().getLifecycle()
                            + "\nPlan：" + planLabel(state.getScenarioControlState())
                            + " · Event #" + state.getScenarioControlState().getLastEventSequence()
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
                            + "\nScenario role："
                            + deviceRoleLabel(state.getScenarioControlState().getSeatRole())
                            + " · " + state.getScenarioControlState().getLifecycle()
                            + "\nPlan：" + planLabel(state.getScenarioControlState())
                            + " · Event #" + state.getScenarioControlState().getLastEventSequence()
                            + "\n300 ms 合并 · 位置动作失败关闭");
        } else if (drawer == CockpitHmiState.DeviceDrawer.ENGINEER) {
            setText(drawerTitleView, "工程仿真");
            CockpitEngineerState engineer = state.getEngineerState();
            setText(engineerStatusView,
                    "Controller：" + engineer.getConnectionState()
                            + "\nRevision：" + engineer.getControllerRevision()
                            + " · Production：DISABLED");
            setText(engineerContextView,
                    "Driving：" + engineer.getDrivingState()
                            + "\nOccupancy：" + engineer.getOccupancyState()
                            + " · Belt：" + engineer.getBeltState()
                            + "\nSource：SIMULATED · Effect authority：FALSE");
            setText(engineerFaultView,
                    "Adapter：" + engineer.getAdapterTarget()
                            + "\nFault：" + engineer.getFaultMode()
                            + " · Status：" + engineer.getStatusCode());
            renderEngineerSelection(engineer);
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
        setButtonsEnabled(engineerSurface, state.getEngineerState().isAvailable());
    }

    private void renderEngineerSelection(CockpitEngineerState engineer) {
        setActivated(findView("centralBrainEngineerDrivingUnknownButton"),
                engineer.getDrivingState()
                        == CockpitSeatState.DrivingState.UNKNOWN_RESTRICTED);
        setActivated(findView("centralBrainEngineerDrivingParkedButton"),
                engineer.getDrivingState() == CockpitSeatState.DrivingState.PARKED);
        setActivated(findView("centralBrainEngineerDrivingMovingButton"),
                engineer.getDrivingState() == CockpitSeatState.DrivingState.MOVING);
        setActivated(findView("centralBrainEngineerOccupancyEmptyButton"),
                engineer.getOccupancyState() == CockpitSeatState.OccupancyState.EMPTY);
        setActivated(findView("centralBrainEngineerOccupancyOccupiedButton"),
                engineer.getOccupancyState() == CockpitSeatState.OccupancyState.OCCUPIED);
        setActivated(findView("centralBrainEngineerBeltBeltedButton"),
                engineer.getBeltState() == CockpitSeatState.BeltState.BELTED);
        setActivated(findView("centralBrainEngineerBeltUnbeltedButton"),
                engineer.getBeltState() == CockpitSeatState.BeltState.UNBELTED);
        setActivated(findView("centralBrainEngineerAdapterHvacButton"),
                engineer.getAdapterTarget() == CockpitEngineerState.AdapterTarget.HVAC);
        setActivated(findView("centralBrainEngineerAdapterSeatButton"),
                engineer.getAdapterTarget() == CockpitEngineerState.AdapterTarget.SEAT);
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

    private static String planLabel(CockpitScenarioControlState state) {
        return state.isPlanPublished()
                ? "PUBLISHED rev " + state.getActivePlanRevision()
                : "NOT PUBLISHED";
    }

    private static String deviceRoleLabel(CockpitScenarioControlState.DeviceRole role) {
        return role.name().replace('_', ' ');
    }

    private static void setVisible(View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private static void setActivated(View view, boolean activated) {
        if (view != null) {
            view.setActivated(activated);
            view.setSelected(activated);
            updateAccessibilityState(view);
        }
    }

    private void applyAccessibilityContract(View view) {
        if (view == null) {
            return;
        }
        if (view instanceof Button) {
            Button button = (Button) view;
            CharSequence label = button.getContentDescription();
            if (TextUtils.isEmpty(label)) {
                label = button.getText();
            }
            if (!TextUtils.isEmpty(label)) {
                button.setContentDescription(normalizeAccessibilityLabel(label));
            }
            button.setFocusable(true);
            button.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            button.setMinimumWidth(displayPolicy.getMinimumTouchTargetPixels());
            button.setMinimumHeight(displayPolicy.getMinimumTouchTargetPixels());
            button.setMaxLines(2);
            button.setEllipsize(TextUtils.TruncateAt.END);
            updateAccessibilityState(button);
        } else if (view == navigationTrigger) {
            view.setFocusable(true);
            view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            view.setMinimumWidth(displayPolicy.getMinimumTouchTargetPixels());
            view.setMinimumHeight(displayPolicy.getMinimumTouchTargetPixels());
            updateAccessibilityState(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                applyAccessibilityContract(group.getChildAt(index));
            }
        }
    }

    private void applyDisplayBounds() {
        if (panelView == null || !isDisplayReady()) {
            return;
        }
        CockpitDisplayPolicy.Bounds bounds = displayPolicy.getPanelBoundsPixels();
        ViewGroup.LayoutParams parameters = panelView.getLayoutParams();
        parameters.width = bounds.getWidth();
        parameters.height = bounds.getHeight();
        if (parameters instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) parameters;
            margins.topMargin = bounds.getTop();
            margins.rightMargin = displayPolicy.getWidthPixels() - bounds.getRight();
        }
        panelView.setLayoutParams(parameters);
    }

    private boolean isDisplayReady() {
        return displayPolicy.isSupported() && displayPolicy.panelFitsDisplay();
    }

    private static void refreshAccessibilityState(View view) {
        if (view == null) {
            return;
        }
        if (view instanceof Button) {
            updateAccessibilityState(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                refreshAccessibilityState(group.getChildAt(index));
            }
        }
    }

    private static void updateAccessibilityState(View view) {
        if (view == null) {
            return;
        }
        String stateDescription = view.isSelected()
                ? (view.isEnabled() ? "已选择，可用" : "已选择，不可用")
                : (view.isEnabled() ? "可用" : "不可用");
        view.setStateDescription(stateDescription);
    }

    private static String normalizeAccessibilityLabel(CharSequence value) {
        return value.toString().replace('\n', ' ').trim();
    }

    private static String safeDisplayToken(String value) {
        return value == null || value.isEmpty() ? "NONE" : value;
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
        if (CockpitMultimodalInput.UI_SCENARIO_ID.equals(scenarioId)) {
            return "处理一下";
        }
        return "";
    }

    private static String simulationPlanLabel(CockpitSimulatedScenarioState state) {
        return state.hasSnapshot()
                ? "REV " + state.getPlanRevision() + " · FIXED DEBUG PLAN"
                : "WAITING";
    }

    private static String simulationPolicyLabel(CockpitSimulatedScenarioState state) {
        return state.getLifecycle() == CockpitSimulatedScenarioState.Lifecycle.WAITING_APPROVAL
                ? "EXPLICIT DEBUG APPROVAL REQUIRED"
                : "SIMULATION ONLY · NO PRODUCTION AUTHORITY";
    }

    private static String simulationGraphLabel(CockpitSimulatedScenarioState state) {
        return state.getLifecycle() == CockpitSimulatedScenarioState.Lifecycle.FAILED
                && !state.getFailureCode().isEmpty()
                ? "FAILED · " + state.getFailureCode()
                : state.getLifecycle().name();
    }

    private static String fixedTargetLabel(String uiScenarioId) {
        if ("care.cold".equals(uiScenarioId)) {
            return "空调开启 / 28 C / 主驾座椅加热";
        }
        if ("care.fatigue".equals(uiScenarioId)) {
            return "空调风量 / 媒体暂停 / 休息区导航 / 审批后座椅放倒";
        }
        if (CockpitMultimodalInput.UI_SCENARIO_ID.equals(uiScenarioId)) {
            return "购物服务 / 订单预览 / 购买路径规划 / 独立确认";
        }
        return "UNAVAILABLE";
    }

    private static String simulationResultLabel(CockpitSimulatedScenarioState state) {
        switch (state.getLifecycle()) {
            case COMPLETED:
                return "Debug 仿真执行完成";
            case PARTIAL:
                return "Debug 仿真部分完成";
            case WAITING_APPROVAL:
                return "等待显式 Debug 审批";
            case FAILED:
            case STUCK:
                return "Debug 仿真失败关闭";
            case CANCELLED:
                return "Debug 仿真已取消";
            case CONNECTING:
            case RUNNING:
            default:
                return "Debug 仿真执行中";
        }
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
            CockpitHmiState debugDetached = CockpitHmiReducer.reduce(
                    state,
                    CockpitHmiReducer.Event.engineerFailure("CB_SIM_DETACHED"));
            detachedState = CockpitHmiReducer.reduce(
                    debugDetached,
                    CockpitHmiReducer.Event.detached());
            state = detachedState;
            detached = true;
            mainHandler.removeCallbacks(submitHvacRunnable);
            mainHandler.removeCallbacks(submitSeatRunnable);
            mainHandler.removeCallbacks(traceDrainRunnable);
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
        clearPendingMultimodalInput();
        if (modelInputBitmap != null) {
            modelInputBitmap.recycle();
            modelInputBitmap = null;
        }
        debugSimulationClient.close();
        orchestrationClient.close();
        application.unregisterActivityLifecycleCallbacks(this);
        Log.i(TAG, markers()
                + " client2_hmi_lifecycle_detached=true"
                + " lifecycle_destroy=" + lifecycleDestroy
                + " client2_hmi_state_retained=true");
    }

    private String markers() {
        return "cockpit_hmi_state_reducer_implemented=true"
                + " cockpit_hmi_lifecycle_owner_java=true"
                + " cockpit_hmi_voice_first_shell_implemented=true"
                + " cockpit_hmi_task_trigger_count=3"
                + " cockpit_hmi_live_trace_implemented=true"
                + " cockpit_hmi_multimodal_input_implemented=true"
                + " cockpit_hmi_four_stage_shell_exposed=false"
                + " cockpit_hmi_manual_actuator_controls_exposed=false"
                + " cockpit_hmi_device_drawer_exposed=false"
                + " cockpit_hvac_simulated_feedback_implemented=true"
                + " cockpit_hvac_reducer_owned=true"
                + " cockpit_hvac_reported_readback_available=false"
                + " cockpit_seat_simulated_feedback_implemented=true"
                + " cockpit_seat_reducer_owned=true"
                + " cockpit_seat_unknown_restricted_fail_closed=true"
                + " cockpit_seat_reported_readback_available=false"
                + " cockpit_execution_timeline_reducer_owned=true"
                + " cockpit_execution_live_milestone_projection=true"
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
                + " cockpit_engineer_simulation_drawer_exposed=false"
                + " cockpit_engineer_signature_permission_required=true"
                + " cockpit_engineer_capability_required=true"
                + " cockpit_engineer_context_revisioned=true"
                + " cockpit_engineer_effect_authorization_source=false"
                + " cockpit_engineer_production_available=false"
                + " cockpit_scenario_control_state_reducer_owned=true"
                + " cockpit_scenario_catalog_normalized=true"
                + " cockpit_scenario_manual_shared_client=true"
                + " cockpit_scenario_device_session_synchronized=true"
                + " cockpit_orchestration_sdk_v1_wired=true"
                + " cockpit_legacy_simulated_scenario_binder_used=false"
                + " cockpit_simulated_scenario_projection_reducer_owned=true"
                + " cockpit_simulated_scenario_effect_dispatch_enabled=true"
                + " cockpit_simulated_scenario_readback_available=true"
                + " cockpit_simulated_safety_interface_reserved=true"
                + " cockpit_simulated_demo_auto_continue=true"
                + " cockpit_simulated_actuator_animation=true"
                + " cockpit_simulated_hardware_effect_dispatch_enabled=false"
                + " cockpit_display_matrix_defined=true"
                + " cockpit_accessibility_semantics_runtime_owned=true"
                + " cockpit_touch_target_min_dp=48"
                + " cockpit_display_effect_authorization_source=false"
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
