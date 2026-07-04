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
    private Button inferButton;
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
        subtitle.setText("Android app layer -> Binder gateway -> Uni Info Bus/SOA prototype");
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

        LinearLayout buttonRow = new LinearLayout(this);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.START);
        root.addView(buttonRow);

        refreshButton = new Button(this);
        refreshButton.setText("Refresh");
        refreshButton.setAllCaps(false);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                refreshHealth();
            }
        });
        buttonRow.addView(refreshButton);

        inferButton = new Button(this);
        inferButton.setText("Invoke SOA Inference");
        inferButton.setAllCaps(false);
        inferButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                runInference();
            }
        });
        buttonRow.addView(inferButton);

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

    private void runInference() {
        setBusy(true, "Status: invoking SOA inference via Binder");
        String body = "{\"service\":\"npu-inference\",\"method\":\"infer\",\"caller_permissions\":[\"ai.infer\",\"service.read\"],"
            + "\"payload\":{\"model\":\"central-intent-v0\",\"input\":{\"utterance\":\"query vehicle state\"},"
            + "\"policy\":{\"safety_state_required\":\"normal\",\"timeout_ms\":2000}}}";
        gatewayRequest("SOA Inference (Binder)", new GatewayCall() {
            @Override
            public String run(CentralBrainGatewayClient client) throws RemoteException {
                return client.invokeServiceJson(newTraceId("soa"), body);
            }
        });
    }

    private void bindGateway() {
        setBusy(true, "Status: binding Android gateway service");
        gatewayClient = new CentralBrainGatewayClient(this, new CentralBrainGatewayClient.Callback() {
            @Override
            public void onConnected(CentralBrainGatewayClient client) {
                gatewayBound = true;
                postResult("Status: Binder gateway connected", "Req IDs: XSC-002, XSC-003, XSC-006, NV-P-002, DEL-001\n"
                    + "Upstream prototype binding: " + BASE_URL + "\n\n"
                    + "Use Refresh or Invoke SOA Inference to exercise the Android Binder path.");
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
        refreshButton.setEnabled(!busy && gatewayBound);
        inferButton.setEnabled(!busy && gatewayBound);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static String newTraceId(String operation) {
        return "android-console-binder-" + operation + "-" + System.currentTimeMillis();
    }
}
