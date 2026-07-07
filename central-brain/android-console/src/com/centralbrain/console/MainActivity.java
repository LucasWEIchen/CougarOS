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
    private Button vehicleSignalsButton;
    private Button vehicleSignalActivationButton;
    private Button vehicleSignalValidationButton;
    private Button governanceButton;
    private Button driverGapsButton;
    private Button hardwareInterfacesButton;
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
        prototypeReadinessButton = addButton(hardwareRow, "Prototype", new View.OnClickListener() {
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
                    + "Use Refresh, Plan, Execute, Skill, Memory, Event Subs, Sub Req, Sub Cancel, Sub Link, Sub Matrix, Sub Gate, Sub Shape, Sub Cursor, Sub QoS, Vehicle Signals, Signal Gate, Signal Check, Governance, Driver Gaps, Hardware IF, or Prototype to exercise the Android Binder path.");
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
        vehicleSignalsButton.setEnabled(enabled);
        vehicleSignalActivationButton.setEnabled(enabled);
        vehicleSignalValidationButton.setEnabled(enabled);
        governanceButton.setEnabled(enabled);
        driverGapsButton.setEnabled(enabled);
        hardwareInterfacesButton.setEnabled(enabled);
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
