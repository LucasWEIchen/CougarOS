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
import com.centralbrain.sdk.diagnostics.DiagnosticRecord;
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
                DiagnosticQuery activationQuery = new DiagnosticQuery();
                activationQuery.pageSize = ICentralBrainDiagnostics.MAX_PAGE_SIZE;
                DiagnosticPage activationPage = diagnostics.getPage(activationQuery);
                boolean activationVerified = hasBlockedEffectActivation(activationPage);
                boolean modelRuntimeVerified = hasBlockedModelRuntime(activationPage);
                boolean eventRuntimeVerified = hasBlockedEventRuntime(activationPage);
                boolean memoryRuntimeVerified = hasBlockedMemoryRuntime(activationPage);
                boolean passed = diagnostics.getProtocolVersion() == 1
                        && ICentralBrainDiagnostics.INTERFACE_HASH.equals(
                                diagnostics.getProtocolHash())
                        && page != null
                        && page.records != null
                        && page.records.length == 1
                        && page.hasMore
                        && activationVerified
                        && modelRuntimeVerified
                        && eventRuntimeVerified
                        && memoryRuntimeVerified;
                Log.i(TAG, "nonce=" + nonce + " diagnostic_probe_passed=" + passed
                        + " effect_delivery_activation_diagnostic_verified="
                        + activationVerified
                        + " model_runtime_readiness_diagnostic_verified="
                        + modelRuntimeVerified
                        + " event_runtime_readiness_diagnostic_verified="
                        + eventRuntimeVerified
                        + " memory_runtime_readiness_diagnostic_verified="
                        + memoryRuntimeVerified
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

    private static boolean hasBlockedEffectActivation(DiagnosticPage page) {
        if (page == null || page.records == null) {
            return false;
        }
        for (DiagnosticRecord record : page.records) {
            if (record != null
                    && "effect-delivery-activation".equals(record.recordId)
                    && "blocked".equals(record.summary)
                    && record.detail != null
                    && record.detail.contains("activation_allowed=false")
                    && record.detail.contains("adapter_configured=false")
                    && record.detail.contains("material_source=empty.effect.material")
                    && record.detail.contains("ADAPTER_MISSING")
                    && record.detail.contains("MATERIAL_SOURCE_EMPTY")
                    && record.detail.contains("apply_enabled=false")
                    && record.detail.contains("status_query_enabled=false")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBlockedModelRuntime(DiagnosticPage page) {
        if (page == null || page.records == null) {
            return false;
        }
        for (DiagnosticRecord record : page.records) {
            if (record != null
                    && "model-runtime-readiness".equals(record.recordId)
                    && "blocked".equals(record.summary)
                    && record.detail != null
                    && record.detail.contains("production_inference_allowed=false")
                    && record.detail.contains("model_provider_contract_available=true")
                    && record.detail.contains(
                            "test_model_router_implementation_available=true")
                    && record.detail.contains(
                            "deterministic_stub_profile_id=deterministic.stub")
                    && record.detail.contains("deterministic_stub_lifecycle=COLD")
                    && record.detail.contains("deterministic_stub_health=HEALTHY")
                    && record.detail.contains(
                            "deterministic_stub_detail_code=STUB_IMPLEMENTATION_NOT_WIRED")
                    && record.detail.contains(
                            "deterministic_stub_implementation_configured=false")
                    && record.detail.contains(
                            "deterministic_stub_routing_enabled=false")
                    && record.detail.contains("vendor_npu_profile_id=vendor.npu.empty")
                    && record.detail.contains("vendor_npu_lifecycle=UNAVAILABLE")
                    && record.detail.contains("vendor_npu_health=UNAVAILABLE")
                    && record.detail.contains(
                            "vendor_npu_detail_code=VENDOR_RUNTIME_UNAVAILABLE")
                    && record.detail.contains("vendor_npu_provider_available=false")
                    && record.detail.contains("scheduler_production_wired=false")
                    && record.detail.contains("production_model_router_wired=false")
                    && record.detail.contains(
                            "production_model_router_dispatch_enabled=false")
                    && record.detail.contains("VENDOR_NPU_INTERFACE_EMPTY")
                    && record.detail.contains("hardware_accessed=false")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBlockedEventRuntime(DiagnosticPage page) {
        if (page == null || page.records == null) {
            return false;
        }
        for (DiagnosticRecord record : page.records) {
            if (record != null
                    && "event-runtime-readiness".equals(record.recordId)
                    && "blocked".equals(record.summary)
                    && record.detail != null
                    && record.detail.contains("event_runtime_activation_allowed=false")
                    && record.detail.contains(
                            "bounded_event_runtime_implementation_available=true")
                    && record.detail.contains("event_cursor_schema_ready=true")
                    && record.detail.contains(
                            "event_repository_implementation_available=true")
                    && record.detail.contains("trusted_event_topic_count=3")
                    && record.detail.contains("durable_event_source_available=false")
                    && record.detail.contains("event_runtime_production_wired=false")
                    && record.detail.contains(
                            "event_cursor_repository_production_wired=false")
                    && record.detail.contains("event_cursor_persistence_wired=false")
                    && record.detail.contains("event_callback_binder_wired=false")
                    && record.detail.contains("event_broker_production_wired=false")
                    && record.detail.contains("event_middleware_chain_wired=false")
                    && record.detail.contains("DURABLE_PUBLISHER_SEQUENCE_MISSING")
                    && record.detail.contains("MIDDLEWARE_CHAIN_NOT_WIRED")
                    && record.detail.contains("hardware_accessed=false")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBlockedMemoryRuntime(DiagnosticPage page) {
        if (page == null || page.records == null) {
            return false;
        }
        for (DiagnosticRecord record : page.records) {
            if (record != null
                    && "memory-runtime-readiness".equals(record.recordId)
                    && "blocked".equals(record.summary)
                    && record.detail != null
                    && record.detail.contains("memory_runtime_activation_allowed=false")
                    && record.detail.contains(
                            "bounded_memory_lifecycle_implementation_available=true")
                    && record.detail.contains("memory_scope_count=3")
                    && record.detail.contains("memory_schema_ready=false")
                    && record.detail.contains(
                            "memory_repository_implementation_available=false")
                    && record.detail.contains(
                            "durable_encrypted_memory_storage_available=false")
                    && record.detail.contains(
                            "memory_encryption_key_lifecycle_configured=false")
                    && record.detail.contains("memory_consent_authority_wired=false")
                    && record.detail.contains("memory_consent_revocation_wired=false")
                    && record.detail.contains(
                            "trusted_memory_retention_clock_wired=false")
                    && record.detail.contains("memory_runtime_production_wired=false")
                    && record.detail.contains(
                            "memory_repository_production_wired=false")
                    && record.detail.contains("memory_middleware_chain_wired=false")
                    && record.detail.contains("raw_memory_content_stored=false")
                    && record.detail.contains("profile_memory_storage_durable=false")
                    && record.detail.contains("DURABLE_ENCRYPTED_STORAGE_MISSING")
                    && record.detail.contains("MIDDLEWARE_CHAIN_NOT_WIRED")
                    && record.detail.contains("hardware_accessed=false")) {
                return true;
            }
        }
        return false;
    }
}
