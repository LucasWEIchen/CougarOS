package com.centralbrain.binding;

/**
 * Android Binder/AIDL skeleton for the Central Brain semantic gateway.
 *
 * Req IDs:
 * - XSC-002 Uni Info Bus semantic interface
 * - XSC-003 SOA service entry
 * - XSC-005 Runtime & Governance
 * - XSC-006 Protocol Binding
 * - NV-P-002 IPC/Binder binding
 * - DEL-001 Android main delivery path
 */
interface ICentralBrainGateway {
    String getContextJson(String traceId);

    String getStateJson(String traceId);

    String listServicesJson(String traceId);

    String invokeServiceJson(String traceId, String requestJson);

    String evaluatePolicyJson(String traceId, String requestJson);

    String getRuntimeGovernanceJson(String traceId);

    String getRecentAuditJson(String traceId, int limit);

    String listBindingsJson(String traceId);
}
