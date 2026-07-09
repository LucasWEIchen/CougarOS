package com.centralbrain.binding;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Android Binder service stub for the Central Brain semantic gateway.
 *
 * Req IDs: XSC-001, XSC-002, XSC-003, XSC-004, XSC-005, XSC-006, APP-004,
 * FW-U-003, FW-U-004, FW-U-006, FW-U-008, NV-P-002, NV-P-006, HW-002,
 * KH-001, KH-002, KH-003, KH-006, KH-007, DEL-001, DEL-002, DEL-003,
 * DEL-004, DEL-005.
 *
 * This sample keeps REST as the upstream prototype binding. Production AAOS
 * integration should host this in a system/privileged service and replace the
 * upstream bridge with the in-process gateway or target platform service.
 */
public final class CentralBrainGatewayBinderService extends Service {
    public static final String ACTION_BIND =
            "com.centralbrain.binding.action.BIND_CENTRAL_BRAIN_GATEWAY";
    public static final String EXTRA_BASE_URL = "com.centralbrain.binding.extra.BASE_URL";
    public static final String DEFAULT_BASE_URL = "http://10.0.2.2:8787";

    private volatile String baseUrl = DEFAULT_BASE_URL;

    private final ICentralBrainGateway.Stub binder = new ICentralBrainGateway.Stub() {
        @Override
        public String getContextJson(String traceId) throws RemoteException {
            return get("/uib/context", traceId);
        }

        @Override
        public String getStateJson(String traceId) throws RemoteException {
            return get("/uib/state", traceId);
        }

        @Override
        public String listEventTopicsJson(String traceId) throws RemoteException {
            return get("/uib/events/topics", traceId);
        }

        @Override
        public String publishEventJson(String traceId, String requestJson) throws RemoteException {
            return post("/uib/events/publish", withTraceId(traceId, requestJson));
        }

        @Override
        public String getRecentEventsJson(String traceId, int limit) throws RemoteException {
            return get("/uib/events/recent?limit=" + Math.max(1, limit), traceId);
        }

        @Override
        public String getEventSubscriptionsJson(String traceId) throws RemoteException {
            return get("/uib/events/subscriptions", traceId);
        }

        @Override
        public String requestEventSubscriptionJson(String traceId, String requestJson) throws RemoteException {
            return post("/uib/events/subscriptions/request", withTraceId(traceId, requestJson));
        }

        @Override
        public String cancelEventSubscriptionJson(String traceId, String requestJson) throws RemoteException {
            return post("/uib/events/subscriptions/cancel", withTraceId(traceId, requestJson));
        }

        @Override
        public String getEventSubscriptionTransportReadinessJson(String traceId) throws RemoteException {
            return get("/uib/events/subscriptions/transport-readiness", traceId);
        }

        @Override
        public String getEventSubscriptionDecisionMatrixJson(String traceId) throws RemoteException {
            return get("/uib/events/subscriptions/decision-matrix", traceId);
        }

        @Override
        public String getEventSubscriptionActivationChecklistJson(String traceId) throws RemoteException {
            return get("/uib/events/subscriptions/activation-checklist", traceId);
        }

        @Override
        public String getEventSubscriptionCallbackWatchShapeJson(String traceId) throws RemoteException {
            return get("/uib/events/subscriptions/callback-watch-shape", traceId);
        }

        @Override
        public String getEventSubscriptionCursorReplayStorageJson(String traceId) throws RemoteException {
            return get("/uib/events/subscriptions/cursor-replay-storage", traceId);
        }

        @Override
        public String getEventSubscriptionBackpressureQosEvidenceJson(String traceId) throws RemoteException {
            return get("/uib/events/subscriptions/backpressure-qos-evidence", traceId);
        }

        @Override
        public String getEventSubscriptionReadinessRollupJson(String traceId) throws RemoteException {
            return get("/uib/events/subscriptions/readiness-rollup", traceId);
        }

        @Override
        public String submitEventSubscriptionActivationEvidenceJson(String traceId, String requestJson) throws RemoteException {
            return post("/uib/events/subscriptions/activation-evidence", withTraceId(traceId, requestJson));
        }

        @Override
        public String getEventSubscriptionActivationEvidenceStatusJson(String traceId) throws RemoteException {
            return get("/uib/events/subscriptions/activation-evidence/status", traceId);
        }

        @Override
        public String getEventSubscriptionActivationEvidenceRetentionChecklistJson(String traceId) throws RemoteException {
            return get("/uib/events/subscriptions/activation-evidence/retention-checklist", traceId);
        }

        @Override
        public String getEventSubscriptionActivationEvidenceDecisionStatusRollupJson(String traceId) throws RemoteException {
            return get("/uib/events/subscriptions/activation-evidence/decision-status-rollup", traceId);
        }

        @Override
        public String getEventSubscriptionActivationApprovalDryRunStatusJson(String traceId) throws RemoteException {
            return get("/uib/events/subscriptions/activation-evidence/approval-dry-run/status", traceId);
        }

        @Override
        public String getEventSubscriptionActivationApprovalAuthorityChecklistJson(String traceId) throws RemoteException {
            return get("/uib/events/subscriptions/activation-evidence/approval-authority-checklist", traceId);
        }

        @Override
        public String getEventSubscriptionActivationApprovalAuthorityAuditConsistencyJson(String traceId) throws RemoteException {
            return get("/uib/events/subscriptions/activation-evidence/approval-authority-checklist/audit-consistency", traceId);
        }

        @Override
        public String getEventSubscriptionActivationApprovalDecisionBlockerRollupJson(String traceId) throws RemoteException {
            return get("/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-blocker-rollup", traceId);
        }

        @Override
        public String dryRunEventSubscriptionActivationApprovalDecisionJson(String traceId, String requestJson) throws RemoteException {
            return post("/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run", withTraceId(traceId, requestJson));
        }

        @Override
        public String getEventSubscriptionActivationApprovalDecisionDryRunStatusJson(String traceId) throws RemoteException {
            return get("/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/status", traceId);
        }

        @Override
        public String getEventSubscriptionActivationApprovalDecisionDryRunAuditConsistencyJson(String traceId) throws RemoteException {
            return get("/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/audit-consistency", traceId);
        }

        @Override
        public String getEventSubscriptionActivationApprovalDecisionClosureBlockerMatrixJson(String traceId) throws RemoteException {
            return get("/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix", traceId);
        }

        @Override
        public String getUibExtensionsJson(String traceId) throws RemoteException {
            return get("/uib/extensions", traceId);
        }

        @Override
        public String getAiSdkCapabilitiesJson(String traceId) throws RemoteException {
            return get("/ai/sdk/capabilities", traceId);
        }

        @Override
        public String planAgentTaskJson(String traceId, String requestJson) throws RemoteException {
            return post("/agent/plan", withTraceId(traceId, requestJson));
        }

        @Override
        public String executeAgentTaskJson(String traceId, String requestJson) throws RemoteException {
            return post("/agent/execute", withTraceId(traceId, requestJson));
        }

        @Override
        public String listSkillsJson(String traceId) throws RemoteException {
            return get("/skills", traceId);
        }

        @Override
        public String invokeSkillJson(String traceId, String skillId, String requestJson) throws RemoteException {
            return post("/skills/" + urlEncode(skillId) + "/invoke", withTraceId(traceId, requestJson));
        }

        @Override
        public String queryMemoryJson(String traceId, String requestJson) throws RemoteException {
            return post("/memory/query", withTraceId(traceId, requestJson));
        }

        @Override
        public String requestActionJson(String traceId, String requestJson) throws RemoteException {
            return post("/uib/actions/request", withTraceId(traceId, requestJson));
        }

        @Override
        public String listServicesJson(String traceId) throws RemoteException {
            return get("/soa/services", traceId);
        }

        @Override
        public String getServiceContractsJson(String traceId) throws RemoteException {
            return get("/soa/contracts", traceId);
        }

        @Override
        public String invokeServiceJson(String traceId, String requestJson) throws RemoteException {
            return post("/soa/invoke", withTraceId(traceId, requestJson));
        }

        @Override
        public String evaluatePolicyJson(String traceId, String requestJson) throws RemoteException {
            return post("/policy/evaluate", withTraceId(traceId, requestJson));
        }

        @Override
        public String precheckGovernanceJson(String traceId, String requestJson) throws RemoteException {
            return post("/governance/precheck", withTraceId(traceId, requestJson));
        }

        @Override
        public String getGovernanceBackendContractJson(String traceId) throws RemoteException {
            return get("/governance/backend-contract", traceId);
        }

        @Override
        public String getGovernanceMigrationCheckJson(String traceId) throws RemoteException {
            return get("/governance/migration-check", traceId);
        }

        @Override
        public String getGovernanceDeploymentPlanJson(String traceId) throws RemoteException {
            return get("/governance/deployment-plan", traceId);
        }

        @Override
        public String getRuntimeGovernanceJson(String traceId) throws RemoteException {
            return get("/governance/runtime", traceId);
        }

        @Override
        public String getRecentAuditJson(String traceId, int limit) throws RemoteException {
            return get("/audit/recent?limit=" + Math.max(1, limit), traceId);
        }

        @Override
        public String listBindingsJson(String traceId) throws RemoteException {
            return get("/bindings", traceId);
        }

        @Override
        public String getBindingDetailJson(String traceId) throws RemoteException {
            return get("/bindings/detail", traceId);
        }

        @Override
        public String getBindingReadinessJson(String traceId) throws RemoteException {
            return get("/bindings/readiness", traceId);
        }

        @Override
        public String getDeliveryReadinessJson(String traceId) throws RemoteException {
            return get("/delivery/readiness", traceId);
        }

        @Override
        public String getPrototypeReadinessJson(String traceId) throws RemoteException {
            return get("/prototype/readiness", traceId);
        }

        @Override
        public String getNativeAdaptersDetailJson(String traceId) throws RemoteException {
            return get("/native/adapters/detail", traceId);
        }

        @Override
        public String getDriverHalGapsJson(String traceId) throws RemoteException {
            return get("/native/driver-gaps", traceId);
        }

        @Override
        public String getHardwareInterfacesJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces", traceId);
        }

        @Override
        public String getHardwareInterfaceActivationChecklistJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/activation-checklist", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionStatusJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-status", traceId);
        }

        @Override
        public String submitHardwareInterfaceOwnerDecisionEvidenceJson(String traceId, String requestJson) throws RemoteException {
            return post("/hardware/interfaces/owner-decision-evidence", withTraceId(traceId, requestJson));
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceStatusJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/status", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceRetentionChecklistJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/retention-checklist", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklistJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklistJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollupJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup", traceId);
        }

        @Override
        public String dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadJson(String traceId, String requestJson) throws RemoteException {
            return post("/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run", withTraceId(traceId, requestJson));
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatusJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistencyJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/audit-consistency", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklistJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatusJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/status", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistencyJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/audit-consistency", traceId);
        }

        @Override
        public String dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionJson(String traceId, String requestJson) throws RemoteException {
            return post("/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run", withTraceId(traceId, requestJson));
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatusJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/status", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistencyJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/audit-consistency", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionClosureBlockerMatrixJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionReviewerMatrixJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffChecklistJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceStatusJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceAuditConsistencyJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/audit-consistency", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceDecisionRollupJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessChecklistJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessAuditConsistencyJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionRollupJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentChecklistJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistencyJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup", traceId);
        }

        @Override
        public String getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummaryJson(String traceId) throws RemoteException {
            return get("/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary", traceId);
        }

        @Override
        public String getVehicleSignalsJson(String traceId) throws RemoteException {
            return get("/vehicle/signals", traceId);
        }

        @Override
        public String getVehicleSignalActivationJson(String traceId) throws RemoteException {
            return get("/vehicle/signals/activation", traceId);
        }

        @Override
        public String getVehicleSignalValidationJson(String traceId) throws RemoteException {
            return get("/vehicle/signals/validation", traceId);
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        updateBaseUrl(intent);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        updateBaseUrl(intent);
        return binder;
    }

    private void updateBaseUrl(Intent intent) {
        if (intent == null) {
            return;
        }
        String candidate = intent.getStringExtra(EXTRA_BASE_URL);
        if (candidate != null && candidate.startsWith("http")) {
            baseUrl = candidate.replaceAll("/+$", "");
        }
    }

    private String get(String path, String traceId) throws RemoteException {
        String suffix = traceId == null || traceId.isEmpty()
                ? path
                : path + (path.contains("?") ? "&" : "?") + "trace_id=" + urlEncode(traceId);
        return request("GET", suffix, null);
    }

    private String post(String path, String body) throws RemoteException {
        return request("POST", path, body == null || body.isEmpty() ? "{}" : body);
    }

    private String request(String method, String path, String body) throws RemoteException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(baseUrl + path).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(3000);
            connection.setRequestProperty("Accept", "application/json");
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                byte[] encoded = body.getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(encoded.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(encoded);
                }
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (stream == null) {
                throw new IOException("HTTP " + status + " without response body");
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append('\n');
                }
                return response.toString().trim();
            }
        } catch (IOException ex) {
            throw new RemoteException("Central Brain gateway request failed: " + ex.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String withTraceId(String traceId, String requestJson) {
        if (traceId == null || traceId.isEmpty() || requestJson == null || requestJson.trim().isEmpty()) {
            return requestJson;
        }
        String trimmed = requestJson.trim();
        if (!trimmed.startsWith("{") || trimmed.contains("\"trace_id\"")) {
            return requestJson;
        }
        if (trimmed.length() == 2) {
            return "{\"trace_id\":\"" + escapeJson(traceId) + "\"}";
        }
        return "{\"trace_id\":\"" + escapeJson(traceId) + "\"," + trimmed.substring(1);
    }

    private static String urlEncode(String value) throws RemoteException {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (IOException ex) {
            throw new RemoteException("Failed to encode trace id: " + ex.getMessage());
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
