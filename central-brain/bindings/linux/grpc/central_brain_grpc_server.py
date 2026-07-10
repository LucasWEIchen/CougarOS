#!/usr/bin/env python3
"""Linux gRPC/RPC contract sample for the Central Brain gateway.

Req IDs: XSC-001, XSC-002, XSC-003, XSC-005, XSC-006, APP-004, FW-U-003,
FW-U-004, FW-U-006, FW-U-008, NV-G-002, NV-G-004, NV-G-005, NV-G-006, NV-G-007,
NV-P-003, DEL-002.

The current environment does not provide grpcio, so this sample uses a tiny
TCP JSON request/response wrapper that mirrors the proto GatewayRequest and
GatewayResponse fields. It validates the RPC contract and governance boundary
without claiming to be a production gRPC transport.
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import signal
import socket
import sys
from typing import Any
from urllib import request
from urllib.error import HTTPError, URLError

BACKEND_DIR = Path(__file__).resolve().parents[3] / "backend"
IPC_DIR = Path(__file__).resolve().parents[1] / "ipc"
sys.path.insert(0, str(BACKEND_DIR))
sys.path.insert(0, str(IPC_DIR))

from central_brain_governance_client import get_audit_via_socket
from central_brain_governance_client import get_runtime_via_socket
from central_brain_governance_client import precheck_service_via_socket
from runtime_governance import RuntimeGovernance


DEFAULT_BASE_URL = os.environ.get("CENTRAL_BRAIN_BASE_URL", "http://127.0.0.1:8787")
DEFAULT_HOST = os.environ.get("CENTRAL_BRAIN_GRPC_HOST", "127.0.0.1")
DEFAULT_PORT = int(os.environ.get("CENTRAL_BRAIN_GRPC_PORT", "18788"))
DEFAULT_GOVERNANCE_SOCKET = os.environ.get("CENTRAL_BRAIN_GOVERNANCE_SOCKET")
DEFAULT_GRPC_AUDIT_LOG = os.environ.get("CENTRAL_BRAIN_GRPC_AUDIT_LOG")
GRPC_GOVERNANCE = RuntimeGovernance(DEFAULT_GRPC_AUDIT_LOG)

RPC_MAP: dict[str, dict[str, Any]] = {
    "GetContext": {"method": "GET", "path": "/uib/context", "req_ids": ["XSC-002", "FW-U-001", "NV-P-003", "DEL-002"]},
    "GetState": {"method": "GET", "path": "/uib/state", "req_ids": ["XSC-002", "FW-U-002", "NV-P-003", "DEL-002"]},
    "ListEventTopics": {"method": "GET", "path": "/uib/events/topics", "req_ids": ["XSC-002", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002"]},
    "PublishEvent": {"method": "POST", "path": "/uib/events/publish", "req_ids": ["XSC-002", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002"]},
    "GetRecentEvents": {"method": "GET", "path": "/uib/events/recent", "req_ids": ["XSC-002", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002"]},
    "GetEventSubscriptions": {"method": "GET", "path": "/uib/events/subscriptions", "req_ids": ["XSC-002", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002"]},
    "RequestEventSubscription": {"method": "POST", "path": "/uib/events/subscriptions/request", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002"]},
    "CancelEventSubscription": {"method": "POST", "path": "/uib/events/subscriptions/cancel", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002"]},
    "GetEventSubscriptionTransportReadiness": {"method": "GET", "path": "/uib/events/subscriptions/transport-readiness", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionDecisionMatrix": {"method": "GET", "path": "/uib/events/subscriptions/decision-matrix", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationChecklist": {"method": "GET", "path": "/uib/events/subscriptions/activation-checklist", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionCallbackWatchShape": {"method": "GET", "path": "/uib/events/subscriptions/callback-watch-shape", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionCursorReplayStorage": {"method": "GET", "path": "/uib/events/subscriptions/cursor-replay-storage", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionBackpressureQosEvidence": {"method": "GET", "path": "/uib/events/subscriptions/backpressure-qos-evidence", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionReadinessRollup": {"method": "GET", "path": "/uib/events/subscriptions/readiness-rollup", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "SubmitEventSubscriptionActivationEvidence": {"method": "POST", "path": "/uib/events/subscriptions/activation-evidence", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationEvidenceStatus": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/status", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationEvidenceRetentionChecklist": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/retention-checklist", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationEvidenceDecisionStatusRollup": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/decision-status-rollup", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDryRunStatus": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-dry-run/status", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalAuthorityChecklist": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalAuthorityAuditConsistency": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/audit-consistency", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionBlockerRollup": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-blocker-rollup", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "DryRunEventSubscriptionActivationApprovalDecision": {"method": "POST", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionDryRunStatus": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/status", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionDryRunAuditConsistency": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/audit-consistency", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionClosureBlockerMatrix": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffChecklist": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffAuditConsistency": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffDecisionRollup": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessMatrix": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceReadinessAuditConsistency": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceStatus": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceAuditConsistency": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceDecisionRollup": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessChecklist": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessAuditConsistency": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionRollup": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentChecklist": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistency": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollup": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummary": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditConsistency": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary/audit-consistency", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditDecisionRollup": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary/audit-consistency/decision-rollup", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetEventSubscriptionActivationApprovalDecisionOwnerHandoffEvidenceAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessAuditDecisionRollupClosureBlockerMatrix": {"method": "GET", "path": "/uib/events/subscriptions/activation-evidence/approval-authority-checklist/decision-dry-run/closure-blocker-matrix/owner-handoff-checklist/audit-consistency/decision-rollup/handoff-evidence-readiness-matrix/audit-consistency/acceptance-status/audit-consistency/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary/audit-consistency/decision-rollup/closure-blocker-matrix", "req_ids": ["XSC-002", "XSC-005", "FW-U-003", "NV-P-003", "NV-P-006", "DEL-002", "DEL-004"]},
    "GetUibExtensions": {"method": "GET", "path": "/uib/extensions", "req_ids": ["XSC-002", "FW-U-008", "XSC-005", "XSC-006", "NV-P-003", "DEL-002"]},
    "GetAiSdkCapabilities": {"method": "GET", "path": "/ai/sdk/capabilities", "req_ids": ["XSC-001", "APP-004", "NV-P-003", "DEL-002"]},
    "PlanAgentTask": {"method": "POST", "path": "/agent/plan", "req_ids": ["XSC-001", "APP-004", "NV-F-001", "FW-U-006", "NV-P-003", "DEL-002"]},
    "ExecuteAgentTask": {"method": "POST", "path": "/agent/execute", "req_ids": ["XSC-001", "APP-004", "NV-F-001", "FW-U-006", "NV-P-003", "DEL-002"]},
    "ListSkills": {"method": "GET", "path": "/skills", "req_ids": ["XSC-001", "FW-U-006", "NV-P-003", "DEL-002"]},
    "InvokeSkill": {"method": "POST", "path": "/skills/vehicle.state.query/invoke", "req_ids": ["XSC-001", "FW-U-006", "NV-G-005", "NV-P-003", "DEL-002"]},
    "QueryMemory": {"method": "POST", "path": "/memory/query", "req_ids": ["XSC-001", "NV-F-001", "FW-U-006", "NV-P-003", "DEL-002"]},
    "RequestAction": {"method": "POST", "path": "/uib/actions/request", "req_ids": ["XSC-002", "FW-U-004", "FW-U-007", "XSC-005", "NV-G-005", "NV-P-003", "DEL-002"]},
    "ListServices": {"method": "GET", "path": "/soa/services", "req_ids": ["XSC-003", "FW-S-004", "NV-P-003", "DEL-002"]},
    "GetServiceContracts": {"method": "GET", "path": "/soa/contracts", "req_ids": ["XSC-003", "FW-S-004", "NV-G-003", "NV-P-003", "DEL-002"]},
    "GetSoaExtensionClosureSummary": {"method": "GET", "path": "/soa/extensions/closure-summary", "req_ids": ["FW-S-006", "XSC-003", "XSC-005", "XSC-006", "NV-G-001", "NV-G-002", "NV-G-003", "DEL-001", "DEL-002", "DEL-003"]},
    "InvokeService": {"method": "POST", "path": "/soa/invoke", "req_ids": ["XSC-003", "FW-S-005", "NV-G-004", "NV-P-003", "DEL-002"]},
    "EvaluatePolicy": {"method": "POST", "path": "/policy/evaluate", "req_ids": ["XSC-005", "NV-G-005", "NV-P-003", "DEL-002"]},
    "PrecheckGovernance": {"method": "POST", "path": "/governance/precheck", "req_ids": ["XSC-005", "NV-G-002", "NV-G-004", "NV-G-005", "NV-G-006", "NV-G-007", "NV-P-003", "DEL-002"]},
    "GetGovernanceBackendContract": {"method": "GET", "path": "/governance/backend-contract", "req_ids": ["XSC-005", "XSC-006", "NV-G-001", "NV-G-002", "NV-G-004", "NV-G-007", "NV-P-003", "DEL-002"]},
    "GetGovernanceMigrationCheck": {"method": "GET", "path": "/governance/migration-check", "req_ids": ["XSC-005", "XSC-006", "NV-G-002", "NV-G-004", "NV-G-005", "NV-G-007", "NV-P-003", "DEL-002", "DEL-003", "DEL-004"]},
    "GetGovernanceDeploymentPlan": {"method": "GET", "path": "/governance/deployment-plan", "req_ids": ["XSC-005", "XSC-006", "NV-G-001", "NV-G-002", "NV-G-004", "NV-G-007", "NV-P-003", "DEL-001", "DEL-002", "DEL-003", "DEL-004"]},
    "GetRuntimeGovernance": {"method": "GET", "path": "/governance/runtime", "req_ids": ["XSC-005", "NV-G-001", "NV-P-003", "DEL-002"]},
    "GetRecentAudit": {"method": "GET", "path": "/audit/recent", "req_ids": ["XSC-005", "NV-G-007", "NV-P-003", "DEL-002"]},
    "GetObservabilityReadiness": {"method": "GET", "path": "/observability/readiness", "req_ids": ["NV-F-012", "XSC-005", "XSC-006", "NV-G-007", "NV-P-002", "NV-P-003", "DEL-001", "DEL-002", "DEL-003", "DEL-004"]},
    "ListBindings": {"method": "GET", "path": "/bindings", "req_ids": ["XSC-006", "NV-P-003", "DEL-002"]},
    "GetBindingReadiness": {"method": "GET", "path": "/bindings/readiness", "req_ids": ["XSC-006", "NV-P-001", "NV-P-002", "NV-P-003", "NV-P-004", "NV-P-005", "NV-P-006", "DEL-002", "DEL-003", "DEL-004"]},
    "GetDeliveryReadiness": {"method": "GET", "path": "/delivery/readiness", "req_ids": ["DEL-001", "DEL-002", "DEL-003", "DEL-004", "DEL-005", "XSC-001", "XSC-002", "XSC-003", "XSC-004", "XSC-005", "XSC-006", "NV-P-003"]},
    "GetPrototypeReadiness": {"method": "GET", "path": "/prototype/readiness", "req_ids": ["XSC-001", "XSC-002", "XSC-003", "XSC-004", "XSC-005", "XSC-006", "NV-P-003", "DEL-001", "DEL-002", "DEL-003", "DEL-004", "DEL-005"]},
    "GetHardwareInterfaces": {"method": "GET", "path": "/hardware/interfaces", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-001", "KH-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceActivationChecklist": {"method": "GET", "path": "/hardware/interfaces/activation-checklist", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionStatus": {"method": "GET", "path": "/hardware/interfaces/owner-decision-status", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "SubmitHardwareInterfaceOwnerDecisionEvidence": {"method": "POST", "path": "/hardware/interfaces/owner-decision-evidence", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceStatus": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/status", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceRetentionChecklist": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/retention-checklist", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklist": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklist": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollup": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "DryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoad": {"method": "POST", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatus": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunAuditConsistency": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/audit-consistency", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityChecklist": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityStatus": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/status", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalAuthorityAuditConsistency": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/audit-consistency", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "DryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecision": {"method": "POST", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunStatus": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/status", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionDryRunAuditConsistency": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/audit-consistency", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionClosureBlockerMatrix": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalDecisionReviewerMatrix": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffChecklist": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceStatus": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceAuditConsistency": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/audit-consistency", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceDecisionRollup": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessChecklist": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessAuditConsistency": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionRollup": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentChecklist": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditConsistency": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollup": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadApprovalReviewerEvidenceHandoffAcceptanceClosureReadinessDecisionReviewerAssignmentAuditDecisionRollupClosureHandoffReadinessSummary": {"method": "GET", "path": "/hardware/interfaces/owner-decision-evidence/adapter-load-approval-authority-checklist/decision-dry-run/closure-blocker-matrix/reviewer-matrix/evidence-handoff-checklist/acceptance-status/decision-rollup/closure-readiness-checklist/audit-consistency/decision-rollup/reviewer-assignment-checklist/audit-consistency/decision-rollup/closure-handoff-readiness-summary", "req_ids": ["XSC-004", "XSC-006", "HW-002", "KH-003", "KH-006", "KH-007", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetVehicleSignals": {"method": "GET", "path": "/vehicle/signals", "req_ids": ["XSC-002", "XSC-004", "XSC-006", "NV-F-004", "NV-F-005", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetVehicleSignalActivation": {"method": "GET", "path": "/vehicle/signals/activation", "req_ids": ["XSC-002", "XSC-004", "XSC-006", "NV-F-003", "NV-F-004", "NV-F-005", "NV-P-001", "NV-P-003", "DEL-002", "DEL-005"]},
    "GetVehicleSignalValidation": {"method": "GET", "path": "/vehicle/signals/validation", "req_ids": ["XSC-002", "XSC-004", "XSC-006", "NV-F-003", "NV-F-004", "NV-F-005", "NV-P-001", "NV-P-003", "KH-003", "KH-006", "KH-007", "DEL-002", "DEL-005"]},
}


def make_response(trace_id: str, status: str, payload: dict[str, Any], req_ids: list[str], error: dict[str, Any] | None = None) -> dict[str, Any]:
    return {
        "trace_id": trace_id,
        "status": status,
        "error_json": json.dumps(error, ensure_ascii=False) if error else "",
        "payload_json": json.dumps(payload, ensure_ascii=False),
        "req_ids": sorted(set(req_ids + ["XSC-006", "NV-P-003", "DEL-002"])),
    }


def parse_request(raw: bytes) -> tuple[str, str, dict[str, Any]]:
    envelope = json.loads(raw.decode("utf-8"))
    if not isinstance(envelope, dict):
        raise ValueError("GatewayRequest must be a JSON object")
    trace_id = envelope.get("trace_id")
    rpc = envelope.get("rpc")
    if not isinstance(trace_id, str) or not trace_id:
        raise ValueError("trace_id must be a non-empty string")
    if rpc not in RPC_MAP:
        raise ValueError(f"unsupported rpc: {rpc}")
    payload_json = envelope.get("payload_json") or "{}"
    if not isinstance(payload_json, str):
        raise ValueError("payload_json must be a JSON string")
    payload = json.loads(payload_json)
    if not isinstance(payload, dict):
        raise ValueError("payload_json must decode to an object")
    return trace_id, rpc, payload


def local_governance_precheck(trace_id: str, payload: dict[str, Any], reason: str | None = None) -> tuple[dict[str, Any], bool]:
    precheck = GRPC_GOVERNANCE.precheck(payload)
    policy = precheck["policy"]
    qos_decision = precheck["qos_decision"]
    allowed = policy["decision"] == "allow" and qos_decision["decision"] == "allow"
    service = payload.get("service", "vehicle-state")
    method = payload.get("method", "invoke")
    outcome = "grpc_prechecked_allowed"
    if policy["decision"] != "allow":
        outcome = "grpc_policy_or_lifecycle_rejected"
    elif qos_decision["decision"] != "allow":
        outcome = "grpc_qos_rejected"

    GRPC_GOVERNANCE.record_audit(
        trace_id,
        {
            "service": service,
            "method": method,
            "outcome": outcome,
            "policy_decision": policy["decision"],
            "lifecycle_state": precheck["lifecycle_state"],
            "qos_decision": qos_decision["decision"],
            "binding": "linux-grpc-json-sample",
        },
    )
    return {
        "state": "allowed" if allowed else "rejected",
        "service": service,
        "method": method,
        "policy": policy,
        "lifecycle_state": precheck["lifecycle_state"],
        "qos": precheck["qos"],
        "qos_decision": qos_decision,
        "audit": {
            "scope": "linux-grpc-json-sample",
            "persistence": "enabled" if DEFAULT_GRPC_AUDIT_LOG else "disabled",
            "path": DEFAULT_GRPC_AUDIT_LOG,
        },
        "precheck_source": {
            "mode": "local-runtime-governance",
            "fallback_reason": reason,
        },
        "req_ids": ["XSC-005", "XSC-006", "NV-G-002", "NV-G-004", "NV-G-005", "NV-G-006", "NV-G-007", "NV-P-003", "DEL-002"],
    }, allowed


def shared_governance_precheck(trace_id: str, payload: dict[str, Any]) -> tuple[dict[str, Any], bool] | None:
    if not DEFAULT_GOVERNANCE_SOCKET:
        return None

    return precheck_service_via_socket(
        DEFAULT_GOVERNANCE_SOCKET,
        trace_id,
        payload,
        ["XSC-005", "XSC-006", "NV-P-003", "DEL-002"],
    )


def shared_governance_diagnostic(trace_id: str, rpc: str, payload: dict[str, Any]) -> dict[str, Any] | None:
    if not DEFAULT_GOVERNANCE_SOCKET:
        return None
    if rpc == "GetRuntimeGovernance":
        return get_runtime_via_socket(
            DEFAULT_GOVERNANCE_SOCKET,
            trace_id,
            ["XSC-005", "XSC-006", "NV-G-001", "NV-G-007", "NV-P-003", "DEL-002"],
        )
    if rpc == "GetRecentAudit":
        return get_audit_via_socket(
            DEFAULT_GOVERNANCE_SOCKET,
            trace_id,
            payload,
            ["XSC-005", "XSC-006", "NV-G-007", "NV-P-003", "DEL-002"],
        )
    return None


def grpc_governance_precheck(trace_id: str, rpc: str, payload: dict[str, Any]) -> tuple[dict[str, Any] | None, bool]:
    if rpc != "InvokeService":
        return None, True

    try:
        shared = shared_governance_precheck(trace_id, payload)
        if shared is not None:
            return shared
    except (OSError, TimeoutError, json.JSONDecodeError, KeyError, TypeError) as exc:
        return local_governance_precheck(trace_id, payload, f"shared governance daemon unavailable: {exc}")

    return local_governance_precheck(trace_id, payload)


def call_gateway(base_url: str, trace_id: str, rpc: str, payload: dict[str, Any]) -> dict[str, Any]:
    mapping = RPC_MAP[rpc]
    diagnostic_fallback_reason = None
    if rpc in {"GetRuntimeGovernance", "GetRecentAudit"}:
        try:
            diagnostic = shared_governance_diagnostic(trace_id, rpc, payload)
            if diagnostic is not None:
                return make_response(
                    trace_id,
                    "ok",
                    {
                        "rpc": rpc,
                        "semantic_path": mapping["path"],
                        "forwarding": "shared-governance-socket",
                        "shared_governance_diagnostic": diagnostic,
                        "req_ids": mapping["req_ids"] + ["XSC-005", "NV-G-007"],
                    },
                    mapping["req_ids"] + ["XSC-005", "NV-G-007"],
                )
        except (OSError, TimeoutError, json.JSONDecodeError, KeyError, TypeError) as exc:
            diagnostic_fallback_reason = f"shared governance daemon unavailable: {exc}"

    governance_precheck, can_forward = grpc_governance_precheck(trace_id, rpc, payload)
    if governance_precheck is not None and not can_forward:
        return make_response(
            trace_id,
            "ok",
            {
                "rpc": rpc,
                "semantic_path": mapping["path"],
                "forwarding": "blocked-before-rest-gateway",
                "grpc_governance_precheck": governance_precheck,
                "req_ids": mapping["req_ids"] + ["XSC-005", "NV-G-004", "NV-G-005", "NV-G-007"],
            },
            mapping["req_ids"] + ["XSC-005", "NV-G-004", "NV-G-005", "NV-G-007"],
        )

    method = mapping["method"]
    body = None
    headers = {"Accept": "application/json"}
    if method == "POST":
        outbound = dict(payload)
        outbound.setdefault("trace_id", trace_id)
        body = json.dumps(outbound).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"

    req = request.Request(base_url.rstrip("/") + mapping["path"], data=body, headers=headers, method=method)
    with request.urlopen(req, timeout=5) as gateway_response:
        gateway_payload = json.loads(gateway_response.read().decode("utf-8"))

    return make_response(
        trace_id,
        "ok",
        {
            "rpc": rpc,
            "semantic_path": mapping["path"],
            "grpc_governance_precheck": governance_precheck,
            "shared_governance_diagnostic_fallback": diagnostic_fallback_reason,
            "gateway": gateway_payload,
            "req_ids": mapping["req_ids"],
        },
        mapping["req_ids"],
    )


def handle_bytes(raw: bytes, base_url: str) -> dict[str, Any]:
    fallback_trace_id = "unknown"
    try:
        trace_id, rpc, payload = parse_request(raw)
        fallback_trace_id = trace_id
        return call_gateway(base_url, trace_id, rpc, payload)
    except json.JSONDecodeError as exc:
        return make_response(fallback_trace_id, "error", {}, ["XSC-006", "NV-P-003"], {"code": "INVALID_JSON", "message": str(exc)})
    except ValueError as exc:
        return make_response(fallback_trace_id, "error", {}, ["XSC-006", "NV-P-003"], {"code": "INVALID_REQUEST", "message": str(exc)})
    except (HTTPError, URLError, TimeoutError, OSError) as exc:
        return make_response(fallback_trace_id, "error", {}, ["XSC-006", "NV-P-003"], {"code": "GATEWAY_UNAVAILABLE", "message": str(exc)})


def serve(host: str, port: int, base_url: str) -> int:
    shutdown = False

    def stop(_signum: int, _frame: Any) -> None:
        nonlocal shutdown
        shutdown = True

    signal.signal(signal.SIGTERM, stop)
    signal.signal(signal.SIGINT, stop)

    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as server:
        server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server.bind((host, port))
        server.listen(8)
        server.settimeout(0.2)
        print(f"Central Brain Linux gRPC/RPC JSON contract sample listening on {host}:{port}", flush=True)
        while not shutdown:
            try:
                conn, _addr = server.accept()
            except socket.timeout:
                continue
            with conn:
                chunks = []
                while True:
                    chunk = conn.recv(65536)
                    if not chunk:
                        break
                    chunks.append(chunk)
                payload = handle_bytes(b"".join(chunks), base_url)
                conn.sendall(json.dumps(payload, ensure_ascii=False).encode("utf-8") + b"\n")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the Central Brain Linux gRPC/RPC contract sample.")
    parser.add_argument("--host", default=DEFAULT_HOST)
    parser.add_argument("--port", type=int, default=DEFAULT_PORT)
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL)
    args = parser.parse_args()
    try:
        return serve(args.host, args.port, args.base_url)
    except OSError as exc:
        print(f"gRPC/RPC sample failed: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
