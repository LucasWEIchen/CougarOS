package com.centralbrain.demo;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.centralbrain.sdk.CentralBrainSdk;

/** Req IDs: APP-004, XSC-001, XSC-006, DEL-001. */
public final class DemoActivity extends Activity {
    private static final int CONTENT_PADDING_DP = 32;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int padding = Math.round(CONTENT_PADDING_DP * getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(padding, padding, padding, padding);
        content.setBackgroundColor(Color.rgb(245, 247, 248));

        TextView title = new TextView(this);
        title.setText(R.string.app_name);
        title.setTextColor(Color.rgb(25, 31, 35));
        title.setTextSize(30);
        content.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView status = new TextView(this);
        status.setText(getString(
                R.string.foundation_status,
                CentralBrainSdk.SDK_VERSION,
                CentralBrainSdk.MATURITY));
        status.setTextColor(Color.rgb(67, 77, 84));
        status.setTextSize(17);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = Math.round(12 * getResources().getDisplayMetrics().density);
        content.addView(status, statusParams);

        setContentView(content);
    }
}
