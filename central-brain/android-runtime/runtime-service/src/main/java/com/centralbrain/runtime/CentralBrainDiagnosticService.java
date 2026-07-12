package com.centralbrain.runtime;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import com.centralbrain.sdk.CentralBrainSdk;
import com.centralbrain.sdk.diagnostics.DiagnosticPage;
import com.centralbrain.sdk.diagnostics.DiagnosticQuery;
import com.centralbrain.sdk.diagnostics.DiagnosticRecord;
import com.centralbrain.sdk.diagnostics.ICentralBrainDiagnostics;
import com.centralbrain.sdk.production.ICentralBrainRuntime;
import com.centralbrain.runtime.acceptance.RuntimeAcceptanceSnapshot;
import com.centralbrain.runtime.effects.EffectDeliveryActivationSnapshot;
import com.centralbrain.runtime.events.EventRuntimeReadinessSnapshot;
import com.centralbrain.runtime.governance.SkillGovernanceReadinessSnapshot;
import com.centralbrain.runtime.identity.AndroidCallerIdentityResolver;
import com.centralbrain.runtime.identity.CallerIdentitySnapshot;
import com.centralbrain.runtime.memory.MemoryRuntimeReadinessSnapshot;
import com.centralbrain.runtime.model.ModelRuntimeReadinessSnapshot;
import com.centralbrain.runtime.nativebridge.NativeRuntimeProcessSnapshot;
import com.centralbrain.runtime.policy.AndroidCapabilityPolicyLoader;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy.Capability;

/** Read-only diagnostic Binder with R3B trusted capability enforcement. */
public final class CentralBrainDiagnosticService extends Service {
    public static final String ACCESS_PERMISSION =
            "com.centralbrain.permission.ACCESS_DIAGNOSTICS";

    private static final String TAG = "CentralBrainDiagnostic";
    private final EffectDeliveryActivationSnapshot effectDeliveryActivation =
            EffectDeliveryActivationSnapshot.current();
    private final ModelRuntimeReadinessSnapshot modelRuntimeReadiness =
            ModelRuntimeReadinessSnapshot.current();
    private final EventRuntimeReadinessSnapshot eventRuntimeReadiness =
            EventRuntimeReadinessSnapshot.current();
    private final MemoryRuntimeReadinessSnapshot memoryRuntimeReadiness =
            MemoryRuntimeReadinessSnapshot.current();
    private final SkillGovernanceReadinessSnapshot skillGovernanceReadiness =
            SkillGovernanceReadinessSnapshot.current();
    private final RuntimeAcceptanceSnapshot runtimeAcceptance =
            RuntimeAcceptanceSnapshot.current();

    private final ICentralBrainDiagnostics.Stub binder = new ICentralBrainDiagnostics.Stub() {
        @Override
        public int getProtocolVersion() {
            resolveAuthorizedCaller();
            return ICentralBrainDiagnostics.INTERFACE_VERSION;
        }

        @Override
        public String getProtocolHash() {
            resolveAuthorizedCaller();
            return ICentralBrainDiagnostics.INTERFACE_HASH;
        }

        @Override
        public DiagnosticPage getPage(DiagnosticQuery query) {
            resolveAuthorizedCaller();
            if (query == null || query.schemaVersion != 1) {
                throw new IllegalArgumentException("DiagnosticQuery schemaVersion=1 is required");
            }

            int pageSize = Math.max(1, Math.min(
                    query.pageSize,
                    ICentralBrainDiagnostics.MAX_PAGE_SIZE));
            DiagnosticRecord[] records = records();
            int start = parseCursor(query.cursor, records.length);
            int end = Math.min(records.length, start + pageSize);
            DiagnosticRecord[] pageRecords = new DiagnosticRecord[end - start];
            System.arraycopy(records, start, pageRecords, 0, pageRecords.length);

            DiagnosticPage page = new DiagnosticPage();
            page.records = pageRecords;
            page.hasMore = end < records.length;
            page.nextCursor = page.hasMore ? Integer.toString(end) : "";
            page.generatedAtElapsedRealtimeMs = SystemClock.elapsedRealtime();
            return page;
        }
    };

    private AndroidCallerIdentityResolver identityResolver;
    private CallerCapabilityPolicy capabilityPolicy;

    @Override
    public void onCreate() {
        super.onCreate();
        identityResolver = new AndroidCallerIdentityResolver(this);
        capabilityPolicy = AndroidCapabilityPolicyLoader.load(
                this,
                R.xml.central_brain_capability_policy,
                identityResolver.resolveOwnIdentity());
        Log.i(TAG, "created capability_default=deny"
                + " capability_rule_count=" + capabilityPolicy.getRuleCount()
                + " effect_delivery_activation_diagnostic_wired=true"
                + " production_effect_delivery_activation_allowed="
                + effectDeliveryActivation.isActivationAllowed()
                + " production_effect_material_source="
                + effectDeliveryActivation.getMaterialSourceId()
                + " model_runtime_readiness_diagnostic_wired=true"
                + " production_inference_allowed="
                + modelRuntimeReadiness.isProductionInferenceAllowed()
                + " vendor_npu_provider_available="
                + modelRuntimeReadiness.isVendorNpuProviderAvailable()
                + " event_runtime_readiness_diagnostic_wired=true"
                + " event_runtime_activation_allowed="
                + eventRuntimeReadiness.isActivationAllowed()
                + " durable_event_source_available="
                + eventRuntimeReadiness.isDurableEventSourceAvailable()
                + " memory_runtime_readiness_diagnostic_wired=true"
                + " memory_runtime_activation_allowed="
                + memoryRuntimeReadiness.isActivationAllowed()
                + " durable_encrypted_memory_storage_available="
                + memoryRuntimeReadiness.isDurableEncryptedStorageAvailable()
                + " hardware_accessed=false");
        Log.i(TAG, "native_runtime_diagnostic_wired=true "
                + nativeRuntimeSnapshot().logFields());
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "diagnostic binder requested hardware_accessed=false");
        return binder;
    }

    private CallerIdentitySnapshot resolveAuthorizedCaller() {
        CallerIdentitySnapshot caller = identityResolver.resolveCallingIdentity();
        CallerCapabilityPolicy.Decision decision = capabilityPolicy.evaluate(
                caller,
                Capability.DIAGNOSTICS_READ);
        if (!decision.isAllowed()) {
            Log.w(TAG, "capability denied capability=" + Capability.DIAGNOSTICS_READ.getId()
                    + " reason=" + decision.getReason()
                    + " matchedPackage=" + decision.getMatchedPackage()
                    + " " + caller.auditSummary()
                    + " hardware_accessed=false");
            throw new SecurityException("Central Brain capability denied: "
                    + Capability.DIAGNOSTICS_READ.getId());
        }
        return caller;
    }

    private static int parseCursor(String cursor, int recordCount) {
        if (cursor == null || cursor.isEmpty()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(cursor);
            if (value < 0 || value > recordCount) {
                throw new NumberFormatException("cursor out of range");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid diagnostic cursor");
        }
    }

    private DiagnosticRecord[] records() {
        NativeRuntimeProcessSnapshot nativeRuntime = nativeRuntimeSnapshot();
        return new DiagnosticRecord[] {
                record("protocol", "production", "version=1", ICentralBrainRuntime.INTERFACE_HASH, 1),
                record("protocol", "diagnostic", "version=1", ICentralBrainDiagnostics.INTERFACE_HASH, 2),
                record(
                        "runtime",
                        "maturity",
                        CentralBrainSdk.MATURITY,
                        "hardware_accessed=false;driver_development_triggered=false",
                        3),
                record(
                        "runtime",
                        "effect-delivery-activation",
                        "blocked",
                        effectDeliveryActivation.diagnosticDetail(),
                        4),
                record(
                        "runtime",
                        "model-runtime-readiness",
                        "blocked",
                        modelRuntimeReadiness.diagnosticDetail(),
                        5),
                record(
                        "runtime",
                        "event-runtime-readiness",
                        "blocked",
                        eventRuntimeReadiness.diagnosticDetail(),
                        6),
                record(
                        "runtime",
                        "memory-runtime-readiness",
                        "blocked",
                        memoryRuntimeReadiness.diagnosticDetail(),
                        7),
                record(
                        "runtime",
                        "skill-governance-readiness",
                        "blocked",
                        skillGovernanceReadiness.diagnosticDetail(),
                        8),
                record(
                        "runtime",
                        "runtime-acceptance",
                        "core-ready-production-blocked",
                        runtimeAcceptance.diagnosticDetail(),
                        9),
                record(
                        "runtime",
                        "native-runtime-readiness",
                        nativeRuntime.isRuntimeReady()
                                ? "ready-provider-empty"
                                : "unavailable",
                        nativeRuntime.diagnosticDetail(),
                        10)
        };
    }

    private NativeRuntimeProcessSnapshot nativeRuntimeSnapshot() {
        return ((CentralBrainRuntimeApplication) getApplication())
                .getNativeRuntimeSnapshot();
    }

    private static DiagnosticRecord record(
            String type,
            String id,
            String summary,
            String detail,
            long sequence) {
        DiagnosticRecord record = new DiagnosticRecord();
        record.recordType = type;
        record.recordId = id;
        record.summary = summary;
        record.detail = detail;
        record.sequence = sequence;
        record.observedAtElapsedRealtimeMs = SystemClock.elapsedRealtime();
        return record;
    }
}
