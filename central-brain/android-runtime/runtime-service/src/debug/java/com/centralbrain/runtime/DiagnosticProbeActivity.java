package com.centralbrain.runtime;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.centralbrain.sdk.diagnostics.DiagnosticPage;
import com.centralbrain.sdk.diagnostics.DiagnosticQuery;
import com.centralbrain.sdk.diagnostics.ICentralBrainDiagnostics;

/** ADB-only diagnostic Binder probe. Req IDs: XSC-005, XSC-006, NV-G-007, NV-P-002. */
public final class DiagnosticProbeActivity extends Activity {
    private static final String TAG = "CentralBrainDiagProbe";

    private String nonce = "missing";
    private boolean bound;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                ICentralBrainDiagnostics diagnostics =
                        ICentralBrainDiagnostics.Stub.asInterface(service);
                DiagnosticQuery query = new DiagnosticQuery();
                query.pageSize = 1;
                DiagnosticPage page = diagnostics.getPage(query);
                boolean passed = diagnostics.getProtocolVersion() == 1
                        && ICentralBrainDiagnostics.INTERFACE_HASH.equals(
                                diagnostics.getProtocolHash())
                        && page != null
                        && page.records != null
                        && page.records.length == 1
                        && page.hasMore;
                Log.i(TAG, "nonce=" + nonce + " diagnostic_probe_passed=" + passed
                        + " record_count=" + (page == null || page.records == null
                                ? -1 : page.records.length)
                        + " hardware_accessed=false");
            } catch (RemoteException | RuntimeException exception) {
                Log.e(TAG, "nonce=" + nonce + " diagnostic_probe_passed=false", exception);
            } finally {
                finishProbe();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.e(TAG, "nonce=" + nonce + " diagnostic_probe_passed=false disconnected=true");
            finishProbe();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!BuildConfig.DEBUG) {
            throw new IllegalStateException("Diagnostic probe must never run in a release build");
        }
        String requestedNonce = getIntent().getStringExtra("nonce");
        nonce = requestedNonce == null ? "missing" : requestedNonce;
        Intent intent = new Intent(this, CentralBrainDiagnosticService.class);
        bound = bindService(intent, connection, Context.BIND_AUTO_CREATE);
        if (!bound) {
            Log.e(TAG, "nonce=" + nonce + " diagnostic_probe_passed=false bind=false");
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        super.onDestroy();
    }

    private void finishProbe() {
        if (bound) {
            unbindService(connection);
            bound = false;
        }
        finish();
    }
}
