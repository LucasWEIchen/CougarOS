package com.centralbrain.runtime;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import com.centralbrain.runtime.governance.ActionGovernancePolicy;
import com.centralbrain.runtime.governance.ActionGovernancePolicy.Decision;
import com.centralbrain.runtime.governance.ActionGovernancePolicy.Outcome;
import com.centralbrain.runtime.governance.ActionGovernancePolicy.RiskClass;
import com.centralbrain.runtime.governance.RuntimeOwnedSafetyVehicleStateProvider;
import com.centralbrain.runtime.governance.SafetyVehicleStateProvider;
import com.centralbrain.runtime.governance.SafetyVehicleStateSnapshot;
import com.centralbrain.runtime.identity.AndroidCallerIdentityResolver;
import com.centralbrain.runtime.identity.CallerIdentitySnapshot;
import com.centralbrain.runtime.identity.DurablePrincipalFingerprint;
import com.centralbrain.runtime.persistence.CentralBrainDatabase;
import com.centralbrain.runtime.persistence.DurableApprovalRepository;
import com.centralbrain.runtime.persistence.DurableApprovalRepository.Snapshot;
import com.centralbrain.runtime.policy.AndroidCapabilityPolicyLoader;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy.Capability;
import com.centralbrain.sdk.governance.ActionDecision;
import com.centralbrain.sdk.governance.ActionRequest;
import com.centralbrain.sdk.governance.ApprovalHandle;
import com.centralbrain.sdk.governance.ApprovalStatus;
import com.centralbrain.sdk.governance.ICentralBrainGovernance;

import java.util.concurrent.TimeUnit;

/** Typed Governance surface with R4B3 durable approval and no action dispatch/grant path. */
public final class CentralBrainGovernanceService extends Service {
    public static final String BIND_PERMISSION =
            "com.centralbrain.permission.BIND_GOVERNANCE";

    private static final String TAG = "CentralBrainGovernance";
    private static final int MAX_TEXT_LENGTH = 256;
    private static final int MAX_PENDING_APPROVALS = 64;
    private static final long APPROVAL_TTL_MS = TimeUnit.MINUTES.toMillis(2);

    private AndroidCallerIdentityResolver identityResolver;
    private CallerCapabilityPolicy capabilityPolicy;
    private SafetyVehicleStateProvider stateProvider;
    private ActionGovernancePolicy actionPolicy;
    private CentralBrainDatabase database;
    private DurableApprovalRepository approvalRepository;

    private final ICentralBrainGovernance.Stub binder = new ICentralBrainGovernance.Stub() {
        @Override
        public int getProtocolVersion() {
            resolveAuthorizedCaller(Capability.GOVERNANCE_PROTOCOL_READ);
            return ICentralBrainGovernance.INTERFACE_VERSION;
        }

        @Override
        public String getProtocolHash() {
            resolveAuthorizedCaller(Capability.GOVERNANCE_PROTOCOL_READ);
            return ICentralBrainGovernance.INTERFACE_HASH;
        }

        @Override
        public ActionDecision evaluateAction(ActionRequest request) {
            CallerIdentitySnapshot caller = resolveAuthorizedCaller(Capability.ACTION_EVALUATE);
            validateActionRequest(request);
            SafetyVehicleStateSnapshot state = stateProvider.currentSnapshot();
            Decision decision = actionPolicy.evaluate(request.actionId, state);
            Log.i(TAG, "evaluated actionId=" + request.actionId
                    + " risk=" + decision.getRiskClass()
                    + " outcome=" + decision.getOutcome()
                    + " reason=" + decision.getReason()
                    + " " + caller.auditSummary()
                    + " dispatch_allowed=false hardware_accessed=false");
            return toActionDecision(request, decision);
        }

        @Override
        public ApprovalHandle requestApproval(ActionRequest request) {
            CallerIdentitySnapshot caller = resolveAuthorizedCaller(Capability.APPROVAL_REQUEST);
            validateActionRequest(request);
            Decision decision = actionPolicy.evaluate(
                    request.actionId,
                    stateProvider.currentSnapshot());
            DurableApprovalRepository.RequestResult result;
            try {
                result = approvalRepository.request(
                        DurablePrincipalFingerprint.from(caller),
                        request.idempotencyKey,
                        request.actionId,
                        decision.getRiskClass().name(),
                        decision.getReason().name(),
                        decision.getOutcome() == Outcome.APPROVAL_REQUIRED
                                && ActionGovernancePolicy.isHighRisk(
                                        decision.getRiskClass()));
            } catch (DurableApprovalRepository.ApprovalRejectedException exception) {
                throw new IllegalArgumentException(exception.getMessage());
            }
            Snapshot pending = result.getSnapshot();
            Log.i(TAG, "approval request approvalId=" + pending.getApprovalId()
                    + " actionId=" + pending.getActionId()
                    + " request_outcome=" + result.getOutcome()
                    + " " + caller.auditSummary()
                    + " grant_supported=false durable=true"
                    + " dispatch_allowed=false hardware_accessed=false");
            return toApprovalHandle(pending);
        }

        @Override
        public ApprovalStatus getApprovalStatus(ApprovalHandle handle) {
            CallerIdentitySnapshot caller = resolveAuthorizedCaller(
                    Capability.APPROVAL_STATUS_OWN);
            String approvalId = validateApprovalHandle(handle);
            Snapshot snapshot = approvalRepository.findOwned(
                    approvalId,
                    DurablePrincipalFingerprint.from(caller));
            return snapshot == null ? unknownApprovalStatus() : toApprovalStatus(snapshot);
        }

        @Override
        public boolean cancelApproval(ApprovalHandle handle) {
            CallerIdentitySnapshot caller = resolveAuthorizedCaller(
                    Capability.APPROVAL_CANCEL_OWN);
            String approvalId = validateApprovalHandle(handle);
            DurableApprovalRepository.CancelOutcome outcome = approvalRepository.cancelOwned(
                    approvalId,
                    DurablePrincipalFingerprint.from(caller));
            boolean cancelled = outcome == DurableApprovalRepository.CancelOutcome.APPLIED
                    || outcome == DurableApprovalRepository.CancelOutcome.REPLAYED;
            Log.i(TAG, "approval cancel approvalId=" + approvalId
                    + " result=" + cancelled
                    + " outcome=" + outcome
                    + " " + caller.auditSummary()
                    + " dispatch_allowed=false hardware_accessed=false");
            return cancelled;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        identityResolver = new AndroidCallerIdentityResolver(this);
        capabilityPolicy = AndroidCapabilityPolicyLoader.load(
                this,
                R.xml.central_brain_capability_policy,
                identityResolver.resolveOwnIdentity());
        stateProvider = new RuntimeOwnedSafetyVehicleStateProvider(SystemClock::elapsedRealtime);
        actionPolicy = new ActionGovernancePolicy();
        database = CentralBrainDatabase.open(this);
        approvalRepository = DurableApprovalRepository.create(
                database,
                MAX_PENDING_APPROVALS,
                APPROVAL_TTL_MS);
        SafetyVehicleStateSnapshot state = stateProvider.currentSnapshot();
        Log.i(TAG, "created capability_default=deny"
                + " capability_rule_count=" + capabilityPolicy.getRuleCount()
                + " state_source=" + state.getSourceId()
                + " source_hardware_backed=" + state.isHardwareBacked()
                + " source_production_trusted=" + state.isProductionTrusted()
                + " approval_max_pending_records=" + MAX_PENDING_APPROVALS
                + " approval_ttl_ms=" + APPROVAL_TTL_MS
                + " approval_grant_supported=" + approvalRepository.supportsApprovalGrant()
                + " approval_durable=" + approvalRepository.isDurable()
                + " dispatch_allowed=false hardware_accessed=false");
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "governance binder requested hardware_accessed=false");
        return binder;
    }

    @Override
    public void onDestroy() {
        if (database != null) {
            database.close();
        }
        super.onDestroy();
    }

    private CallerIdentitySnapshot resolveAuthorizedCaller(Capability capability) {
        CallerIdentitySnapshot caller = identityResolver.resolveCallingIdentity();
        CallerCapabilityPolicy.Decision decision = capabilityPolicy.evaluate(caller, capability);
        if (!decision.isAllowed()) {
            Log.w(TAG, "capability denied capability=" + capability.getId()
                    + " reason=" + decision.getReason()
                    + " matchedPackage=" + decision.getMatchedPackage()
                    + " " + caller.auditSummary()
                    + " hardware_accessed=false");
            throw new SecurityException("Central Brain capability denied: " + capability.getId());
        }
        return caller;
    }

    private static void validateActionRequest(ActionRequest request) {
        if (request == null || request.schemaVersion != 1) {
            throw new IllegalArgumentException("ActionRequest schemaVersion=1 is required");
        }
        if (isBlank(request.clientRequestId)
                || isBlank(request.actionId)
                || isBlank(request.idempotencyKey)) {
            throw new IllegalArgumentException(
                    "clientRequestId, actionId and idempotencyKey are required");
        }
        if (tooLong(request.clientRequestId)
                || tooLong(request.actionId)
                || tooLong(request.idempotencyKey)) {
            throw new IllegalArgumentException("ActionRequest text exceeds limit");
        }
    }

    private static String validateApprovalHandle(ApprovalHandle handle) {
        if (handle == null
                || handle.schemaVersion != 1
                || isBlank(handle.approvalId)
                || tooLong(handle.approvalId)) {
            throw new IllegalArgumentException("valid ApprovalHandle is required");
        }
        return handle.approvalId;
    }

    private static ActionDecision toActionDecision(ActionRequest request, Decision decision) {
        SafetyVehicleStateSnapshot state = decision.getState();
        ActionDecision result = new ActionDecision();
        result.schemaVersion = 1;
        result.clientRequestId = request.clientRequestId;
        result.actionId = request.actionId;
        result.riskClass = riskClass(decision.getRiskClass());
        result.outcome = outcome(decision.getOutcome());
        result.reasonCode = decision.getReason().name();
        if (state != null) {
            result.stateSourceId = state.getSourceId();
            result.stateRevision = state.getRevision();
            result.safetyState = safetyState(state.getSafetyState());
            result.motionState = motionState(state.getMotionState());
            result.driverAvailable = state.isDriverAvailable();
            result.sourceHardwareBacked = state.isHardwareBacked();
            result.sourceProductionTrusted = state.isProductionTrusted();
        }
        result.dispatchAllowed = false;
        return result;
    }

    private static ApprovalHandle toApprovalHandle(Snapshot snapshot) {
        ApprovalHandle handle = new ApprovalHandle();
        handle.schemaVersion = 1;
        handle.approvalId = snapshot.getApprovalId();
        handle.actionId = snapshot.getActionId();
        handle.status = approvalStatus(snapshot.getState());
        handle.createdAtElapsedRealtimeMs = wallToElapsed(snapshot.getCreatedAtWallMs());
        handle.expiresAtElapsedRealtimeMs = wallToElapsed(snapshot.getExpiresAtWallMs());
        return handle;
    }

    private static ApprovalStatus toApprovalStatus(Snapshot snapshot) {
        ApprovalStatus status = new ApprovalStatus();
        status.schemaVersion = 1;
        status.approvalId = snapshot.getApprovalId();
        status.actionId = snapshot.getActionId();
        status.status = approvalStatus(snapshot.getState());
        status.riskClass = riskClass(snapshot.getRiskClass());
        status.reasonCode = snapshot.getReasonCode();
        status.createdAtElapsedRealtimeMs = wallToElapsed(snapshot.getCreatedAtWallMs());
        status.expiresAtElapsedRealtimeMs = wallToElapsed(snapshot.getExpiresAtWallMs());
        status.grantSupported = false;
        status.durable = true;
        status.dispatchAllowed = false;
        return status;
    }

    private static ApprovalStatus unknownApprovalStatus() {
        ApprovalStatus status = new ApprovalStatus();
        status.schemaVersion = 1;
        status.status = ICentralBrainGovernance.APPROVAL_STATUS_UNKNOWN;
        status.grantSupported = false;
        status.durable = true;
        status.dispatchAllowed = false;
        return status;
    }

    private static int riskClass(RiskClass riskClass) {
        switch (riskClass) {
            case READ_ONLY:
                return ICentralBrainGovernance.RISK_READ_ONLY;
            case COMFORT_CONTROL:
                return ICentralBrainGovernance.RISK_COMFORT_CONTROL;
            case DRIVER_DISTRACTION:
                return ICentralBrainGovernance.RISK_DRIVER_DISTRACTION;
            case DIAGNOSTIC_WRITE:
                return ICentralBrainGovernance.RISK_DIAGNOSTIC_WRITE;
            case OTA:
                return ICentralBrainGovernance.RISK_OTA;
            case UNKNOWN:
            default:
                return ICentralBrainGovernance.RISK_UNKNOWN;
        }
    }

    private static int outcome(Outcome outcome) {
        switch (outcome) {
            case ALLOW_POLICY_ONLY:
                return ICentralBrainGovernance.DECISION_ALLOW_POLICY_ONLY;
            case APPROVAL_REQUIRED:
                return ICentralBrainGovernance.DECISION_APPROVAL_REQUIRED;
            case DENY:
            default:
                return ICentralBrainGovernance.DECISION_DENY;
        }
    }

    private static int safetyState(SafetyVehicleStateSnapshot.SafetyState safetyState) {
        switch (safetyState) {
            case NORMAL:
                return ICentralBrainGovernance.SAFETY_NORMAL;
            case DEGRADED:
                return ICentralBrainGovernance.SAFETY_DEGRADED;
            case EMERGENCY:
                return ICentralBrainGovernance.SAFETY_EMERGENCY;
            case UNKNOWN:
            default:
                return ICentralBrainGovernance.SAFETY_UNKNOWN;
        }
    }

    private static int motionState(SafetyVehicleStateSnapshot.MotionState motionState) {
        switch (motionState) {
            case PARKED:
                return ICentralBrainGovernance.MOTION_PARKED;
            case MOVING:
                return ICentralBrainGovernance.MOTION_MOVING;
            case UNKNOWN:
            default:
                return ICentralBrainGovernance.MOTION_UNKNOWN;
        }
    }

    private static int approvalStatus(String status) {
        if (DurableApprovalRepository.STATE_PENDING.equals(status)) {
            return ICentralBrainGovernance.APPROVAL_STATUS_PENDING;
        }
        if (DurableApprovalRepository.STATE_CANCELLED.equals(status)) {
            return ICentralBrainGovernance.APPROVAL_STATUS_CANCELLED;
        }
        if (DurableApprovalRepository.STATE_EXPIRED.equals(status)) {
            return ICentralBrainGovernance.APPROVAL_STATUS_EXPIRED;
        }
        return ICentralBrainGovernance.APPROVAL_STATUS_UNKNOWN;
    }

    private static int riskClass(String riskClass) {
        try {
            return riskClass(RiskClass.valueOf(riskClass));
        } catch (IllegalArgumentException exception) {
            return ICentralBrainGovernance.RISK_UNKNOWN;
        }
    }

    private static long wallToElapsed(long wallTimeMs) {
        long wallNow = System.currentTimeMillis();
        long elapsedNow = SystemClock.elapsedRealtime();
        if (wallTimeMs >= wallNow) {
            long delta = wallTimeMs - wallNow;
            return delta > Long.MAX_VALUE - elapsedNow ? Long.MAX_VALUE : elapsedNow + delta;
        }
        return Math.max(0, elapsedNow - (wallNow - wallTimeMs));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static boolean tooLong(String value) {
        return value != null && value.length() > MAX_TEXT_LENGTH;
    }
}
