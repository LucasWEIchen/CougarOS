package com.centralbrain.binding;

/**
 * Android Binder/AIDL skeleton for the Central Brain semantic gateway.
 *
 * Req IDs:
 * - XSC-002 Uni Info Bus semantic interface
 * - XSC-003 SOA service entry
 * - XSC-001 AI SDK application facade
 * - XSC-005 Runtime & Governance
 * - XSC-006 Protocol Binding
 * - XSC-004 AIOS Kernel / Native adapter visibility
 * - FW-U-003 Uni Info Bus Event
 * - FW-U-004 Uni Info Bus Action
 * - FW-U-006 Tool / Skill contract
 * - NV-P-002 IPC/Binder binding
 * - NV-P-006 DDS/high-rate topic reservation
 * - DEL-001 Android main delivery path
 */
interface ICentralBrainGateway {
    String getContextJson(String traceId);

    String getStateJson(String traceId);

    String listEventTopicsJson(String traceId);

    String publishEventJson(String traceId, String requestJson);

    String getRecentEventsJson(String traceId, int limit);

    String getAiSdkCapabilitiesJson(String traceId);

    String planAgentTaskJson(String traceId, String requestJson);

    String executeAgentTaskJson(String traceId, String requestJson);

    String listSkillsJson(String traceId);

    String invokeSkillJson(String traceId, String skillId, String requestJson);

    String queryMemoryJson(String traceId, String requestJson);

    String requestActionJson(String traceId, String requestJson);

    String listServicesJson(String traceId);

    String invokeServiceJson(String traceId, String requestJson);

    String evaluatePolicyJson(String traceId, String requestJson);

    String getRuntimeGovernanceJson(String traceId);

    String getRecentAuditJson(String traceId, int limit);

    String listBindingsJson(String traceId);

    String getBindingDetailJson(String traceId);

    String getNativeAdaptersDetailJson(String traceId);
}
