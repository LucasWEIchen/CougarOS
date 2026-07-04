#!/usr/bin/env python3
"""Protocol Binding registry for the Central Brain prototype."""

from __future__ import annotations

import copy
from typing import Any


BINDING_REQ_IDS = [
    "XSC-006",
    "NV-P-001",
    "NV-P-002",
    "NV-P-003",
    "NV-P-004",
    "NV-P-005",
    "NV-P-006",
]

BINDING_REGISTRY: list[dict[str, Any]] = [
    {
        "name": "rest-http-json",
        "status": "active-prototype",
        "platforms": ["Android emulator", "Linux host"],
        "semantic_paths": [
            "/uib/context",
            "/uib/state",
            "/uib/events/topics",
            "/uib/events/publish",
            "/uib/events/recent",
            "/uib/actions/request",
            "/ai/sdk/capabilities",
            "/agent/plan",
            "/agent/execute",
            "/skills",
            "/skills/{skill_id}/invoke",
            "/memory/query",
            "/soa/services",
            "/soa/invoke",
            "/governance/precheck",
        ],
        "artifacts": ["central-brain/contracts/central_brain_api.json"],
        "req_ids": ["XSC-001", "XSC-002", "XSC-005", "XSC-006", "APP-004", "FW-U-003", "FW-U-004", "NV-G-004", "NV-P-005", "NV-P-006"],
    },
    {
        "name": "android-binder-aidl",
        "status": "service-stub-sample",
        "platforms": ["Android"],
        "semantic_paths": [
            "getContextJson -> /uib/context",
            "getStateJson -> /uib/state",
            "listEventTopicsJson -> /uib/events/topics",
            "publishEventJson -> /uib/events/publish",
            "getRecentEventsJson -> /uib/events/recent",
            "getAiSdkCapabilitiesJson -> /ai/sdk/capabilities",
            "planAgentTaskJson -> /agent/plan",
            "executeAgentTaskJson -> /agent/execute",
            "listSkillsJson -> /skills",
            "invokeSkillJson -> /skills/{skill_id}/invoke",
            "queryMemoryJson -> /memory/query",
            "requestActionJson -> /uib/actions/request",
            "listServicesJson -> /soa/services",
            "invokeServiceJson -> /soa/invoke",
            "evaluatePolicyJson -> /policy/evaluate",
            "precheckGovernanceJson -> /governance/precheck",
            "getRuntimeGovernanceJson -> /governance/runtime",
            "getRecentAuditJson -> /audit/recent",
            "listBindingsJson -> /bindings",
            "getBindingDetailJson -> /bindings/detail",
            "getNativeAdaptersDetailJson -> /native/adapters/detail",
        ],
        "artifacts": [
            "central-brain/bindings/android/aidl/com/centralbrain/binding/ICentralBrainGateway.aidl",
            "central-brain/bindings/android/java/com/centralbrain/binding/CentralBrainGatewayBinderService.java",
            "central-brain/bindings/android/java/com/centralbrain/binding/CentralBrainGatewayClient.java",
            "central-brain/bindings/android/README.md",
            "docs/CENTRAL_BRAIN_ANDROID_SYSTEM_SERVICE_INTEGRATION.md",
        ],
        "req_ids": ["XSC-001", "XSC-002", "XSC-003", "XSC-004", "XSC-005", "XSC-006", "APP-004", "FW-U-003", "FW-U-004", "FW-U-007", "FW-S-005", "NV-G-002", "NV-G-004", "NV-G-005", "NV-G-006", "NV-G-007", "NV-P-002", "NV-P-005", "NV-P-006", "DEL-001", "DEL-003", "DEL-004"],
    },
    {
        "name": "linux-ipc",
        "status": "active-sample",
        "platforms": ["Linux"],
        "semantic_paths": [
            "uib.context.get -> /uib/context",
            "uib.state.get -> /uib/state",
            "uib.events.topics -> /uib/events/topics",
            "uib.events.publish -> /uib/events/publish",
            "uib.events.recent -> /uib/events/recent",
            "uib.actions.request -> /uib/actions/request",
            "ai.sdk.capabilities -> /ai/sdk/capabilities",
            "agent.plan -> /agent/plan",
            "agent.execute -> /agent/execute",
            "skills.list -> /skills",
            "skills.invoke -> /skills/{skill_id}/invoke",
            "memory.query -> /memory/query",
            "soa.services.list -> /soa/services",
            "soa.service.invoke -> local Runtime & Governance precheck -> /soa/invoke",
            "policy.evaluate -> /policy/evaluate",
            "governance.precheck -> /governance/precheck",
            "governance.runtime.get -> /governance/runtime",
            "audit.recent.get -> /audit/recent",
            "bindings.list -> /bindings",
        ],
        "artifacts": [
            "central-brain/bindings/linux/ipc/central_brain_ipc_envelope.schema.json",
            "central-brain/bindings/linux/ipc/central_brain_ipc_daemon.py",
            "central-brain/bindings/linux/ipc/central_brain_ipc_client.py",
            "central-brain/bindings/linux/README.md",
        ],
        "req_ids": ["XSC-001", "XSC-002", "XSC-003", "XSC-005", "XSC-006", "APP-004", "FW-U-003", "FW-U-004", "NV-G-002", "NV-G-004", "NV-G-005", "NV-G-006", "NV-G-007", "NV-P-002", "NV-P-006", "DEL-002"],
    },
    {
        "name": "grpc",
        "status": "contract-skeleton",
        "platforms": ["Android", "Linux"],
        "semantic_paths": [
            "CentralBrainGateway.GetContext -> /uib/context",
            "CentralBrainGateway.GetState -> /uib/state",
            "CentralBrainGateway.ListEventTopics -> /uib/events/topics",
            "CentralBrainGateway.PublishEvent -> /uib/events/publish",
            "CentralBrainGateway.GetRecentEvents -> /uib/events/recent",
            "CentralBrainGateway.GetAiSdkCapabilities -> /ai/sdk/capabilities",
            "CentralBrainGateway.PlanAgentTask -> /agent/plan",
            "CentralBrainGateway.ExecuteAgentTask -> /agent/execute",
            "CentralBrainGateway.ListSkills -> /skills",
            "CentralBrainGateway.InvokeSkill -> /skills/{skill_id}/invoke",
            "CentralBrainGateway.QueryMemory -> /memory/query",
            "CentralBrainGateway.RequestAction -> /uib/actions/request",
            "CentralBrainGateway.InvokeService -> /soa/invoke",
            "CentralBrainGateway.PrecheckGovernance -> /governance/precheck",
        ],
        "artifacts": ["central-brain/bindings/linux/proto/central_brain_gateway.proto"],
        "req_ids": ["XSC-001", "XSC-002", "XSC-003", "XSC-005", "XSC-006", "APP-004", "FW-U-003", "FW-U-004", "NV-G-002", "NV-G-004", "NV-G-005", "NV-G-006", "NV-G-007", "NV-P-003", "NV-P-006", "DEL-002"],
    },
    {
        "name": "mqtt",
        "status": "planned-policy-gated",
        "platforms": ["Android", "Linux"],
        "semantic_paths": ["cloud/vehicle message topics via Policy and Privacy checks"],
        "artifacts": [],
        "req_ids": ["XSC-006", "NV-P-004"],
    },
    {
        "name": "someip",
        "status": "planned-after-vehicle-network",
        "platforms": ["Linux", "QNX/RT domain integration assumption"],
        "semantic_paths": ["vehicle cross-ECU services via service discovery"],
        "artifacts": [],
        "req_ids": ["XSC-006", "NV-P-001"],
    },
    {
        "name": "dds",
        "status": "planned-for-high-rate-topics",
        "platforms": ["Linux", "Android native"],
        "semantic_paths": [
            "planned DDS delivery for /uib/events/topics and /uib/events/recent high-rate topics",
        ],
        "artifacts": [],
        "req_ids": ["XSC-006", "NV-P-006"],
    },
]


class ProtocolBindingRegistry:
    """In-process binding registry for prototype discovery and smoke tests."""

    def list_payload(self) -> dict[str, Any]:
        return {
            "bindings": copy.deepcopy(BINDING_REGISTRY),
            "req_ids": BINDING_REQ_IDS,
        }

    def detail_payload(self) -> dict[str, Any]:
        bindings = copy.deepcopy(BINDING_REGISTRY)
        return {
            "bindings": bindings,
            "active_semantic_entrypoints": sorted(
                {
                    path
                    for binding in bindings
                    for path in binding.get("semantic_paths", [])
                    if path.startswith("/")
                }
            ),
            "contract_artifacts": sorted(
                {
                    artifact
                    for binding in bindings
                    for artifact in binding.get("artifacts", [])
                }
            ),
            "constraints": [
                "Protocol Binding cannot bypass Uni Info Bus semantic objects.",
                "SOA calls remain policy, lifecycle, and audit checked.",
                "Runtime & Governance precheck can be called without service dispatch; default QoS behavior is diagnostic peek, not reservation.",
                "Android Binder service sample maps Binder calls to the semantic gateway; REST remains the upstream prototype binding.",
                "Android system/privileged service integration is documented only; target signing, SELinux, and service manager choices remain platform decisions.",
                "Linux IPC active sample applies a local Runtime & Governance SOA precheck before forwarding allowed service invocations to the semantic gateway; REST remains the upstream prototype binding.",
                "Virtualization and driver layers are documented integration assumptions only in this increment.",
            ],
            "req_ids": BINDING_REQ_IDS + ["DEL-001", "DEL-002", "DEL-003", "DEL-004"],
        }
