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

BINDING_READINESS_ROWS: list[dict[str, Any]] = [
    {
        "binding": "android-binder-aidl",
        "platform": "Android",
        "current_state": "service-stub-sample",
        "ready_for": ["debug APK integration", "AIDL contract review", "system-service planning"],
        "blocked_by": [
            "target AAOS signing and priv-app policy",
            "SELinux domain and service manager registration decision",
            "production shared Runtime & Governance backend owner",
        ],
        "validation": [
            "bash tools/check_central_brain_binding_artifacts.sh",
            "bash tools/build_central_brain_console.sh",
            "bash tools/check_central_brain_android_system_service_docs.sh",
        ],
        "req_ids": ["XSC-006", "NV-P-002", "DEL-001", "DEL-003", "DEL-004"],
    },
    {
        "binding": "linux-ipc",
        "platform": "Linux",
        "current_state": "active-sample",
        "ready_for": ["Unix socket local integration", "shared governance precheck sample", "systemd sample review"],
        "blocked_by": [
            "target distro package format and service identity decision",
            "production shared governance daemon replacement",
            "LSM/SELinux/AppArmor policy for target image",
        ],
        "validation": [
            "bash tools/smoke_central_brain_linux_ipc.sh",
            "bash tools/check_central_brain_linux_package_profile.sh",
            "bash tools/check_central_brain_linux_systemd_hardening.sh",
        ],
        "req_ids": ["XSC-006", "NV-P-002", "XSC-005", "DEL-002", "DEL-003", "DEL-004"],
    },
    {
        "binding": "linux-grpc-rpc",
        "platform": "Linux",
        "current_state": "grpc-json-active-sample",
        "ready_for": ["proto contract review", "JSON TCP contract smoke", "shared governance precheck sample"],
        "blocked_by": [
            "grpcio or C++ gRPC target runtime availability",
            "credential source for production RPC calls",
            "production shared governance backend replacement",
        ],
        "validation": [
            "bash tools/smoke_central_brain_linux_grpc.sh",
            "bash tools/check_central_brain_binding_artifacts.sh",
        ],
        "req_ids": ["XSC-006", "NV-P-003", "XSC-005", "DEL-002", "DEL-004"],
    },
    {
        "binding": "rest-http-json",
        "platform": "Android emulator/Linux host",
        "current_state": "active-prototype",
        "ready_for": ["semantic gateway smoke", "contract reference", "prototype fallback"],
        "blocked_by": [
            "replacement by Android Binder, Linux IPC, or true gRPC in production paths",
            "service identity and policy source for non-debug deployment",
        ],
        "validation": [
            "bash tools/smoke_central_brain_semantic_gateway.sh",
            "python3 -m json.tool central-brain/contracts/central_brain_api.json",
        ],
        "req_ids": ["XSC-002", "XSC-003", "XSC-005", "XSC-006", "NV-P-005", "DEL-001", "DEL-002"],
    },
    {
        "binding": "mqtt",
        "platform": "Android/Linux",
        "current_state": "planned-policy-gated",
        "ready_for": ["privacy and policy contract discussion"],
        "blocked_by": [
            "cloud vehicle-message broker selection",
            "privacy routing and audit export policy",
        ],
        "validation": ["GET /bindings/readiness"],
        "req_ids": ["XSC-006", "NV-P-004", "DEL-004"],
    },
    {
        "binding": "someip",
        "platform": "Linux/vehicle network",
        "current_state": "planned-after-vehicle-network",
        "ready_for": ["vehicle network integration planning"],
        "blocked_by": [
            "target vehicle service discovery stack",
            "DBC/ARXML or service catalog source",
        ],
        "validation": ["GET /bindings/readiness"],
        "req_ids": ["XSC-006", "NV-P-001", "DEL-004"],
    },
    {
        "binding": "dds",
        "platform": "Linux/Android native",
        "current_state": "planned-for-high-rate-topics",
        "ready_for": ["high-rate event data-plane planning"],
        "blocked_by": [
            "DDS vendor/runtime selection",
            "high-frequency topic QoS and backpressure requirements",
            "shared memory/Safety Runtime constraints for target platform",
        ],
        "validation": ["GET /bindings/readiness"],
        "req_ids": ["XSC-006", "NV-P-006", "FW-U-003", "DEL-004"],
    },
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
            "/governance/backend-contract",
            "/governance/migration-check",
            "/governance/deployment-plan",
            "/native/driver-gaps",
            "/soa/contracts",
            "/bindings/readiness",
        ],
        "artifacts": ["central-brain/contracts/central_brain_api.json"],
        "req_ids": ["XSC-001", "XSC-002", "XSC-003", "XSC-005", "XSC-006", "APP-004", "FW-U-003", "FW-U-004", "FW-S-004", "KH-003", "KH-006", "DEL-005", "NV-G-003", "NV-G-004", "NV-P-005", "NV-P-006"],
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
            "getServiceContractsJson -> /soa/contracts",
            "invokeServiceJson -> /soa/invoke",
            "evaluatePolicyJson -> /policy/evaluate",
            "precheckGovernanceJson -> /governance/precheck",
            "getGovernanceBackendContractJson -> /governance/backend-contract",
            "getGovernanceMigrationCheckJson -> /governance/migration-check",
            "getGovernanceDeploymentPlanJson -> /governance/deployment-plan",
            "getRuntimeGovernanceJson -> /governance/runtime",
            "getRecentAuditJson -> /audit/recent",
            "listBindingsJson -> /bindings",
            "getBindingDetailJson -> /bindings/detail",
            "getBindingReadinessJson -> /bindings/readiness",
            "getNativeAdaptersDetailJson -> /native/adapters/detail",
            "getDriverHalGapsJson -> /native/driver-gaps",
        ],
        "artifacts": [
            "central-brain/bindings/android/aidl/com/centralbrain/binding/ICentralBrainGateway.aidl",
            "central-brain/bindings/android/java/com/centralbrain/binding/CentralBrainGatewayBinderService.java",
            "central-brain/bindings/android/java/com/centralbrain/binding/CentralBrainGatewayClient.java",
            "central-brain/bindings/android/README.md",
            "docs/CENTRAL_BRAIN_ANDROID_SYSTEM_SERVICE_INTEGRATION.md",
        ],
        "req_ids": ["XSC-001", "XSC-002", "XSC-003", "XSC-004", "XSC-005", "XSC-006", "APP-004", "FW-U-003", "FW-U-004", "FW-U-007", "FW-S-004", "FW-S-005", "KH-003", "KH-006", "DEL-005", "NV-G-002", "NV-G-003", "NV-G-004", "NV-G-005", "NV-G-006", "NV-G-007", "NV-P-002", "NV-P-005", "NV-P-006", "DEL-001", "DEL-003", "DEL-004"],
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
            "soa.contracts.get -> /soa/contracts",
            "soa.service.invoke -> shared Linux governance daemon precheck with local fallback -> /soa/invoke",
            "policy.evaluate -> /policy/evaluate",
            "governance.precheck -> /governance/precheck or direct shared governance socket",
            "governance.backend.contract.get -> /governance/backend-contract",
            "governance.migration.check -> /governance/migration-check",
            "governance.deployment.plan.get -> /governance/deployment-plan",
            "governance.runtime.get -> shared governance socket diagnostic with REST gateway fallback",
            "audit.recent.get -> shared governance socket diagnostic with REST gateway fallback",
            "bindings.list -> /bindings",
            "bindings.readiness.get -> /bindings/readiness",
        ],
        "artifacts": [
            "central-brain/bindings/linux/ipc/central_brain_ipc_envelope.schema.json",
            "central-brain/bindings/linux/ipc/central_brain_governance_daemon.py",
            "central-brain/bindings/linux/ipc/central_brain_governance_client.py",
            "central-brain/bindings/linux/ipc/central_brain_ipc_daemon.py",
            "central-brain/bindings/linux/ipc/central_brain_ipc_client.py",
            "central-brain/bindings/linux/README.md",
        ],
        "req_ids": ["XSC-001", "XSC-002", "XSC-003", "XSC-005", "XSC-006", "APP-004", "FW-U-003", "FW-U-004", "FW-S-004", "NV-G-002", "NV-G-003", "NV-G-004", "NV-G-005", "NV-G-006", "NV-G-007", "NV-P-002", "NV-P-006", "DEL-002"],
    },
    {
        "name": "grpc",
        "status": "grpc-json-active-sample",
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
            "CentralBrainGateway.ListServices -> /soa/services",
            "CentralBrainGateway.GetServiceContracts -> /soa/contracts",
            "CentralBrainGateway.InvokeService -> /soa/invoke",
            "CentralBrainGateway.EvaluatePolicy -> /policy/evaluate",
            "CentralBrainGateway.PrecheckGovernance -> /governance/precheck",
            "CentralBrainGateway.GetGovernanceBackendContract -> /governance/backend-contract",
            "CentralBrainGateway.GetGovernanceMigrationCheck -> /governance/migration-check",
            "CentralBrainGateway.GetGovernanceDeploymentPlan -> /governance/deployment-plan",
            "CentralBrainGateway.GetRuntimeGovernance -> shared governance socket diagnostic with REST gateway fallback",
            "CentralBrainGateway.GetRecentAudit -> shared governance socket diagnostic with REST gateway fallback",
            "CentralBrainGateway.ListBindings -> /bindings",
            "CentralBrainGateway.GetBindingReadiness -> /bindings/readiness",
        ],
        "artifacts": [
            "central-brain/bindings/linux/proto/central_brain_gateway.proto",
            "central-brain/bindings/linux/ipc/central_brain_governance_client.py",
            "central-brain/bindings/linux/grpc/central_brain_grpc_server.py",
            "central-brain/bindings/linux/grpc/central_brain_grpc_client.py",
            "central-brain/deploy/linux/systemd/central-brain-linux-grpc.service",
        ],
        "req_ids": ["XSC-001", "XSC-002", "XSC-003", "XSC-005", "XSC-006", "APP-004", "FW-U-003", "FW-U-004", "FW-U-006", "FW-S-004", "NV-G-002", "NV-G-003", "NV-G-004", "NV-G-005", "NV-G-006", "NV-G-007", "NV-P-003", "NV-P-006", "DEL-002"],
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
                "Linux IPC active sample can use a shared Linux governance daemon for SOA precheck before forwarding allowed service invocations to the semantic gateway; it falls back to local precheck when that socket is unavailable.",
                "Linux IPC and gRPC/RPC samples call the shared governance socket through one reusable client helper for precheck plus read-only runtime/audit diagnostics, so transport replacement does not duplicate the Runtime & Governance envelope.",
                "The shared governance backend target contract is exposed at /governance/backend-contract for Android Binder, Linux IPC, and gRPC/RPC integration alignment.",
                "The governance migration check is exposed at /governance/migration-check to verify production backend replacement invariants without implementing the production backend.",
                "The governance deployment plan is exposed at /governance/deployment-plan to keep Android system service, Linux daemon, and gRPC/RPC deployment-shape decisions visible without implementing the production backend.",
                "Linux shared governance daemon also exposes runtime status and recent audit diagnostics over the same Unix socket; IPC/gRPC use that direct path before REST fallback and these operations do not dispatch services.",
                "Linux gRPC/RPC sample mirrors the proto GatewayRequest/GatewayResponse fields over a dependency-free JSON TCP wrapper because grpcio is not available in this workspace.",
                "Linux gRPC/RPC InvokeService uses the same shared governance daemon precheck with local fallback before forwarding allowed SOA calls.",
                "Virtualization and driver layers are documented integration assumptions only in this increment.",
            ],
            "req_ids": BINDING_REQ_IDS + ["DEL-001", "DEL-002", "DEL-003", "DEL-004"],
        }

    def readiness_payload(self) -> dict[str, Any]:
        rows = copy.deepcopy(BINDING_READINESS_ROWS)
        return {
            "readiness": rows,
            "summary": {
                "production_ready": False,
                "active_samples": [
                    row["binding"]
                    for row in rows
                    if row["current_state"] in {"active-prototype", "active-sample", "grpc-json-active-sample", "service-stub-sample"}
                ],
                "planned_bindings": [
                    row["binding"]
                    for row in rows
                    if row["current_state"].startswith("planned")
                ],
                "driver_development_triggered": False,
                "virtualization_development_triggered": False,
                "service_dispatch_triggered": False,
            },
            "non_goals": [
                "No production shared governance backend is implemented by this readiness contract.",
                "No true gRPC runtime, MQTT broker, SOME/IP stack, DDS broker, Driver/HAL, Safety Runtime, vehicle bus, or virtualization code is added.",
            ],
            "next_decisions": [
                "Choose target Linux distro/package format before replacing the package profile sample.",
                "Choose true gRPC runtime and credential source before replacing the JSON TCP wrapper.",
                "Choose Android system/privileged service owner, signing, and SELinux shape before framework integration.",
            ],
            "req_ids": sorted(set(BINDING_REQ_IDS + ["XSC-005", "DEL-001", "DEL-002", "DEL-003", "DEL-004"])),
        }
