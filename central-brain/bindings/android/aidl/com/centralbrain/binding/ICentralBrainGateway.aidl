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
 * - FW-U-008 Uni Info Bus extension mechanism
 * - NV-P-002 IPC/Binder binding
 * - NV-P-006 DDS/high-rate topic reservation
 * - HW-002, KH-001/KH-002/KH-003/KH-006/KH-007 hardware empty-interface backlog visibility
 * - DEL-001 Android main delivery path
 * - DEL-002/003/004/005 Android/Linux delivery readiness visibility
 */
interface ICentralBrainGateway {
    String getContextJson(String traceId);

    String getStateJson(String traceId);

    String listEventTopicsJson(String traceId);

    String publishEventJson(String traceId, String requestJson);

    String getRecentEventsJson(String traceId, int limit);

    String getEventSubscriptionsJson(String traceId);

    String requestEventSubscriptionJson(String traceId, String requestJson);

    String cancelEventSubscriptionJson(String traceId, String requestJson);

    String getEventSubscriptionTransportReadinessJson(String traceId);

    String getEventSubscriptionDecisionMatrixJson(String traceId);

    String getEventSubscriptionActivationChecklistJson(String traceId);

    String getEventSubscriptionCallbackWatchShapeJson(String traceId);

    String getEventSubscriptionCursorReplayStorageJson(String traceId);

    String getEventSubscriptionBackpressureQosEvidenceJson(String traceId);

    String getEventSubscriptionReadinessRollupJson(String traceId);

    String submitEventSubscriptionActivationEvidenceJson(String traceId, String requestJson);

    String getEventSubscriptionActivationEvidenceStatusJson(String traceId);

    String getEventSubscriptionActivationEvidenceRetentionChecklistJson(String traceId);

    String getUibExtensionsJson(String traceId);

    String getAiSdkCapabilitiesJson(String traceId);

    String planAgentTaskJson(String traceId, String requestJson);

    String executeAgentTaskJson(String traceId, String requestJson);

    String listSkillsJson(String traceId);

    String invokeSkillJson(String traceId, String skillId, String requestJson);

    String queryMemoryJson(String traceId, String requestJson);

    String requestActionJson(String traceId, String requestJson);

    String listServicesJson(String traceId);

    String getServiceContractsJson(String traceId);

    String invokeServiceJson(String traceId, String requestJson);

    String evaluatePolicyJson(String traceId, String requestJson);

    String precheckGovernanceJson(String traceId, String requestJson);

    String getGovernanceBackendContractJson(String traceId);

    String getGovernanceMigrationCheckJson(String traceId);

    String getGovernanceDeploymentPlanJson(String traceId);

    String getRuntimeGovernanceJson(String traceId);

    String getRecentAuditJson(String traceId, int limit);

    String listBindingsJson(String traceId);

    String getBindingDetailJson(String traceId);

    String getBindingReadinessJson(String traceId);

    String getDeliveryReadinessJson(String traceId);

    String getPrototypeReadinessJson(String traceId);

    String getNativeAdaptersDetailJson(String traceId);

    String getDriverHalGapsJson(String traceId);

    String getHardwareInterfacesJson(String traceId);

    String getHardwareInterfaceActivationChecklistJson(String traceId);

    String getHardwareInterfaceOwnerDecisionStatusJson(String traceId);

    String submitHardwareInterfaceOwnerDecisionEvidenceJson(String traceId, String requestJson);

    String getHardwareInterfaceOwnerDecisionEvidenceStatusJson(String traceId);

    String getHardwareInterfaceOwnerDecisionEvidenceRetentionChecklistJson(String traceId);

    String getVehicleSignalsJson(String traceId);

    String getVehicleSignalActivationJson(String traceId);

    String getVehicleSignalValidationJson(String traceId);
}
