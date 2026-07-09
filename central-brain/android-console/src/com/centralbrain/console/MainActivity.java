package com.centralbrain.console;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.centralbrain.binding.CentralBrainGatewayBinderService;
import com.centralbrain.binding.CentralBrainGatewayClient;

public class MainActivity extends Activity {
    private static final String BASE_URL = CentralBrainGatewayBinderService.DEFAULT_BASE_URL;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextView statusView;
    private TextView detailView;
    private Button refreshButton;
    private Button planButton;
    private Button executeButton;
    private Button skillButton;
    private Button memoryButton;
    private Button eventSubscriptionsButton;
    private Button eventSubscriptionRequestButton;
    private Button eventSubscriptionCancelButton;
    private Button eventSubscriptionTransportButton;
    private Button eventSubscriptionDecisionButton;
    private Button eventSubscriptionActivationButton;
    private Button eventSubscriptionCallbackShapeButton;
    private Button eventSubscriptionCursorReplayButton;
    private Button eventSubscriptionBackpressureQosButton;
    private Button eventSubscriptionReadinessRollupButton;
    private Button eventSubscriptionActivationEvidenceButton;
    private Button eventSubscriptionActivationEvidenceStatusButton;
    private Button eventSubscriptionActivationEvidenceRetentionButton;
    private Button eventSubscriptionActivationEvidenceDecisionStatusButton;
    private Button eventSubscriptionActivationApprovalDryRunStatusButton;
    private Button eventSubscriptionActivationApprovalAuthorityChecklistButton;
    private Button eventSubscriptionActivationApprovalAuthorityAuditConsistencyButton;
    private Button eventSubscriptionActivationApprovalDecisionBlockerRollupButton;
    private Button vehicleSignalsButton;
    private Button vehicleSignalActivationButton;
    private Button vehicleSignalValidationButton;
    private Button governanceButton;
    private Button driverGapsButton;
    private Button hardwareInterfacesButton;
    private Button hardwareActivationChecklistButton;
    private Button hardwareOwnerDecisionStatusButton;
    private Button hardwareOwnerDecisionEvidenceButton;
    private Button hardwareOwnerDecisionEvidenceStatusButton;
    private Button hardwareOwnerDecisionEvidenceRetentionButton;
    private Button hardwareOwnerDecisionEvidenceReplacementButton;
    private Button hardwareOwnerDecisionEvidenceSelectedAdapterButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadBlockerButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadDryRunButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadDryRunStatusButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistencyButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadApprovalAuthorityButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatusButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistencyButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatusButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistencyButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionClosureBlockerMatrixButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionReviewerMatrixButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceStatusButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceAuditConsistencyButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceDecisionRollupButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessAuditConsistencyButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionRollupButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentChecklistButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistencyButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupButton;
    private Button hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummaryButton;
    private Button prototypeReadinessButton;
    private CentralBrainGatewayClient gatewayClient;
    private boolean gatewayBound;

    private interface GatewayCall {
        String run(CentralBrainGatewayClient client) throws RemoteException;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContentView());
        bindGateway();
    }

    @Override
    protected void onDestroy() {
        if (gatewayClient != null && gatewayBound) {
            gatewayClient.unbind();
            gatewayBound = false;
        }
        super.onDestroy();
    }

    private View buildContentView() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));
        root.setBackgroundColor(Color.rgb(247, 249, 250));
        scrollView.addView(root);

        TextView title = new TextView(this);
        title.setText("Central Brain Console");
        title.setTextSize(26);
        title.setTextColor(Color.rgb(22, 35, 43));
        title.setGravity(Gravity.START);
        title.setPadding(0, 0, 0, dp(8));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Android app layer -> Binder gateway -> AI SDK/Uni Info Bus prototype");
        subtitle.setTextSize(14);
        subtitle.setTextColor(Color.rgb(76, 91, 101));
        subtitle.setPadding(0, 0, 0, dp(18));
        root.addView(subtitle);

        statusView = new TextView(this);
        statusView.setText("Status: checking");
        statusView.setTextSize(18);
        statusView.setTextColor(Color.rgb(0, 105, 92));
        statusView.setPadding(0, 0, 0, dp(14));
        root.addView(statusView);

        LinearLayout buttonArea = new LinearLayout(this);
        buttonArea.setOrientation(LinearLayout.VERTICAL);
        root.addView(buttonArea);

        LinearLayout primaryRow = buttonRow();
        buttonArea.addView(primaryRow);
        refreshButton = addButton(primaryRow, "Refresh", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refreshHealth();
            }
        });
        planButton = addButton(primaryRow, "Plan Agent Task", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                planAgentTask();
            }
        });

        LinearLayout agentRow = buttonRow();
        buttonArea.addView(agentRow);
        executeButton = addButton(agentRow, "Execute Task", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                executeAgentTask();
            }
        });
        skillButton = addButton(agentRow, "Invoke Skill", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                invokeSkill();
            }
        });

        LinearLayout memoryRow = buttonRow();
        buttonArea.addView(memoryRow);
        memoryButton = addButton(memoryRow, "Query Memory", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                queryMemory();
            }
        });
        eventSubscriptionsButton = addButton(memoryRow, "Event Subs", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getEventSubscriptions();
            }
        });

        LinearLayout eventRow = buttonRow();
        buttonArea.addView(eventRow);
        eventSubscriptionRequestButton = addButton(eventRow, "Sub Req", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestEventSubscription();
            }
        });
        eventSubscriptionCancelButton = addButton(eventRow, "Sub Cancel", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cancelEventSubscription();
            }
        });
        eventSubscriptionTransportButton = addButton(eventRow, "Sub Link", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getEventSubscriptionTransportReadiness();
            }
        });
        eventSubscriptionDecisionButton = addButton(eventRow, "Sub Matrix", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getEventSubscriptionDecisionMatrix();
            }
        });

        LinearLayout eventGateRow = buttonRow();
        buttonArea.addView(eventGateRow);
        eventSubscriptionActivationButton = addButton(eventGateRow, "Sub Gate", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getEventSubscriptionActivationChecklist();
            }
        });
        eventSubscriptionCallbackShapeButton = addButton(eventGateRow, "Sub Shape", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getEventSubscriptionCallbackWatchShape();
            }
        });
        eventSubscriptionCursorReplayButton = addButton(eventGateRow, "Sub Cursor", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getEventSubscriptionCursorReplayStorage();
            }
        });
        eventSubscriptionBackpressureQosButton = addButton(eventGateRow, "Sub QoS", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getEventSubscriptionBackpressureQosEvidence();
            }
        });

        LinearLayout eventReadinessRow = buttonRow();
        buttonArea.addView(eventReadinessRow);
        eventSubscriptionReadinessRollupButton = addButton(eventReadinessRow, "Sub Ready", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getEventSubscriptionReadinessRollup();
            }
        });
        eventSubscriptionActivationEvidenceButton = addButton(eventReadinessRow, "Sub Evidence", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                submitEventSubscriptionActivationEvidence();
            }
        });
        eventSubscriptionActivationEvidenceStatusButton = addButton(eventReadinessRow, "Sub Review", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getEventSubscriptionActivationEvidenceStatus();
            }
        });
        eventSubscriptionActivationEvidenceRetentionButton = addButton(eventReadinessRow, "Sub Retain", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getEventSubscriptionActivationEvidenceRetentionChecklist();
            }
        });

        LinearLayout eventEvidenceDecisionRow = buttonRow();
        buttonArea.addView(eventEvidenceDecisionRow);
        eventSubscriptionActivationEvidenceDecisionStatusButton = addButton(eventEvidenceDecisionRow, "Sub Decide", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getEventSubscriptionActivationEvidenceDecisionStatusRollup();
            }
        });
        eventSubscriptionActivationApprovalDryRunStatusButton = addButton(eventEvidenceDecisionRow, "Sub ApStat", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getEventSubscriptionActivationApprovalDryRunStatus();
            }
        });
        eventSubscriptionActivationApprovalAuthorityChecklistButton = addButton(eventEvidenceDecisionRow, "Sub ApAuth", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getEventSubscriptionActivationApprovalAuthorityChecklist();
            }
        });
        eventSubscriptionActivationApprovalAuthorityAuditConsistencyButton = addButton(eventEvidenceDecisionRow, "Sub ApAudit", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getEventSubscriptionActivationApprovalAuthorityAuditConsistency();
            }
        });

        LinearLayout eventApprovalDecisionRow = buttonRow();
        buttonArea.addView(eventApprovalDecisionRow);
        eventSubscriptionActivationApprovalDecisionBlockerRollupButton = addButton(eventApprovalDecisionRow, "Sub ApBlock", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getEventSubscriptionActivationApprovalDecisionBlockerRollup();
            }
        });

        LinearLayout signalRow = buttonRow();
        buttonArea.addView(signalRow);
        vehicleSignalsButton = addButton(signalRow, "Vehicle Signals", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getVehicleSignals();
            }
        });
        vehicleSignalActivationButton = addButton(signalRow, "Signal Gate", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getVehicleSignalActivation();
            }
        });
        vehicleSignalValidationButton = addButton(signalRow, "Signal Check", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getVehicleSignalValidation();
            }
        });

        LinearLayout governanceRow = buttonRow();
        buttonArea.addView(governanceRow);
        governanceButton = addButton(governanceRow, "Precheck", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                precheckGovernance();
            }
        });
        driverGapsButton = addButton(governanceRow, "Driver Gaps", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getDriverGaps();
            }
        });

        LinearLayout hardwareRow = buttonRow();
        buttonArea.addView(hardwareRow);
        hardwareInterfacesButton = addButton(hardwareRow, "Hardware IF", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareInterfaces();
            }
        });
        hardwareActivationChecklistButton = addButton(hardwareRow, "HW Gate", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareActivationChecklist();
            }
        });
        hardwareOwnerDecisionStatusButton = addButton(hardwareRow, "HW Owner", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionStatus();
            }
        });

        LinearLayout hardwareEvidenceRow = buttonRow();
        buttonArea.addView(hardwareEvidenceRow);
        hardwareOwnerDecisionEvidenceButton = addButton(hardwareEvidenceRow, "HW Evidence", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                submitHardwareOwnerDecisionEvidence();
            }
        });
        hardwareOwnerDecisionEvidenceStatusButton = addButton(hardwareEvidenceRow, "HW EvStatus", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceStatus();
            }
        });
        hardwareOwnerDecisionEvidenceRetentionButton = addButton(hardwareEvidenceRow, "HW Retain", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceRetentionChecklist();
            }
        });
        hardwareOwnerDecisionEvidenceReplacementButton = addButton(hardwareEvidenceRow, "HW Replace", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceReplacementTriggerChecklist();
            }
        });
        hardwareOwnerDecisionEvidenceSelectedAdapterButton = addButton(hardwareEvidenceRow, "HW Adapter", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceSelectedAdapterReadinessChecklist();
            }
        });

        LinearLayout hardwareLoadRow = buttonRow();
        buttonArea.addView(hardwareLoadRow);
        hardwareOwnerDecisionEvidenceAdapterLoadBlockerButton = addButton(hardwareLoadRow, "HW Load", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadBlockerRollup();
            }
        });
        hardwareOwnerDecisionEvidenceAdapterLoadDryRunButton = addButton(hardwareLoadRow, "HW DryRun", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dryRunHardwareOwnerDecisionEvidenceAdapterLoad();
            }
        });
        hardwareOwnerDecisionEvidenceAdapterLoadDryRunStatusButton = addButton(hardwareLoadRow, "HW DryState", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadDryRunStatus();
            }
        });

        LinearLayout hardwareAuditRow = buttonRow();
        buttonArea.addView(hardwareAuditRow);
        hardwareOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistencyButton = addButton(hardwareAuditRow, "HW DryAudit", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistency();
            }
        });
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalAuthorityButton = addButton(hardwareAuditRow, "HW Approve", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklist();
            }
        });
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatusButton = addButton(hardwareAuditRow, "HW ApStat", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatus();
            }
        });

        LinearLayout hardwareApprovalAuditRow = buttonRow();
        buttonArea.addView(hardwareApprovalAuditRow);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistencyButton = addButton(hardwareApprovalAuditRow, "HW ApAudit", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistency();
            }
        });

        LinearLayout hardwareApprovalDecisionRow = buttonRow();
        buttonArea.addView(hardwareApprovalDecisionRow);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunButton = addButton(hardwareApprovalDecisionRow, "HW ApDec", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dryRunHardwareOwnerDecisionEvidenceAdapterLoadApprovalDecision();
            }
        });
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatusButton = addButton(hardwareApprovalDecisionRow, "HW ApDStat", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatus();
            }
        });

        LinearLayout hardwareApprovalDecisionAuditRow = buttonRow();
        buttonArea.addView(hardwareApprovalDecisionAuditRow);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistencyButton = addButton(hardwareApprovalDecisionAuditRow, "HW ApDAudit", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistency();
            }
        });

        LinearLayout hardwareApprovalClosureRow = buttonRow();
        buttonArea.addView(hardwareApprovalClosureRow);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionClosureBlockerMatrixButton = addButton(hardwareApprovalClosureRow, "HW ApBlock", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionClosureBlockerMatrix();
            }
        });
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionReviewerMatrixButton = addButton(hardwareApprovalClosureRow, "HW ApRev", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionReviewerMatrix();
            }
        });
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffButton = addButton(hardwareApprovalClosureRow, "HW ApHand", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoff();
            }
        });
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceStatusButton = addButton(hardwareApprovalClosureRow, "HW ApHStat", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceStatus();
            }
        });

        LinearLayout hardwareApprovalHandoffAuditRow = buttonRow();
        buttonArea.addView(hardwareApprovalHandoffAuditRow);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceAuditConsistencyButton = addButton(hardwareApprovalHandoffAuditRow, "HW ApHAud", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceAuditConsistency();
            }
        });
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceDecisionRollupButton = addButton(hardwareApprovalHandoffAuditRow, "HW ApHRoll", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceDecisionRollup();
            }
        });
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessButton = addButton(hardwareApprovalHandoffAuditRow, "HW ApHClose", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadiness();
            }
        });

        LinearLayout hardwareApprovalClosureAuditRow = buttonRow();
        buttonArea.addView(hardwareApprovalClosureAuditRow);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessAuditConsistencyButton = addButton(hardwareApprovalClosureAuditRow, "HW ApHCAud", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessAuditConsistency();
            }
        });
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionRollupButton = addButton(hardwareApprovalClosureAuditRow, "HW ApHCRoll", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionRollup();
            }
        });

        LinearLayout hardwareApprovalClosureReviewerRow = buttonRow();
        buttonArea.addView(hardwareApprovalClosureReviewerRow);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentChecklistButton = addButton(hardwareApprovalClosureReviewerRow, "HW ApHCRev", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentChecklist();
            }
        });
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistencyButton = addButton(hardwareApprovalClosureReviewerRow, "HW ApHRvA", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistency();
            }
        });
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupButton = addButton(hardwareApprovalClosureReviewerRow, "HW ApHRvRoll", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollup();
            }
        });

        LinearLayout hardwareApprovalClosureHandoffRow = buttonRow();
        buttonArea.addView(hardwareApprovalClosureHandoffRow);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummaryButton = addButton(hardwareApprovalClosureHandoffRow, "HW ApHReady", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummary();
            }
        });

        LinearLayout readinessRow = buttonRow();
        buttonArea.addView(readinessRow);
        prototypeReadinessButton = addButton(readinessRow, "Prototype", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getPrototypeReadiness();
            }
        });

        detailView = new TextView(this);
        detailView.setTextSize(13);
        detailView.setTextColor(Color.rgb(34, 45, 52));
        detailView.setPadding(0, dp(18), 0, 0);
        detailView.setTextIsSelectable(true);
        root.addView(detailView);

        return scrollView;
    }

    private void refreshHealth() {
        setBusy(true, "Status: refreshing Uni Info Bus state via Binder");
        gatewayRequest("Uni Info Bus State (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getStateJson(newTraceId("state"));
            }
        });
    }

    private void planAgentTask() {
        setBusy(true, "Status: planning Agent task via Binder");
        String body = "{\"utterance\":\"query vehicle state\",\"caller\":{\"app_id\":\"android-console\",\"role\":\"debug_console\"},"
            + "\"caller_permissions\":[\"vehicle.read\",\"service.read\"],\"vehicle_state\":\"parked\",\"safety_state\":\"normal\"}";
        gatewayRequest("AI SDK Agent Plan (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.planAgentTaskJson(newTraceId("agent-plan"), body);
            }
        });
    }

    private void executeAgentTask() {
        setBusy(true, "Status: validating Agent execute contract via Binder");
        String body = "{\"task\":{\"task_id\":\"android-console-task\",\"steps\":["
            + "{\"step_id\":\"query_vehicle_state\",\"type\":\"invoke_service\",\"service\":\"vehicle-state\","
            + "\"method\":\"getState\",\"semantic_entry\":\"POST /soa/invoke\"}]},"
            + "\"caller\":{\"app_id\":\"android-console\",\"role\":\"debug_console\"},"
            + "\"caller_permissions\":[\"vehicle.read\",\"service.read\"],\"vehicle_state\":\"parked\",\"safety_state\":\"normal\"}";
        gatewayRequest("AI SDK Agent Execute (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.executeAgentTaskJson(newTraceId("agent-execute"), body);
            }
        });
    }

    private void invokeSkill() {
        setBusy(true, "Status: invoking Skill contract via Binder");
        String body = "{\"input\":{\"intent\":\"vehicle_state_query\"},\"permissions\":[\"vehicle.read\"],"
            + "\"caller_permissions\":[\"vehicle.read\",\"service.read\"],\"vehicle_state\":\"parked\","
            + "\"safety_state\":\"normal\"}";
        gatewayRequest("Skill Invoke (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.invokeSkillJson(newTraceId("skill-invoke"), "vehicle.state.query", body);
            }
        });
    }

    private void queryMemory() {
        setBusy(true, "Status: querying local Memory contract via Binder");
        String body = "{\"query\":\"vehicle state\",\"scope\":\"vehicle_session\","
            + "\"caller_permissions\":[\"vehicle.read\",\"service.read\"],\"vehicle_state\":\"parked\","
            + "\"safety_state\":\"normal\"}";
        gatewayRequest("Memory Query (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.queryMemoryJson(newTraceId("memory-query"), body);
            }
        });
    }

    private void getEventSubscriptions() {
        setBusy(true, "Status: loading event subscription contract via Binder");
        gatewayRequest("Event Subscriptions (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getEventSubscriptionsJson(newTraceId("event-subscriptions"));
            }
        });
    }

    private void requestEventSubscription() {
        setBusy(true, "Status: validating event subscription request via Binder");
        String body = "{\"subscription_id\":\"android-console-contract-sub\","
            + "\"topics\":[\"vehicle.signal.changed\"],"
            + "\"filters\":{\"source\":\"android-console\",\"safety_state\":\"normal\"},"
            + "\"cursor\":{\"replay_limit\":5},"
            + "\"delivery\":{\"mode\":\"contract-only\",\"callback\":\"not-registered\"},"
            + "\"caller\":{\"app_id\":\"android-console\",\"role\":\"debug_console\"},"
            + "\"caller_permissions\":[\"vehicle.read\",\"service.read\"],"
            + "\"vehicle_state\":\"parked\",\"safety_state\":\"normal\"}";
        gatewayRequest("Event Subscription Request (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.requestEventSubscriptionJson(newTraceId("event-subscribe-request"), body);
            }
        });
    }

    private void cancelEventSubscription() {
        setBusy(true, "Status: validating event subscription cancel via Binder");
        String body = "{\"subscription_id\":\"android-console-contract-sub\","
            + "\"caller\":{\"app_id\":\"android-console\",\"role\":\"debug_console\"},"
            + "\"caller_permissions\":[\"vehicle.read\",\"service.read\"],"
            + "\"vehicle_state\":\"parked\",\"safety_state\":\"normal\"}";
        gatewayRequest("Event Subscription Cancel (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.cancelEventSubscriptionJson(newTraceId("event-subscribe-cancel"), body);
            }
        });
    }

    private void getEventSubscriptionTransportReadiness() {
        setBusy(true, "Status: loading event callback/watch readiness via Binder");
        gatewayRequest("Event Subscription Transport Readiness (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getEventSubscriptionTransportReadinessJson(newTraceId("event-subscription-transport"));
            }
        });
    }

    private void getEventSubscriptionDecisionMatrix() {
        setBusy(true, "Status: loading event owner decision matrix via Binder");
        gatewayRequest("Event Subscription Decision Matrix (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getEventSubscriptionDecisionMatrixJson(newTraceId("event-subscription-decision"));
            }
        });
    }

    private void getEventSubscriptionActivationChecklist() {
        setBusy(true, "Status: loading event broker activation gates via Binder");
        gatewayRequest("Event Subscription Activation Checklist (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getEventSubscriptionActivationChecklistJson(newTraceId("event-subscription-activation"));
            }
        });
    }

    private void getEventSubscriptionCallbackWatchShape() {
        setBusy(true, "Status: loading event callback/watch shape via Binder");
        gatewayRequest("Event Subscription Callback/Watch Shape (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getEventSubscriptionCallbackWatchShapeJson(newTraceId("event-subscription-callback-watch-shape"));
            }
        });
    }

    private void getEventSubscriptionCursorReplayStorage() {
        setBusy(true, "Status: loading event cursor/replay storage contract via Binder");
        gatewayRequest("Event Subscription Cursor/Replay Storage (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getEventSubscriptionCursorReplayStorageJson(newTraceId("event-subscription-cursor-replay-storage"));
            }
        });
    }

    private void getEventSubscriptionBackpressureQosEvidence() {
        setBusy(true, "Status: loading event backpressure/QoS evidence via Binder");
        gatewayRequest("Event Subscription Backpressure/QoS Evidence (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getEventSubscriptionBackpressureQosEvidenceJson(newTraceId("event-subscription-backpressure-qos-evidence"));
            }
        });
    }

    private void getEventSubscriptionReadinessRollup() {
        setBusy(true, "Status: loading event readiness rollup via Binder");
        gatewayRequest("Event Subscription Readiness Rollup (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getEventSubscriptionReadinessRollupJson(newTraceId("event-subscription-readiness-rollup"));
            }
        });
    }

    private void submitEventSubscriptionActivationEvidence() {
        setBusy(true, "Status: validating event activation evidence via Binder");
        String body = "{\"evidence_submission_id\":\"android-console-activation-evidence\","
            + "\"target_gate_ids\":[\"EV-ACT-001\",\"EV-RU-001\",\"DRV-GAP-004\"],"
            + "\"evidence_refs\":[{\"ref_id\":\"android-console-evidence-doc\","
            + "\"type\":\"doc\",\"uri_or_path\":\"docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md\","
            + "\"owner\":\"android-console\",\"summary\":\"contract-only evidence reference sample\"}],"
            + "\"reviewer\":{\"app_id\":\"android-console\",\"role\":\"debug_console\"},"
            + "\"caller_permissions\":[\"vehicle.read\",\"service.read\"],"
            + "\"vehicle_state\":\"parked\",\"safety_state\":\"normal\"}";
        gatewayRequest("Event Subscription Activation Evidence (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.submitEventSubscriptionActivationEvidenceJson(newTraceId("event-subscription-activation-evidence"), body);
            }
        });
    }

    private void getEventSubscriptionActivationEvidenceStatus() {
        setBusy(true, "Status: loading event activation evidence review status via Binder");
        gatewayRequest("Event Subscription Activation Evidence Status (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getEventSubscriptionActivationEvidenceStatusJson(newTraceId("event-subscription-activation-evidence-status"));
            }
        });
    }

    private void getEventSubscriptionActivationEvidenceRetentionChecklist() {
        setBusy(true, "Status: loading event activation evidence retention checklist via Binder");
        gatewayRequest("Event Subscription Activation Evidence Retention Checklist (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getEventSubscriptionActivationEvidenceRetentionChecklistJson(newTraceId("event-subscription-activation-evidence-retention-checklist"));
            }
        });
    }

    private void getEventSubscriptionActivationEvidenceDecisionStatusRollup() {
        setBusy(true, "Status: loading event activation evidence decision status via Binder");
        gatewayRequest("Event Subscription Activation Evidence Decision Status Rollup (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getEventSubscriptionActivationEvidenceDecisionStatusRollupJson(newTraceId("event-subscription-activation-evidence-decision-status-rollup"));
            }
        });
    }

    private void getEventSubscriptionActivationApprovalDryRunStatus() {
        setBusy(true, "Status: loading event activation approval dry-run status via Binder");
        gatewayRequest("Event Subscription Activation Approval Dry-Run Status (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getEventSubscriptionActivationApprovalDryRunStatusJson(newTraceId("event-subscription-activation-approval-dry-run-status"));
            }
        });
    }

    private void getEventSubscriptionActivationApprovalAuthorityChecklist() {
        setBusy(true, "Status: loading event activation approval authority checklist via Binder");
        gatewayRequest("Event Subscription Activation Approval Authority Checklist (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getEventSubscriptionActivationApprovalAuthorityChecklistJson(newTraceId("event-subscription-activation-approval-authority-checklist"));
            }
        });
    }

    private void getEventSubscriptionActivationApprovalAuthorityAuditConsistency() {
        setBusy(true, "Status: loading event activation approval authority audit via Binder");
        gatewayRequest("Event Subscription Activation Approval Authority Audit (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getEventSubscriptionActivationApprovalAuthorityAuditConsistencyJson(newTraceId("event-subscription-activation-approval-authority-audit"));
            }
        });
    }

    private void getEventSubscriptionActivationApprovalDecisionBlockerRollup() {
        setBusy(true, "Status: loading event activation approval decision blocker rollup via Binder");
        gatewayRequest("Event Subscription Activation Approval Decision Blocker Rollup (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getEventSubscriptionActivationApprovalDecisionBlockerRollupJson(newTraceId("event-subscription-activation-approval-decision-blocker-rollup"));
            }
        });
    }

    private void precheckGovernance() {
        setBusy(true, "Status: checking Runtime & Governance via Binder");
        String body = "{\"service\":\"vehicle-state\",\"method\":\"getState\","
            + "\"caller_permissions\":[\"vehicle.read\",\"service.read\"],\"vehicle_state\":\"parked\","
            + "\"safety_state\":\"normal\",\"consume_qos\":false}";
        gatewayRequest("Governance Precheck (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.precheckGovernanceJson(newTraceId("governance-precheck"), body);
            }
        });
    }

    private void getDriverGaps() {
        setBusy(true, "Status: loading Driver/HAL gap backlog via Binder");
        gatewayRequest("Driver/HAL Gaps (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getDriverHalGapsJson(newTraceId("driver-gaps"));
            }
        });
    }

    private void getHardwareInterfaces() {
        setBusy(true, "Status: loading hardware empty interfaces via Binder");
        gatewayRequest("Hardware Empty Interfaces (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfacesJson(newTraceId("hardware-interfaces"));
            }
        });
    }

    private void getHardwareActivationChecklist() {
        setBusy(true, "Status: loading hardware activation checklist via Binder");
        gatewayRequest("Hardware Activation Checklist (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceActivationChecklistJson(newTraceId("hardware-interface-activation-checklist"));
            }
        });
    }

    private void getHardwareOwnerDecisionStatus() {
        setBusy(true, "Status: loading hardware owner decision status via Binder");
        gatewayRequest("Hardware Owner Decision Status (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionStatusJson(newTraceId("hardware-interface-owner-decision-status"));
            }
        });
    }

    private void submitHardwareOwnerDecisionEvidence() {
        setBusy(true, "Status: submitting hardware owner decision evidence via Binder");
        String body = "{\"evidence_submission_id\":\"android-console-hw-owner-evidence\","
                + "\"target_interface_ids\":[\"npu-runtime\"],"
                + "\"target_gate_ids\":[\"HW-ODS-001\",\"HW-ODS-006\",\"DRV-GAP-001\"],"
                + "\"evidence_refs\":[{\"ref_id\":\"android-console-hw-owner-doc\","
                + "\"type\":\"owner_approval\","
                + "\"uri_or_path\":\"docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md\","
                + "\"owner\":\"android-console\","
                + "\"summary\":\"contract-only hardware owner evidence reference\"}],"
                + "\"reviewer\":{\"app_id\":\"central-brain-console\",\"role\":\"debug_console\"},"
                + "\"caller_permissions\":[\"vehicle.read\",\"service.read\"],"
                + "\"vehicle_state\":\"parked\","
                + "\"safety_state\":\"normal\"}";
        gatewayRequest("Hardware Owner Decision Evidence (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.submitHardwareInterfaceOwnerDecisionEvidenceJson(newTraceId("hardware-interface-owner-decision-evidence"), body);
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceStatus() {
        setBusy(true, "Status: loading hardware owner evidence status via Binder");
        gatewayRequest("Hardware Owner Evidence Status (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceStatusJson(newTraceId("hardware-interface-owner-decision-evidence-status"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceRetentionChecklist() {
        setBusy(true, "Status: loading hardware owner evidence retention checklist via Binder");
        gatewayRequest("Hardware Owner Evidence Retention Checklist (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceRetentionChecklistJson(newTraceId("hardware-interface-owner-decision-evidence-retention-checklist"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceReplacementTriggerChecklist() {
        setBusy(true, "Status: loading hardware owner evidence replacement trigger checklist via Binder");
        gatewayRequest("Hardware Owner Evidence Replacement Trigger Checklist (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklistJson(newTraceId("hardware-interface-owner-decision-evidence-replacement-trigger-checklist"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceSelectedAdapterReadinessChecklist() {
        setBusy(true, "Status: loading hardware owner evidence selected adapter readiness checklist via Binder");
        gatewayRequest("Hardware Owner Evidence Selected Adapter Readiness Checklist (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklistJson(newTraceId("hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadBlockerRollup() {
        setBusy(true, "Status: loading hardware owner evidence adapter load blocker rollup via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Blocker Rollup (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollupJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-blocker-rollup"));
            }
        });
    }

    private void dryRunHardwareOwnerDecisionEvidenceAdapterLoad() {
        setBusy(true, "Status: running hardware owner evidence adapter load dry-run via Binder");
        String body = "{\"dry_run_request_id\":\"android-console-hw-adapter-load-dry-run\","
                + "\"selected_interface_id\":\"npu-runtime\","
                + "\"selected_adapter_id\":\"target-platform-npu-adapter\","
                + "\"adapter_version\":\"0.0.0-contract\","
                + "\"evidence_refs\":[{\"ref_id\":\"android-console-hw-adapter-load-approval\","
                + "\"type\":\"owner_approval\","
                + "\"uri_or_path\":\"docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md\","
                + "\"owner\":\"android-console\","
                + "\"summary\":\"contract-only adapter-load dry-run approval reference\"}],"
                + "\"requested_by\":{\"app_id\":\"central-brain-console\",\"role\":\"debug_console\"},"
                + "\"caller_permissions\":[\"vehicle.read\",\"service.read\"],"
                + "\"vehicle_state\":\"parked\","
                + "\"safety_state\":\"normal\"}";
        gatewayRequest("Hardware Owner Evidence Adapter Load Dry-Run (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-dry-run"), body);
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadDryRunStatus() {
        setBusy(true, "Status: loading hardware owner evidence adapter load dry-run status via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Dry-Run Status (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatusJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-dry-run-status"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistency() {
        setBusy(true, "Status: loading hardware owner evidence adapter load dry-run audit consistency via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Dry-Run Audit Consistency (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistencyJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-dry-run-audit-consistency"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklist() {
        setBusy(true, "Status: loading hardware owner evidence adapter load approval authority checklist via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Approval Authority Checklist (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklistJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-approval-authority-checklist"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatus() {
        setBusy(true, "Status: loading hardware owner evidence adapter load approval authority status via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Approval Authority Status (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatusJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-approval-authority-status"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistency() {
        setBusy(true, "Status: loading hardware owner evidence adapter load approval authority audit consistency via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Approval Authority Audit Consistency (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistencyJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-approval-authority-audit-consistency"));
            }
        });
    }

    private void dryRunHardwareOwnerDecisionEvidenceAdapterLoadApprovalDecision() {
        setBusy(true, "Status: running hardware owner evidence adapter load approval decision dry-run via Binder");
        String body = "{\"approval_decision_request_id\":\"android-console-hw-approval-decision-dry-run\","
                + "\"selected_interface_id\":\"npu-runtime\","
                + "\"selected_adapter_id\":\"target-platform-npu-adapter\","
                + "\"adapter_version\":\"0.0.0-contract\","
                + "\"approval_decision\":\"approve_adapter_load\","
                + "\"approval_authority\":\"target-platform-approval-authority\","
                + "\"approval_signature\":\"contract-only-signature-placeholder\","
                + "\"evidence_refs\":[{\"ref_id\":\"android-console-hw-approval-decision-evidence\","
                + "\"type\":\"approval_authority\","
                + "\"uri_or_path\":\"docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md\","
                + "\"owner\":\"android-console\","
                + "\"summary\":\"contract-only approval decision dry-run evidence reference\"}],"
                + "\"requested_by\":{\"app_id\":\"central-brain-console\",\"role\":\"debug_console\"},"
                + "\"caller_permissions\":[\"vehicle.read\",\"service.read\"],"
                + "\"vehicle_state\":\"parked\","
                + "\"safety_state\":\"normal\"}";
        gatewayRequest("Hardware Owner Evidence Adapter Load Approval Decision Dry-Run (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run"), body);
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatus() {
        setBusy(true, "Status: loading hardware owner evidence adapter load approval decision dry-run status via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Approval Decision Dry-Run Status (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatusJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run-status"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistency() {
        setBusy(true, "Status: loading hardware owner evidence adapter load approval decision dry-run audit consistency via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Approval Decision Dry-Run Audit Consistency (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistencyJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-approval-decision-dry-run-audit-consistency"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionClosureBlockerMatrix() {
        setBusy(true, "Status: loading hardware owner evidence adapter load approval decision closure blocker matrix via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Approval Decision Closure Blocker Matrix (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionClosureBlockerMatrixJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-approval-decision-closure-blocker-matrix"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionReviewerMatrix() {
        setBusy(true, "Status: loading hardware owner evidence adapter load approval decision reviewer matrix via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Approval Decision Reviewer Matrix (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionReviewerMatrixJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-approval-decision-reviewer-matrix"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoff() {
        setBusy(true, "Status: loading hardware owner evidence adapter load approval reviewer evidence handoff via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Approval Reviewer Evidence Handoff (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffChecklistJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-checklist"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceStatus() {
        setBusy(true, "Status: loading hardware owner evidence adapter load approval reviewer evidence handoff acceptance status via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Approval Reviewer Evidence Handoff Acceptance Status (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceStatusJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-status"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceAuditConsistency() {
        setBusy(true, "Status: loading hardware owner evidence adapter load approval reviewer evidence handoff acceptance audit consistency via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Approval Reviewer Evidence Handoff Acceptance Audit Consistency (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceAuditConsistencyJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-audit-consistency"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceDecisionRollup() {
        setBusy(true, "Status: loading hardware owner evidence adapter load approval reviewer evidence handoff acceptance decision rollup via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Approval Reviewer Evidence Handoff Acceptance Decision Rollup (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceDecisionRollupJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-decision-rollup"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadiness() {
        setBusy(true, "Status: loading hardware owner evidence adapter load approval reviewer evidence handoff acceptance closure readiness via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Approval Reviewer Evidence Handoff Acceptance Closure Readiness (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessChecklistJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-checklist"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessAuditConsistency() {
        setBusy(true, "Status: loading hardware owner evidence adapter load approval reviewer evidence handoff acceptance closure readiness audit consistency via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Approval Reviewer Evidence Handoff Acceptance Closure Readiness Audit Consistency (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessAuditConsistencyJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-audit-consistency"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionRollup() {
        setBusy(true, "Status: loading hardware owner evidence adapter load approval reviewer evidence handoff acceptance closure readiness decision rollup via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Approval Reviewer Evidence Handoff Acceptance Closure Readiness Decision Rollup (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionRollupJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-rollup"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentChecklist() {
        setBusy(true, "Status: loading hardware owner evidence adapter load approval reviewer evidence handoff acceptance closure readiness decision reviewer assignment checklist via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Approval Reviewer Evidence Handoff Acceptance Closure Readiness Decision Reviewer Assignment Checklist (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentChecklistJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-checklist"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistency() {
        setBusy(true, "Status: loading hardware owner evidence adapter load approval reviewer evidence handoff acceptance closure readiness decision reviewer assignment audit consistency via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Approval Reviewer Evidence Handoff Acceptance Closure Readiness Decision Reviewer Assignment Audit Consistency (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistencyJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-consistency"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollup() {
        setBusy(true, "Status: loading hardware owner evidence adapter load approval reviewer evidence handoff acceptance closure readiness decision reviewer assignment audit decision rollup via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Approval Reviewer Evidence Handoff Acceptance Closure Readiness Decision Reviewer Assignment Audit Decision Rollup (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup"));
            }
        });
    }

    private void getHardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummary() {
        setBusy(true, "Status: loading hardware owner evidence adapter load approval reviewer evidence handoff acceptance closure handoff readiness summary via Binder");
        gatewayRequest("Hardware Owner Evidence Adapter Load Approval Reviewer Evidence Handoff Acceptance Closure Handoff Readiness Summary (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummaryJson(newTraceId("hardware-interface-owner-decision-evidence-adapter-load-approval-reviewer-evidence-handoff-acceptance-closure-readiness-decision-reviewer-assignment-audit-decision-rollup-closure-handoff-readiness-summary"));
            }
        });
    }

    private void getPrototypeReadiness() {
        setBusy(true, "Status: loading Python prototype readiness via Binder");
        gatewayRequest("Prototype Readiness (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getPrototypeReadinessJson(newTraceId("prototype-readiness"));
            }
        });
    }

    private void getVehicleSignals() {
        setBusy(true, "Status: loading vehicle signal catalog via Binder");
        gatewayRequest("Vehicle Signals (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getVehicleSignalsJson(newTraceId("vehicle-signals"));
            }
        });
    }

    private void getVehicleSignalActivation() {
        setBusy(true, "Status: loading vehicle signal activation gates via Binder");
        gatewayRequest("Vehicle Signal Activation (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getVehicleSignalActivationJson(newTraceId("vehicle-signal-activation"));
            }
        });
    }

    private void getVehicleSignalValidation() {
        setBusy(true, "Status: loading vehicle signal validation envelope via Binder");
        gatewayRequest("Vehicle Signal Validation (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.getVehicleSignalValidationJson(newTraceId("vehicle-signal-validation"));
            }
        });
    }

    private void bindGateway() {
        setBusy(true, "Status: binding Android gateway service");
        gatewayClient = new CentralBrainGatewayClient(this, new CentralBrainGatewayClient.Callback() {
            @Override
            public void onConnected(CentralBrainGatewayClient client) {
                gatewayBound = true;
                postResult("Status: Binder gateway connected", "Req IDs: XSC-001, APP-004, XSC-002, XSC-003, XSC-006, NV-P-002, DEL-001\n"
                    + "Upstream prototype binding: " + BASE_URL + "\n\n"
                    + "Use Refresh, Plan, Execute, Skill, Memory, Event Subs, Sub Req, Sub Cancel, Sub Link, Sub Matrix, Sub Gate, Sub Shape, Sub Cursor, Sub QoS, Sub Ready, Sub Evidence, Sub Review, Sub Retain, Sub Decide, Sub ApStat, Sub ApAuth, Sub ApAudit, Sub ApBlock, Vehicle Signals, Signal Gate, Signal Check, Governance, Driver Gaps, Hardware IF, HW Gate, HW Owner, HW Evidence, HW EvStatus, HW Retain, HW Replace, HW Adapter, HW Load, HW DryRun, HW DryState, HW DryAudit, HW Approve, HW ApStat, HW ApAudit, HW ApDec, HW ApDStat, HW ApDAudit, HW ApBlock, HW ApRev, HW ApHand, HW ApHStat, HW ApHAud, or Prototype to exercise the Android Binder path.");
            }

            @Override
            public void onDisconnected() {
                gatewayBound = false;
                postResult("Status: Binder gateway disconnected", "Central Brain gateway service disconnected.");
            }
        });
        if (!gatewayClient.bind(BASE_URL)) {
            gatewayBound = false;
            postResult("Status: Binder gateway bind failed",
                "Unable to bind Central Brain gateway service.\n\nReq IDs: XSC-006, NV-P-002, DEL-001");
        }
    }

    private void gatewayRequest(String label, GatewayCall call) {
        if (!gatewayBound || gatewayClient == null) {
            postResult("Status: Binder gateway unavailable", "Central Brain Binder gateway is not connected.");
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    postResult("Status: " + label + " OK", call.run(gatewayClient));
                } catch (Exception e) {
                    postResult("Status: backend unavailable", e.getClass().getSimpleName() + ": " + e.getMessage()
                        + "\n\nStart backend with:\n  bash tools/run_central_brain_backend.sh");
                }
            }
        }, "central-brain-request").start();
    }

    private void postResult(String status, String detail) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                setBusy(false, status);
                detailView.setText(detail);
            }
        });
    }

    private void setBusy(boolean busy, String status) {
        statusView.setText(status);
        boolean enabled = !busy && gatewayBound;
        refreshButton.setEnabled(enabled);
        planButton.setEnabled(enabled);
        executeButton.setEnabled(enabled);
        skillButton.setEnabled(enabled);
        memoryButton.setEnabled(enabled);
        eventSubscriptionsButton.setEnabled(enabled);
        eventSubscriptionRequestButton.setEnabled(enabled);
        eventSubscriptionCancelButton.setEnabled(enabled);
        eventSubscriptionTransportButton.setEnabled(enabled);
        eventSubscriptionDecisionButton.setEnabled(enabled);
        eventSubscriptionActivationButton.setEnabled(enabled);
        eventSubscriptionCallbackShapeButton.setEnabled(enabled);
        eventSubscriptionCursorReplayButton.setEnabled(enabled);
        eventSubscriptionBackpressureQosButton.setEnabled(enabled);
        eventSubscriptionReadinessRollupButton.setEnabled(enabled);
        eventSubscriptionActivationEvidenceButton.setEnabled(enabled);
        eventSubscriptionActivationEvidenceStatusButton.setEnabled(enabled);
        eventSubscriptionActivationEvidenceRetentionButton.setEnabled(enabled);
        eventSubscriptionActivationEvidenceDecisionStatusButton.setEnabled(enabled);
        eventSubscriptionActivationApprovalDryRunStatusButton.setEnabled(enabled);
        eventSubscriptionActivationApprovalAuthorityChecklistButton.setEnabled(enabled);
        eventSubscriptionActivationApprovalAuthorityAuditConsistencyButton.setEnabled(enabled);
        eventSubscriptionActivationApprovalDecisionBlockerRollupButton.setEnabled(enabled);
        vehicleSignalsButton.setEnabled(enabled);
        vehicleSignalActivationButton.setEnabled(enabled);
        vehicleSignalValidationButton.setEnabled(enabled);
        governanceButton.setEnabled(enabled);
        driverGapsButton.setEnabled(enabled);
        hardwareInterfacesButton.setEnabled(enabled);
        hardwareActivationChecklistButton.setEnabled(enabled);
        hardwareOwnerDecisionStatusButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceStatusButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceRetentionButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceReplacementButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceSelectedAdapterButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadBlockerButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadDryRunButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadDryRunStatusButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistencyButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalAuthorityButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatusButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistencyButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatusButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistencyButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionClosureBlockerMatrixButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalDecisionReviewerMatrixButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceStatusButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceAuditConsistencyButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceDecisionRollupButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessAuditConsistencyButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionRollupButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentChecklistButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistencyButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupButton.setEnabled(enabled);
        hardwareOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummaryButton.setEnabled(enabled);
        prototypeReadinessButton.setEnabled(enabled);
    }

    private LinearLayout buttonRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.START);
        return row;
    }

    private Button addButton(LinearLayout row, String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(row.getChildCount() == 0 ? 0 : dp(8), dp(4), 0, dp(4));
        row.addView(button, params);
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String newTraceId(String operation) {
        return "android-console-binder-" + operation + "-" + System.currentTimeMillis();
    }
}
