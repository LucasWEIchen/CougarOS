package com.centralbrain.console;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.BufferedReader;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String BASE_URL = "http://10.0.2.2:8787";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextView statusView;
    private TextView detailView;
    private Button refreshButton;
    private Button inferButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(buildContentView());
        refreshHealth();
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
        subtitle.setText("Android app layer -> Uni Info Bus prototype -> mock PCIe NPU backend");
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
        inferButton.setText("Run Mock Inference");
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
        setBusy(true, "Status: refreshing");
        request("GET", "/health", null, "Health");
    }

    private void runInference() {
        setBusy(true, "Status: running mock inference");
        String body = "{\"model\":\"central-intent-v0\",\"input\":{\"utterance\":\"query vehicle state\"},\"policy\":{\"safety_state_required\":\"normal\",\"timeout_ms\":2000}}";
        request("POST", "/ai/infer", body, "Inference");
    }

    private void request(String method, String path, String body, String label) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    HttpURLConnection connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
                    connection.setRequestMethod(method);
                    connection.setConnectTimeout(2000);
                    connection.setReadTimeout(4000);
                    connection.setRequestProperty("Accept", "application/json");
                    if (body != null) {
                        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                        connection.setDoOutput(true);
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                        connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
                        try (OutputStream output = connection.getOutputStream()) {
                            output.write(bytes);
                        }
                    }

                    int code = connection.getResponseCode();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                        code >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                        StandardCharsets.UTF_8
                    ));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line).append('\n');
                    }
                    reader.close();
                    postResult("Status: " + label + " HTTP " + code, response.toString());
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
        refreshButton.setEnabled(!busy);
        inferButton.setEnabled(!busy);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
