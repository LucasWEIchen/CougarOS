#!/usr/bin/env python3
"""Hardware empty-interface registry for the Central Brain prototype.

Req IDs: XSC-004, XSC-006, HW-002, KH-003, KH-006, KH-007, DEL-001, DEL-002,
DEL-005.

This module intentionally exposes contracts only. It must not open device
nodes, call vendor HALs, probe VHAL, allocate DMA buffers, or touch
virtualization APIs in the Python prototype.
"""

from __future__ import annotations

import copy
import uuid
from typing import Any


HARDWARE_REQ_IDS = [
    "XSC-004",
    "XSC-006",
    "HW-002",
    "KH-001",
    "KH-002",
    "KH-003",
    "KH-006",
    "KH-007",
    "APP-004",
    "FW-U-003",
    "FW-U-004",
    "NV-F-004",
    "NV-F-005",
    "NV-F-006",
    "NV-F-011",
    "NV-P-001",
    "NV-P-006",
    "HV-001",
    "HV-002",
    "HV-003",
    "DEL-001",
    "DEL-002",
    "DEL-005",
]


EMPTY_INTERFACE_REGISTRY: list[dict[str, Any]] = [
    {
        "interface_id": "npu-runtime",
        "name": "External PCIe NPU Runtime",
        "architecture_layer": "AI SDK -> Model Runtime Adapter -> Driver/HAL",
        "implementation_state": "empty-interface",
        "hardware_dependency": "external PCIe NPU accelerator card",
        "android_primary_path": "future vendor HAL/AIDL or JNI bridge behind the AI SDK facade",
        "linux_sync_path": "future device node, vendor daemon, or runtime SDK adapter behind the same AI SDK facade",
        "reserved_methods": [
            {
                "name": "load_model",
                "request_contract": "model_id, model_uri, runtime_policy",
                "response_contract": "model_handle, runtime_capabilities, safety_state",
                "side_effects": "none-in-prototype",
            },
            {
                "name": "infer",
                "request_contract": "model_handle, input_tensors, qos_policy",
                "response_contract": "output_tensors, timing, accelerator_status",
                "side_effects": "none-in-prototype",
            },
            {
                "name": "get_accelerator_status",
                "request_contract": "trace_id",
                "response_contract": "power, temperature, memory, error_state",
                "side_effects": "none-in-prototype",
            },
        ],
        "driver_gap_ids": ["DRV-GAP-001", "DRV-GAP-005"],
        "activation_trigger": "real PCIe NPU card, vendor runtime, IOMMU/DMA policy, and safety isolation requirements are available",
        "req_ids": ["XSC-001", "APP-004", "HW-002", "NV-F-011", "KH-003", "KH-006", "KH-007", "DEL-005"],
    },
    {
        "interface_id": "vehicle-bus",
        "name": "Vehicle Signal and Control Bus",
        "architecture_layer": "Uni Info Bus -> SOA -> AIOS Kernel native adapter -> Driver/HAL",
        "implementation_state": "empty-interface",
        "hardware_dependency": "VHAL, CAN/LIN/FlexRay/Ethernet signal source, DBC or ARXML catalog",
        "android_primary_path": "future Vehicle HAL or platform service adapter mapped to Uni Info Bus objects",
        "linux_sync_path": "future SocketCAN, SOME/IP, vendor daemon, or signal gateway adapter",
        "reserved_methods": [
            {
                "name": "read_signal",
                "request_contract": "signal_name, freshness_ms, safety_state",
                "response_contract": "value, timestamp_ms, source_quality",
                "side_effects": "none-in-prototype",
            },
            {
                "name": "write_actuator",
                "request_contract": "action_name, target, policy_token",
                "response_contract": "accepted, dispatch_id, interlock_state",
                "side_effects": "none-in-prototype",
            },
            {
                "name": "subscribe_signal",
                "request_contract": "topic, qos, backpressure_policy",
                "response_contract": "subscription_id, event_contract",
                "side_effects": "none-in-prototype",
            },
        ],
        "driver_gap_ids": ["DRV-GAP-002", "DRV-GAP-004"],
        "activation_trigger": "target vehicle bus catalog, permission model, and HAL/service transport are selected",
        "req_ids": ["XSC-002", "XSC-003", "XSC-004", "FW-U-003", "FW-U-004", "NV-F-004", "NV-F-005", "KH-006", "DEL-005"],
    },
    {
        "interface_id": "camera-audio-sensors",
        "name": "Cabin Camera, Audio, and Sensor Inputs",
        "architecture_layer": "Application layer -> AI SDK -> Uni Info Bus event/context objects",
        "implementation_state": "empty-interface",
        "hardware_dependency": "camera HAL, audio capture path, IMU/sensor HAL, privacy gate",
        "android_primary_path": "future Android Camera/Audio/Sensor APIs or vendor HAL under policy gate",
        "linux_sync_path": "future V4L2, ALSA/PipeWire, sensor hub, or vendor daemon adapter",
        "reserved_methods": [
            {
                "name": "open_stream",
                "request_contract": "stream_type, purpose, privacy_scope",
                "response_contract": "stream_handle, format, safety_state",
                "side_effects": "none-in-prototype",
            },
            {
                "name": "read_frame_or_sample",
                "request_contract": "stream_handle, deadline_ms",
                "response_contract": "buffer_ref, metadata, redaction_state",
                "side_effects": "none-in-prototype",
            },
        ],
        "driver_gap_ids": ["DRV-GAP-003"],
        "activation_trigger": "target cockpit sensors, privacy rules, and data-plane runtime are selected",
        "req_ids": ["APP-004", "NV-F-006", "KH-003", "KH-006", "DEL-005"],
    },
    {
        "interface_id": "ethernet-someip-dds-tsn",
        "name": "Vehicle Ethernet, SOME/IP, DDS, and TSN Binding",
        "architecture_layer": "Protocol Binding -> SOA/Uni Info Bus event data plane",
        "implementation_state": "empty-interface",
        "hardware_dependency": "vehicle Ethernet NIC, SOME/IP stack, DDS runtime, TSN QoS policy",
        "android_primary_path": "future native transport service exposed through Binder or AIOS Kernel adapter",
        "linux_sync_path": "future SOME/IP/DDS daemon or C++ binding beside the Python prototype contract",
        "reserved_methods": [
            {
                "name": "publish_topic",
                "request_contract": "topic, payload, qos, safety_state",
                "response_contract": "accepted, sequence_id, transport_state",
                "side_effects": "none-in-prototype",
            },
            {
                "name": "call_service",
                "request_contract": "service_id, method, payload, policy_token",
                "response_contract": "result, transport_status, audit_id",
                "side_effects": "none-in-prototype",
            },
        ],
        "driver_gap_ids": ["DRV-GAP-004"],
        "activation_trigger": "target network stack, service discovery source, and QoS/backpressure rules are selected",
        "req_ids": ["XSC-003", "XSC-006", "NV-P-001", "NV-P-006", "KH-001", "KH-003", "DEL-005"],
    },
    {
        "interface_id": "shared-memory-safety-runtime",
        "name": "Shared Memory and Safety Runtime Boundary",
        "architecture_layer": "Runtime & Governance -> AIOS Kernel -> virtualization boundary",
        "implementation_state": "empty-interface",
        "hardware_dependency": "shared memory allocator, safety monitor, isolation policy, hypervisor or OS partitioning",
        "android_primary_path": "future platform-owned safety service or native shared-memory bridge",
        "linux_sync_path": "future memfd/hugepage/vendor shared-memory bridge controlled by safety policy",
        "reserved_methods": [
            {
                "name": "reserve_buffer",
                "request_contract": "size, producer, consumer, safety_class",
                "response_contract": "buffer_handle, permissions, lifetime",
                "side_effects": "none-in-prototype",
            },
            {
                "name": "report_safety_state",
                "request_contract": "component, state, evidence",
                "response_contract": "accepted, mitigation, audit_id",
                "side_effects": "none-in-prototype",
            },
        ],
        "driver_gap_ids": ["DRV-GAP-005"],
        "activation_trigger": "platform safety owner defines shared-memory, ASIL/QM, and isolation rules",
        "req_ids": ["XSC-005", "KH-002", "KH-007", "HV-001", "HV-002", "HV-003", "DEL-005"],
    },
]

HARDWARE_ACTIVATION_REQ_IDS = [
    "XSC-004",
    "XSC-006",
    "HW-002",
    "KH-003",
    "KH-006",
    "KH-007",
    "DEL-001",
    "DEL-002",
    "DEL-005",
]

HARDWARE_ACTIVATION_GATES = [
    {
        "gate_id": "HW-ACT-001",
        "name": "target-interface-owner-assigned",
        "required_evidence": "Target Android/Linux owner, process boundary, and escalation path are assigned for each hardware interface.",
        "passed": False,
    },
    {
        "gate_id": "HW-ACT-002",
        "name": "driver-gap-reviewed",
        "required_evidence": "Linked DRV-GAP items have target platform evidence and minimal Driver/HAL development decision.",
        "passed": False,
    },
    {
        "gate_id": "HW-ACT-003",
        "name": "android-abi-contract-approved",
        "required_evidence": "Android HAL/AIDL/NDK/vendor SDK ABI, permission model, and Binder identity mapping are approved.",
        "passed": False,
    },
    {
        "gate_id": "HW-ACT-004",
        "name": "linux-abi-contract-approved",
        "required_evidence": "Linux device node, ioctl/sysfs/vendor library, daemon, or IPC ABI and service identity are approved.",
        "passed": False,
    },
    {
        "gate_id": "HW-ACT-005",
        "name": "safety-and-policy-binding-approved",
        "required_evidence": "Safety State, Policy, Runtime & Governance audit, fault fallback, and ASIL/QM assumptions are approved.",
        "passed": False,
    },
    {
        "gate_id": "HW-ACT-006",
        "name": "smoke-test-harness-defined",
        "required_evidence": "No-hardware prototype smoke plus target hardware smoke commands and pass/fail evidence format are defined.",
        "passed": False,
    },
    {
        "gate_id": "HW-ACT-007",
        "name": "rollback-and-fault-semantics-approved",
        "required_evidence": "Timeout, reset, degrade, retry, rollback, and telemetry behavior are approved for target hardware.",
        "passed": False,
    },
    {
        "gate_id": "HW-ACT-008",
        "name": "no-hardware-access-in-prototype",
        "required_evidence": "Prototype exposes activation criteria only and reports no device, HAL, vendor SDK, shared memory, or virtualization access.",
        "passed": True,
    },
]

HARDWARE_OWNER_DECISION_REQ_IDS = HARDWARE_ACTIVATION_REQ_IDS

HARDWARE_OWNER_DECISION_GATES = [
    {
        "gate_id": "HW-ODS-001",
        "name": "target-interface-owner-open",
        "required_decision": "Assign the target interface owner and escalation path for each hardware interface.",
        "status": "open",
        "passed": False,
    },
    {
        "gate_id": "HW-ODS-002",
        "name": "android-abi-owner-open",
        "required_decision": "Assign the Android HAL/AIDL/NDK/vendor SDK ABI owner and permission model reviewer.",
        "status": "open",
        "passed": False,
    },
    {
        "gate_id": "HW-ODS-003",
        "name": "linux-abi-owner-open",
        "required_decision": "Assign the Linux device node, ioctl/sysfs/vendor library, daemon, or IPC ABI owner.",
        "status": "open",
        "passed": False,
    },
    {
        "gate_id": "HW-ODS-004",
        "name": "driver-gap-owner-open",
        "required_decision": "Assign owner review for linked Driver/HAL gap IDs and minimal new development decisions.",
        "status": "open",
        "passed": False,
    },
    {
        "gate_id": "HW-ODS-005",
        "name": "safety-policy-owner-open",
        "required_decision": "Assign Safety/Policy owner for fault isolation, ASIL/QM assumptions, and audit behavior.",
        "status": "open",
        "passed": False,
    },
    {
        "gate_id": "HW-ODS-006",
        "name": "target-smoke-evidence-open",
        "required_decision": "Attach target hardware smoke evidence owner and pass/fail evidence format.",
        "status": "open",
        "passed": False,
    },
    {
        "gate_id": "HW-ODS-007",
        "name": "rollback-fault-semantics-open",
        "required_decision": "Assign owner for timeout, reset, degrade, retry, rollback, and telemetry semantics.",
        "status": "open",
        "passed": False,
    },
    {
        "gate_id": "HW-ODS-008",
        "name": "no-hardware-access-in-prototype",
        "required_decision": "Keep this Python prototype contract-only until target evidence is reviewed.",
        "status": "satisfied-by-prototype-boundary",
        "passed": True,
    },
]

HARDWARE_OWNER_DECISION_ITEMS = [
    {
        "decision_id": "target_interface_owner",
        "gate_id": "HW-ODS-001",
        "owner_status": "open",
        "required_owner": "target Android/Linux hardware-interface owner",
    },
    {
        "decision_id": "android_abi_owner",
        "gate_id": "HW-ODS-002",
        "owner_status": "open",
        "required_owner": "Android HAL/AIDL/NDK/vendor SDK ABI owner",
    },
    {
        "decision_id": "linux_abi_owner",
        "gate_id": "HW-ODS-003",
        "owner_status": "open",
        "required_owner": "Linux device node/ioctl/sysfs/vendor daemon ABI owner",
    },
    {
        "decision_id": "driver_hal_gap_owner",
        "gate_id": "HW-ODS-004",
        "owner_status": "open",
        "required_owner": "Driver/HAL gap reviewer",
    },
    {
        "decision_id": "safety_policy_owner",
        "gate_id": "HW-ODS-005",
        "owner_status": "open",
        "required_owner": "Safety/Policy binding owner",
    },
    {
        "decision_id": "target_smoke_evidence_owner",
        "gate_id": "HW-ODS-006",
        "owner_status": "open",
        "required_owner": "target hardware smoke evidence owner",
    },
    {
        "decision_id": "rollback_fault_semantics_owner",
        "gate_id": "HW-ODS-007",
        "owner_status": "open",
        "required_owner": "rollback and fault semantics owner",
    },
]

HARDWARE_OWNER_EVIDENCE_REQ_IDS = HARDWARE_OWNER_DECISION_REQ_IDS

HARDWARE_OWNER_EVIDENCE_GATES = [
    {
        "gate_id": "HW-ODE-001",
        "name": "target-interfaces-declared",
        "required_evidence": "Submission identifies which hardware empty interfaces the evidence claims to support.",
        "passed": False,
    },
    {
        "gate_id": "HW-ODE-002",
        "name": "target-gates-declared",
        "required_evidence": "Submission identifies which HW-ODS/HW-ACT/DRV-GAP gates it claims to support.",
        "passed": False,
    },
    {
        "gate_id": "HW-ODE-003",
        "name": "evidence-reference-shape-present",
        "required_evidence": "Submission includes evidence_refs with ref_id, type, uri_or_path, owner, and summary fields.",
        "passed": False,
    },
    {
        "gate_id": "HW-ODE-004",
        "name": "reviewer-identity-present",
        "required_evidence": "Submission includes reviewer identity suitable for Runtime & Governance audit.",
        "passed": False,
    },
    {
        "gate_id": "HW-ODE-005",
        "name": "runtime-governance-policy-checked",
        "required_evidence": "Owner decision evidence submission is policy checked and audited.",
        "passed": False,
    },
    {
        "gate_id": "HW-ODE-006",
        "name": "evidence-store-owner-assigned",
        "required_evidence": "Target platform assigns durable evidence store owner, retention policy, and access control.",
        "passed": False,
    },
    {
        "gate_id": "HW-ODE-007",
        "name": "no-gate-auto-close-claim",
        "required_evidence": "Prototype reports no owner assignment, gate state changes, or activation allowed from evidence intake.",
        "passed": True,
    },
    {
        "gate_id": "HW-ODE-008",
        "name": "android-linux-contract-parity-proven",
        "required_evidence": "REST, Android Binder, Android Console, Linux CLI, Linux IPC, Linux gRPC/RPC, docs, and smoke tests expose equivalent owner evidence intake behavior.",
        "passed": True,
    },
]

HARDWARE_OWNER_EVIDENCE_STATUS_REQ_IDS = HARDWARE_OWNER_EVIDENCE_REQ_IDS

HARDWARE_OWNER_EVIDENCE_STATUS_GATES = [
    {
        "gate_id": "HW-OES-001",
        "name": "evidence-store-owner-assigned",
        "required_evidence": "Target platform assigns durable hardware evidence store owner, retention policy, and access control.",
        "passed": False,
    },
    {
        "gate_id": "HW-OES-002",
        "name": "review-workflow-owner-assigned",
        "required_evidence": "Target platform assigns review queue owner, reviewer identity source, and escalation policy.",
        "passed": False,
    },
    {
        "gate_id": "HW-OES-003",
        "name": "gate-closure-authority-assigned",
        "required_evidence": "Target platform assigns authority for closing HW-ODS/HW-ACT/DRV-GAP gates and rollback semantics.",
        "passed": False,
    },
    {
        "gate_id": "HW-OES-004",
        "name": "target-smoke-evidence-rules-approved",
        "required_evidence": "Target hardware smoke URI rules, hash/version rules, and pass/fail evidence format are approved.",
        "passed": False,
    },
    {
        "gate_id": "HW-OES-005",
        "name": "no-persisted-submissions-claim",
        "required_evidence": "Prototype reports zero persisted owner evidence submissions and no evidence-store read path.",
        "passed": True,
    },
    {
        "gate_id": "HW-OES-006",
        "name": "no-review-queue-or-gate-closure-claim",
        "required_evidence": "Prototype reports no review queue updates, no owner assignment, no gate closure, and no hardware activation permission.",
        "passed": True,
    },
    {
        "gate_id": "HW-OES-007",
        "name": "no-hardware-access-in-status-view",
        "required_evidence": "Status rollup does not open devices, call HAL/vendor SDK, allocate shared memory, or access Safety Runtime.",
        "passed": True,
    },
    {
        "gate_id": "HW-OES-008",
        "name": "android-linux-status-contract-parity-proven",
        "required_evidence": "REST, Android Binder, Android Console, Linux CLI, Linux IPC, Linux gRPC/RPC, docs, and smoke tests expose equivalent owner evidence status behavior.",
        "passed": True,
    },
]

HARDWARE_OWNER_EVIDENCE_RETENTION_REQ_IDS = HARDWARE_OWNER_EVIDENCE_REQ_IDS

HARDWARE_OWNER_EVIDENCE_RETENTION_GATES = [
    {
        "gate_id": "HW-OER-001",
        "name": "durable-evidence-store-owner-assigned",
        "required_evidence": "Target platform assigns durable hardware evidence store owner, process boundary, service identity, and audit backend.",
        "passed": False,
    },
    {
        "gate_id": "HW-OER-002",
        "name": "evidence-uri-rules-approved",
        "required_evidence": "Allowed URI/path schemes, immutability, hash/version, safety redaction, and hardware lab artifact rules are approved.",
        "passed": False,
    },
    {
        "gate_id": "HW-OER-003",
        "name": "retention-policy-owner-assigned",
        "required_evidence": "Retention TTL, cleanup trigger, privacy classification, and hardware evidence retention owner are assigned.",
        "passed": False,
    },
    {
        "gate_id": "HW-OER-004",
        "name": "review-workflow-owner-assigned",
        "required_evidence": "Review queue owner, reviewer identity source, escalation policy, and rejection semantics are assigned.",
        "passed": False,
    },
    {
        "gate_id": "HW-OER-005",
        "name": "gate-closure-authority-assigned",
        "required_evidence": "Gate closure authority, approval signature, rollback behavior, and Runtime & Governance audit binding are approved.",
        "passed": False,
    },
    {
        "gate_id": "HW-OER-006",
        "name": "delete-export-semantics-approved",
        "required_evidence": "Deletion, export, redaction, orphaned reference cleanup, and audit export rules are approved.",
        "passed": False,
    },
    {
        "gate_id": "HW-OER-007",
        "name": "rollback-fault-closure-evidence-defined",
        "required_evidence": "Target platform defines rollback/fault closure evidence for NPU, vehicle bus, sensors, Ethernet, shared memory, and Safety Runtime interfaces.",
        "passed": False,
    },
    {
        "gate_id": "HW-OER-008",
        "name": "android-linux-retention-closure-contract-parity-proven",
        "required_evidence": "REST, Android Binder, Android Console, Linux CLI, Linux IPC, Linux gRPC/RPC, docs, and smoke tests expose equivalent retention/closure checklist fields without activating storage or gates.",
        "passed": True,
    },
]

HARDWARE_OWNER_EVIDENCE_REPLACEMENT_REQ_IDS = HARDWARE_OWNER_EVIDENCE_REQ_IDS

HARDWARE_OWNER_EVIDENCE_REPLACEMENT_GATES = [
    {
        "gate_id": "HW-OET-001",
        "name": "replacement-target-interface-selected",
        "required_evidence": "Target platform selects which empty hardware interface is allowed to leave placeholder state.",
        "passed": False,
    },
    {
        "gate_id": "HW-OET-002",
        "name": "adapter-readiness-criteria-approved",
        "required_evidence": "Native adapter, Model Runtime Adapter, Vehicle Signal Adapter, or protocol adapter readiness criteria are approved.",
        "passed": False,
    },
    {
        "gate_id": "HW-OET-003",
        "name": "driver-hal-gap-closure-evidence-required",
        "required_evidence": "Driver/HAL gap closure evidence is required before replacing the empty-interface contract.",
        "passed": False,
    },
    {
        "gate_id": "HW-OET-004",
        "name": "android-linux-abi-replacement-parity-approved",
        "required_evidence": "Android Binder/AIDL and Linux CLI/IPC/gRPC replacement ABI parity criteria are approved.",
        "passed": False,
    },
    {
        "gate_id": "HW-OET-005",
        "name": "rollback-to-empty-interface-plan-approved",
        "required_evidence": "Rollback plan can restore the contract-only empty-interface path if the real adapter fails.",
        "passed": False,
    },
    {
        "gate_id": "HW-OET-006",
        "name": "safety-policy-replacement-review-required",
        "required_evidence": "Safety Runtime, Runtime & Governance policy, and fault semantics review is required before adapter replacement.",
        "passed": False,
    },
    {
        "gate_id": "HW-OET-007",
        "name": "smoke-harness-replacement-evidence-required",
        "required_evidence": "Target hardware smoke harness evidence is required for the selected interface and rollback path.",
        "passed": False,
    },
    {
        "gate_id": "HW-OET-008",
        "name": "no-auto-replacement-contract-parity-proven",
        "required_evidence": "REST, Android Binder, Android Console, Linux CLI, Linux IPC, Linux gRPC/RPC, docs, and smoke tests expose the replacement trigger checklist without activating any real adapter.",
        "passed": True,
    },
]

HARDWARE_OWNER_EVIDENCE_SELECTED_ADAPTER_REQ_IDS = HARDWARE_OWNER_EVIDENCE_REQ_IDS

HARDWARE_OWNER_EVIDENCE_SELECTED_ADAPTER_GATES = [
    {
        "gate_id": "HW-OEA-001",
        "name": "selected-adapter-owner-assigned",
        "required_evidence": "Target platform assigns the owner for the selected real adapter candidate and its lifecycle boundary.",
        "passed": False,
    },
    {
        "gate_id": "HW-OEA-002",
        "name": "selected-adapter-interface-contract-approved",
        "required_evidence": "Selected adapter method contracts, error model, lifecycle states, and compatibility with the empty-interface contract are approved.",
        "passed": False,
    },
    {
        "gate_id": "HW-OEA-003",
        "name": "selected-adapter-driver-hal-gap-evidence-attached",
        "required_evidence": "Driver/HAL gap closure evidence or target platform waiver is attached for the selected adapter candidate.",
        "passed": False,
    },
    {
        "gate_id": "HW-OEA-004",
        "name": "selected-adapter-android-linux-binding-parity-approved",
        "required_evidence": "Android Binder/AIDL and Linux CLI/IPC/gRPC selected-adapter readiness contracts expose equivalent evidence fields.",
        "passed": False,
    },
    {
        "gate_id": "HW-OEA-005",
        "name": "selected-adapter-safety-policy-fault-model-reviewed",
        "required_evidence": "Runtime & Governance policy, Safety Runtime state, fault model, and safe degraded behavior are reviewed for the selected adapter.",
        "passed": False,
    },
    {
        "gate_id": "HW-OEA-006",
        "name": "selected-adapter-smoke-harness-plan-attached",
        "required_evidence": "Target hardware smoke harness, lab artifact rules, immutable evidence references, and pass/fail criteria are attached.",
        "passed": False,
    },
    {
        "gate_id": "HW-OEA-007",
        "name": "selected-adapter-rollback-to-empty-interface-reviewed",
        "required_evidence": "Rollback plan can disable the selected adapter and restore the previous empty-interface response contract.",
        "passed": False,
    },
    {
        "gate_id": "HW-OEA-008",
        "name": "no-adapter-load-contract-parity-proven",
        "required_evidence": "REST, Android Binder, Android Console, Linux CLI, Linux IPC, Linux gRPC/RPC, docs, and smoke tests expose selected-adapter readiness without loading or activating an adapter.",
        "passed": True,
    },
]

HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_BLOCKER_REQ_IDS = HARDWARE_OWNER_EVIDENCE_REQ_IDS

HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_BLOCKER_GATES = [
    {
        "gate_id": "HW-ALB-001",
        "name": "source-checklists-bound",
        "required_evidence": "Activation checklist, owner decision status, evidence status, retention checklist, replacement trigger checklist, and selected-adapter readiness checklist are all linked before adapter load review.",
        "passed": False,
    },
    {
        "gate_id": "HW-ALB-002",
        "name": "target-owner-and-abi-open",
        "required_evidence": "Target owner, Android ABI owner, Linux ABI owner, and selected adapter owner decisions remain open.",
        "passed": False,
    },
    {
        "gate_id": "HW-ALB-003",
        "name": "evidence-store-review-open",
        "required_evidence": "Durable evidence store, review workflow, review queue, and gate closure authority are not active.",
        "passed": False,
    },
    {
        "gate_id": "HW-ALB-004",
        "name": "replacement-trigger-open",
        "required_evidence": "Empty-interface replacement target, adapter readiness criteria, Driver/HAL gap closure evidence, and rollback plan are not approved.",
        "passed": False,
    },
    {
        "gate_id": "HW-ALB-005",
        "name": "selected-adapter-readiness-open",
        "required_evidence": "Selected adapter candidate, adapter contract, parity evidence, safety fault model, smoke harness, and rollback review are not complete.",
        "passed": False,
    },
    {
        "gate_id": "HW-ALB-006",
        "name": "safety-policy-smoke-rollback-open",
        "required_evidence": "Safety/Policy owner, target hardware smoke evidence, fault semantics, and rollback evidence are still unresolved.",
        "passed": False,
    },
    {
        "gate_id": "HW-ALB-007",
        "name": "android-linux-binding-parity-visible",
        "required_evidence": "Android Binder/AIDL, Android Console, Linux CLI, Linux IPC, and Linux gRPC/RPC expose equivalent no-load blocker rollup fields.",
        "passed": False,
    },
    {
        "gate_id": "HW-ALB-008",
        "name": "no-adapter-load-contract-parity-proven",
        "required_evidence": "REST, Android Binder, Android Console, Linux CLI, Linux IPC, Linux gRPC/RPC, docs, and smoke tests expose the blocker rollup without loading, activating, replacing, or dispatching a real adapter.",
        "passed": True,
    },
]

HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_DRY_RUN_REQ_IDS = HARDWARE_OWNER_EVIDENCE_REQ_IDS

HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_DRY_RUN_GATES = [
    {
        "gate_id": "HW-ALD-001",
        "name": "dry-run-request-shape-valid",
        "required_evidence": "Request declares selected_interface_id, selected_adapter_id, adapter_version, requested_by, and immutable evidence_refs.",
        "passed": False,
    },
    {
        "gate_id": "HW-ALD-002",
        "name": "adapter-load-blocker-rollup-bound",
        "required_evidence": "Dry-run decision is bound to the adapter-load blocker rollup before any adapter load is considered.",
        "passed": True,
    },
    {
        "gate_id": "HW-ALD-003",
        "name": "target-interface-known",
        "required_evidence": "selected_interface_id maps to a registered hardware empty interface.",
        "passed": False,
    },
    {
        "gate_id": "HW-ALD-004",
        "name": "selected-adapter-declared",
        "required_evidence": "selected_adapter_id and adapter_version are declared without loading or probing the adapter.",
        "passed": False,
    },
    {
        "gate_id": "HW-ALD-005",
        "name": "approval-evidence-refs-present",
        "required_evidence": "Evidence refs identify owner approval, Driver/HAL gap review, Safety/Policy review, smoke, and rollback artifacts.",
        "passed": False,
    },
    {
        "gate_id": "HW-ALD-006",
        "name": "runtime-governance-policy-checked",
        "required_evidence": "Runtime & Governance policy/audit context is checked before dry-run rejection is returned.",
        "passed": False,
    },
    {
        "gate_id": "HW-ALD-007",
        "name": "open-blockers-enforced",
        "required_evidence": "Open owner, ABI, evidence-store, replacement, selected-adapter, Safety/Policy, smoke, and rollback blockers reject the request.",
        "passed": True,
    },
    {
        "gate_id": "HW-ALD-008",
        "name": "no-adapter-load-contract-parity-proven",
        "required_evidence": "REST, Android Binder, Android Console, Linux CLI, Linux IPC, Linux gRPC/RPC, docs, and smoke tests expose the dry-run without loading or activating an adapter.",
        "passed": True,
    },
]

HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_DRY_RUN_STATUS_REQ_IDS = HARDWARE_OWNER_EVIDENCE_REQ_IDS

HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_DRY_RUN_STATUS_GATES = [
    {
        "gate_id": "HW-ALS-001",
        "name": "dry-run-status-endpoint-bound",
        "required_evidence": "A read-only status endpoint exists for adapter-load dry-run side-effect inspection.",
        "passed": True,
    },
    {
        "gate_id": "HW-ALS-002",
        "name": "no-persisted-dry-run-records",
        "required_evidence": "Prototype reports zero persisted dry-run requests and no durable last-result storage.",
        "passed": True,
    },
    {
        "gate_id": "HW-ALS-003",
        "name": "no-review-queue-or-evidence-store",
        "required_evidence": "Status view reports no evidence store, review workflow, or review queue side effects.",
        "passed": True,
    },
    {
        "gate_id": "HW-ALS-004",
        "name": "last-result-not-stored",
        "required_evidence": "Status view returns the last-result shape only and confirms no persisted last result is available.",
        "passed": True,
    },
    {
        "gate_id": "HW-ALS-005",
        "name": "adapter-load-blocker-rollup-still-open",
        "required_evidence": "Status view remains bound to the adapter-load blocker rollup and reports blockers still open.",
        "passed": True,
    },
    {
        "gate_id": "HW-ALS-006",
        "name": "android-linux-status-contract-parity-proven",
        "required_evidence": "REST, Android Binder, Android Console, Linux CLI, Linux IPC, Linux gRPC/RPC, docs, and smoke tests expose equivalent dry-run status behavior.",
        "passed": True,
    },
    {
        "gate_id": "HW-ALS-007",
        "name": "no-hardware-access-in-status-view",
        "required_evidence": "Status view does not open devices, call HAL/vendor SDK, allocate shared memory, or access Safety Runtime.",
        "passed": True,
    },
    {
        "gate_id": "HW-ALS-008",
        "name": "no-driver-or-virtualization-trigger",
        "required_evidence": "Status view does not trigger Driver/HAL, Safety Runtime, vehicle bus, service dispatch, or virtualization work.",
        "passed": True,
    },
]


class HardwareInterfaceRegistry:
    """Read-only registry for hardware-dependent empty interfaces."""

    def summary_payload(self) -> dict[str, Any]:
        interfaces = copy.deepcopy(EMPTY_INTERFACE_REGISTRY)
        return {
            "interface_count": len(interfaces),
            "interfaces": [item["interface_id"] for item in interfaces],
            "implementation_state": "empty-interface-registry",
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
            "req_ids": HARDWARE_REQ_IDS,
        }

    def activation_checklist_payload(self) -> dict[str, Any]:
        interfaces = copy.deepcopy(EMPTY_INTERFACE_REGISTRY)
        gate_ids = [gate["gate_id"] for gate in HARDWARE_ACTIVATION_GATES]
        per_interface = []
        for item in interfaces:
            per_interface.append(
                {
                    "interface_id": item["interface_id"],
                    "name": item["name"],
                    "implementation_state": item["implementation_state"],
                    "activation_state": "blocked-pending-target-platform-evidence",
                    "activation_trigger": item["activation_trigger"],
                    "driver_gap_ids": item["driver_gap_ids"],
                    "required_owner_decisions": [
                        "target_interface_owner",
                        "android_abi_owner",
                        "linux_abi_owner",
                        "driver_hal_gap_owner",
                        "safety_policy_owner",
                        "test_harness_owner",
                    ],
                    "required_gate_ids": gate_ids,
                    "android_primary_path": item["android_primary_path"],
                    "linux_sync_path": item["linux_sync_path"],
                    "activation_allowed": False,
                    "hardware_accessed": False,
                    "driver_development_triggered": False,
                    "virtualization_development_triggered": False,
                }
            )

        return {
            "activation_checklist_state": "contract-only-no-hardware-activation",
            "activation_allowed": False,
            "interfaces": per_interface,
            "mandatory_gates": copy.deepcopy(HARDWARE_ACTIVATION_GATES),
            "owner_decision_shape": {
                "required_fields": [
                    "interface_id",
                    "target_owner",
                    "android_abi_owner",
                    "linux_abi_owner",
                    "driver_gap_owner",
                    "safety_policy_owner",
                    "test_harness_owner",
                    "evidence_refs",
                ],
                "owner_decision_complete": False,
            },
            "test_evidence_shape": {
                "prototype_smoke_required": [
                    "GET /hardware/interfaces",
                    "GET /hardware/interfaces/activation-checklist",
                    "Android Binder getHardwareInterfaceActivationChecklistJson",
                    "Linux CLI hardware-interface-activation-checklist",
                    "Linux IPC hardware.interfaces.activation.checklist",
                    "Linux gRPC/RPC GetHardwareInterfaceActivationChecklist",
                ],
                "target_hardware_smoke_required_before_activation": [
                    "device-discovery-without-root-bypass",
                    "permission-denied-negative-test",
                    "fault-timeout-reset-test",
                    "audit-trace-export-test",
                    "android-linux-contract-parity-test",
                ],
                "target_hardware_smoke_attached": False,
            },
            "api_surface": {
                "rest": "GET /hardware/interfaces/activation-checklist",
                "android_binder": "getHardwareInterfaceActivationChecklistJson",
                "linux_cli": "hardware-interface-activation-checklist",
                "linux_ipc": "hardware.interfaces.activation.checklist",
                "linux_grpc_rpc": "CentralBrainGateway.GetHardwareInterfaceActivationChecklist",
            },
            "summary": {
                "hardware_activation_checklist_active": True,
                "owner_decision_complete": False,
                "android_abi_confirmed": False,
                "linux_abi_confirmed": False,
                "driver_gap_review_complete": False,
                "safety_policy_binding_confirmed": False,
                "target_hardware_smoke_attached": False,
                "activation_allowed": False,
                "hardware_accessed": False,
                "driver_development_triggered": False,
                "virtualization_development_triggered": False,
                "service_dispatch_triggered": False,
            },
            "req_ids": HARDWARE_ACTIVATION_REQ_IDS,
        }

    def owner_decision_status_payload(self) -> dict[str, Any]:
        interfaces = copy.deepcopy(EMPTY_INTERFACE_REGISTRY)
        open_gate_ids = [gate["gate_id"] for gate in HARDWARE_OWNER_DECISION_GATES if not gate["passed"]]
        per_interface = []
        for item in interfaces:
            per_interface.append(
                {
                    "interface_id": item["interface_id"],
                    "name": item["name"],
                    "implementation_state": item["implementation_state"],
                    "decision_state": "blocked-owner-decisions-open",
                    "activation_trigger": item["activation_trigger"],
                    "driver_gap_ids": item["driver_gap_ids"],
                    "owner_decisions": copy.deepcopy(HARDWARE_OWNER_DECISION_ITEMS),
                    "open_gate_ids": open_gate_ids,
                    "android_primary_path": item["android_primary_path"],
                    "linux_sync_path": item["linux_sync_path"],
                    "activation_allowed": False,
                    "hardware_accessed": False,
                    "driver_development_triggered": False,
                    "virtualization_development_triggered": False,
                }
            )

        return {
            "owner_decision_status_state": "contract-only-owner-decisions-open",
            "activation_allowed": False,
            "interfaces": per_interface,
            "decision_gates": copy.deepcopy(HARDWARE_OWNER_DECISION_GATES),
            "rollup": {
                "open_decision_count": len(HARDWARE_OWNER_DECISION_ITEMS) * len(interfaces),
                "open_gate_ids": open_gate_ids,
                "blocked_interface_count": len(interfaces),
                "blocking_sources": [
                    "target_interface_owner",
                    "android_abi_owner",
                    "linux_abi_owner",
                    "driver_hal_gap_owner",
                    "safety_policy_owner",
                    "target_smoke_evidence_owner",
                    "rollback_fault_semantics_owner",
                ],
            },
            "api_surface": {
                "rest": "GET /hardware/interfaces/owner-decision-status",
                "android_binder": "getHardwareInterfaceOwnerDecisionStatusJson",
                "linux_cli": "hardware-interface-owner-decision-status",
                "linux_ipc": "hardware.interfaces.owner.decision.status",
                "linux_grpc_rpc": "CentralBrainGateway.GetHardwareInterfaceOwnerDecisionStatus",
            },
            "summary": {
                "owner_decision_status_active": True,
                "all_required_owners_assigned": False,
                "target_interface_owner_assigned": False,
                "android_abi_owner_assigned": False,
                "linux_abi_owner_assigned": False,
                "driver_gap_owner_assigned": False,
                "safety_policy_owner_assigned": False,
                "target_hardware_smoke_attached": False,
                "rollback_fault_semantics_confirmed": False,
                "activation_allowed": False,
                "hardware_accessed": False,
                "driver_development_triggered": False,
                "virtualization_development_triggered": False,
                "service_dispatch_triggered": False,
            },
            "req_ids": HARDWARE_OWNER_DECISION_REQ_IDS,
        }

    def owner_decision_evidence_payload(self, request: dict[str, Any], policy: dict[str, Any]) -> dict[str, Any]:
        interface_ids = {item["interface_id"] for item in EMPTY_INTERFACE_REGISTRY}
        raw_interface_ids = request.get("target_interface_ids") or request.get("interface_ids") or []
        target_interface_ids = raw_interface_ids if isinstance(raw_interface_ids, list) else [str(raw_interface_ids)]
        raw_gate_ids = request.get("target_gate_ids") or request.get("gate_ids") or []
        target_gate_ids = raw_gate_ids if isinstance(raw_gate_ids, list) else [str(raw_gate_ids)]
        raw_evidence_refs = request.get("evidence_refs") or request.get("attachments") or []
        evidence_refs = raw_evidence_refs if isinstance(raw_evidence_refs, list) else [raw_evidence_refs]
        raw_reviewer = request.get("reviewer") or {"app_id": "unknown", "role": "contract-reviewer"}
        reviewer = raw_reviewer if isinstance(raw_reviewer, dict) else {"name": str(raw_reviewer), "role": "contract-reviewer"}
        submission_id = str(request.get("evidence_submission_id") or f"hw-ode-{uuid.uuid4()}")
        unknown_interface_ids = [item for item in target_interface_ids if item not in interface_ids]
        required_ref_fields = ["ref_id", "type", "uri_or_path", "owner", "summary"]
        invalid_evidence_ref_indexes = [
            index
            for index, evidence_ref in enumerate(evidence_refs)
            if not isinstance(evidence_ref, dict) or not all(evidence_ref.get(field) for field in required_ref_fields)
        ]
        allowed = policy["decision"] == "allow"
        has_target_interfaces = bool(target_interface_ids) and not unknown_interface_ids
        has_target_gates = bool(target_gate_ids)
        has_evidence_refs = bool(evidence_refs) and not invalid_evidence_ref_indexes
        has_reviewer = bool(reviewer.get("app_id") or reviewer.get("name") or reviewer.get("role"))

        if not allowed:
            state = "rejected_by_policy"
        elif not has_target_interfaces or not has_target_gates or not has_evidence_refs or not has_reviewer:
            state = "rejected_missing_evidence"
        else:
            state = "validated_contract_only"

        mandatory_gates = copy.deepcopy(HARDWARE_OWNER_EVIDENCE_GATES)
        gate_passes = {
            "HW-ODE-001": has_target_interfaces,
            "HW-ODE-002": has_target_gates,
            "HW-ODE-003": has_evidence_refs,
            "HW-ODE-004": has_reviewer,
            "HW-ODE-005": allowed,
        }
        for gate in mandatory_gates:
            if gate["gate_id"] in gate_passes:
                gate["passed"] = gate_passes[gate["gate_id"]]

        return {
            "operation": "hardware-owner-decision-evidence",
            "evidence_submission_id": submission_id,
            "evidence_intake_state": state,
            "intake_validated": state == "validated_contract_only",
            "target_interface_ids": target_interface_ids,
            "unknown_interface_ids": unknown_interface_ids,
            "target_gate_ids": target_gate_ids,
            "evidence_refs": evidence_refs,
            "invalid_evidence_ref_indexes": invalid_evidence_ref_indexes,
            "reviewer": reviewer,
            "evidence_contract": {
                "required_gate_prefixes": ["HW-ODS", "HW-ACT", "DRV-GAP"],
                "accepted_ref_types": ["doc", "test_log", "owner_approval", "platform_decision", "driver_gap_review", "safety_review"],
                "required_ref_fields": required_ref_fields,
                "storage_owner": "TBD-target-platform",
                "review_owner": "TBD-target-platform",
                "prototype_storage": "not implemented; request is validated and discarded after response",
            },
            "validation": {
                "policy_checked": True,
                "policy": policy,
                "target_interfaces_present": has_target_interfaces,
                "target_gates_present": has_target_gates,
                "evidence_refs_present": has_evidence_refs,
                "evidence_refs_shape_valid": has_evidence_refs,
                "reviewer_present": has_reviewer,
                "audit_recorded": True,
            },
            "review_result": {
                "accepted_for_review": False,
                "review_queue_updated": False,
                "evidence_persisted": False,
                "owner_assigned": False,
                "gate_state_changed": False,
                "gates_closed": False,
                "activation_allowed": False,
                "reason": "prototype exposes hardware owner evidence intake contract only; no evidence store, owner assignment, or review workflow is implemented",
            },
            "mandatory_gates": mandatory_gates,
            "api_surface": {
                "rest": "POST /hardware/interfaces/owner-decision-evidence",
                "android_binder": "submitHardwareInterfaceOwnerDecisionEvidenceJson",
                "linux_cli": "hardware-interface-owner-decision-evidence",
                "linux_ipc": "hardware.interfaces.owner.decision.evidence",
                "linux_grpc_rpc": "CentralBrainGateway.SubmitHardwareInterfaceOwnerDecisionEvidence",
            },
            "summary": {
                "owner_decision_evidence_contract_active": True,
                "owner_decision_evidence_validated": state == "validated_contract_only",
                "owner_decision_evidence_accepted_for_review": False,
                "owner_decision_evidence_persisted": False,
                "review_queue_updated": False,
                "owner_assigned": False,
                "gate_state_changed": False,
                "gates_closed": False,
                "activation_allowed": False,
                "hardware_accessed": False,
                "driver_development_triggered": False,
                "virtualization_development_triggered": False,
                "service_dispatch_triggered": False,
            },
            "req_ids": HARDWARE_OWNER_EVIDENCE_REQ_IDS,
        }

    def owner_decision_evidence_status_payload(self) -> dict[str, Any]:
        return {
            "owner_decision_evidence_status_state": "contract-only-no-evidence-store",
            "status_scope": {
                "source_endpoint": "POST /hardware/interfaces/owner-decision-evidence",
                "lookup_mode": "prototype-static-status",
                "prototype_storage": "not implemented; evidence submissions are validated and discarded after response",
                "target_gate_scope": ["HW-ODS", "HW-ACT", "HW-ODE", "DRV-GAP"],
            },
            "owners": {
                "evidence_store_owner": "TBD-target-platform",
                "review_workflow_owner": "TBD-target-platform",
                "gate_closure_authority": "TBD-target-platform",
                "target_smoke_evidence_owner": "TBD-target-platform",
                "rollback_fault_semantics_owner": "TBD-target-platform",
            },
            "review_pipeline": {
                "evidence_store_active": False,
                "review_workflow_active": False,
                "review_queue_updated": False,
                "owner_assigned": False,
                "gate_state_changed": False,
                "gates_closed": False,
                "activation_allowed": False,
                "reason": "hardware owner evidence intake is contract-only; this status endpoint exposes that no durable store, owner assignment, or review workflow exists yet",
            },
            "counters": {
                "persisted_submission_count": 0,
                "pending_review_count": 0,
                "accepted_for_review_count": 0,
                "reviewed_submission_count": 0,
                "assigned_owner_count": 0,
                "closed_gate_count": 0,
            },
            "remaining_decisions": [
                {
                    "decision_id": "durable_evidence_store_owner",
                    "current_selection": "TBD-target-platform",
                    "blocked_by": ["storage location", "service identity", "retention policy", "audit export backend"],
                },
                {
                    "decision_id": "review_workflow_owner",
                    "current_selection": "TBD-target-platform",
                    "blocked_by": ["review queue backend", "reviewer identity source", "escalation SLA", "rejection semantics"],
                },
                {
                    "decision_id": "gate_closure_authority",
                    "current_selection": "TBD-target-platform",
                    "blocked_by": ["gate owner", "approval signature", "rollback rule", "Runtime & Governance binding"],
                },
                {
                    "decision_id": "target_smoke_evidence_rules",
                    "current_selection": "TBD-target-platform",
                    "blocked_by": ["allowed URI schemes", "hash/version rule", "pass/fail evidence format", "hardware lab owner"],
                },
            ],
            "mandatory_gates": copy.deepcopy(HARDWARE_OWNER_EVIDENCE_STATUS_GATES),
            "api_surface": {
                "rest": "GET /hardware/interfaces/owner-decision-evidence/status",
                "android_binder": "getHardwareInterfaceOwnerDecisionEvidenceStatusJson",
                "linux_cli": "hardware-interface-owner-decision-evidence-status",
                "linux_ipc": "hardware.interfaces.owner.decision.evidence.status",
                "linux_grpc_rpc": "CentralBrainGateway.GetHardwareInterfaceOwnerDecisionEvidenceStatus",
            },
            "summary": {
                "owner_decision_evidence_status_contract_active": True,
                "review_status_available": True,
                "owner_decision_evidence_contract_active": True,
                "owner_decision_evidence_accepted_for_review": False,
                "owner_decision_evidence_persisted": False,
                "evidence_store_active": False,
                "review_workflow_active": False,
                "persisted_submission_count": 0,
                "pending_review_count": 0,
                "accepted_for_review_count": 0,
                "reviewed_submission_count": 0,
                "assigned_owner_count": 0,
                "closed_gate_count": 0,
                "review_queue_updated": False,
                "owner_assigned": False,
                "gate_state_changed": False,
                "gates_closed": False,
                "activation_allowed": False,
                "hardware_accessed": False,
                "driver_development_triggered": False,
                "virtualization_development_triggered": False,
                "service_dispatch_triggered": False,
            },
            "req_ids": HARDWARE_OWNER_EVIDENCE_STATUS_REQ_IDS,
        }

    def owner_decision_evidence_retention_checklist_payload(self) -> dict[str, Any]:
        return {
            "retention_closure_checklist_state": "contract-only-retention-closure-checklist-open",
            "storage_activation_allowed": False,
            "gate_closure_allowed": False,
            "owner_decision_complete": False,
            "scope": {
                "source_endpoints": [
                    "POST /hardware/interfaces/owner-decision-evidence",
                    "GET /hardware/interfaces/owner-decision-evidence/status",
                ],
                "target_endpoint": "GET /hardware/interfaces/owner-decision-evidence/retention-checklist",
                "purpose": "fix retention, URI, review-owner, gate-closure, approval signature, delete/export, and rollback/fault decisions before any durable hardware evidence store or gate closure workflow is considered",
                "prototype_storage": "not implemented; this checklist does not persist, dereference, delete, export, review, or close hardware evidence",
                "target_gate_scope": ["HW-ODS", "HW-ACT", "HW-ODE", "HW-OES", "DRV-GAP"],
            },
            "owner_decisions": [
                {
                    "decision_id": "HW-OER-001",
                    "area": "durable-evidence-store-owner",
                    "required_decision": "Assign the Android/Linux process and data owner for durable hardware owner evidence records.",
                    "current_selection": "TBD-target-platform",
                    "blocked_by": ["target storage location", "service identity", "audit export backend", "backup/restore policy"],
                },
                {
                    "decision_id": "HW-OER-002",
                    "area": "evidence-uri-rules",
                    "required_decision": "Approve allowed URI/path forms for hardware smoke logs, owner approvals, platform decisions, driver gap reviews, and safety reviews.",
                    "current_selection": "TBD-target-platform",
                    "blocked_by": ["allowed schemes", "artifact immutability", "hardware lab artifact root", "secret and safety-fault redaction rule"],
                },
                {
                    "decision_id": "HW-OER-003",
                    "area": "retention-policy-owner",
                    "required_decision": "Assign retention TTL, privacy classification, cleanup, and audit retention owner for hardware evidence.",
                    "current_selection": "TBD-target-platform",
                    "blocked_by": ["retention duration", "privacy review", "cleanup trigger", "regulatory export need"],
                },
                {
                    "decision_id": "HW-OER-004",
                    "area": "review-workflow-owner",
                    "required_decision": "Assign review queue owner, reviewer roles, escalation policy, and rejection semantics.",
                    "current_selection": "TBD-target-platform",
                    "blocked_by": ["review queue backend", "reviewer identity source", "escalation SLA", "audit trail owner"],
                },
                {
                    "decision_id": "HW-OER-005",
                    "area": "gate-closure-authority",
                    "required_decision": "Assign who can close HW-ODS/HW-ACT/HW-ODE/HW-OES/DRV-GAP gates and how closure is audited and rolled back.",
                    "current_selection": "TBD-target-platform",
                    "blocked_by": ["gate owner", "approval signature", "rollback rule", "Runtime & Governance binding"],
                },
                {
                    "decision_id": "HW-OER-006",
                    "area": "delete-export-semantics",
                    "required_decision": "Approve hardware evidence deletion, export, redaction, and orphaned reference behavior.",
                    "current_selection": "TBD-target-platform",
                    "blocked_by": ["delete authorization", "export format", "redaction policy", "orphaned ref cleanup"],
                },
                {
                    "decision_id": "HW-OER-007",
                    "area": "rollback-fault-closure-evidence",
                    "required_decision": "Define minimum rollback, fault, timeout, reset, and safety degradation evidence required before closing hardware gates.",
                    "current_selection": "TBD-target-platform",
                    "blocked_by": ["NPU fault semantics", "vehicle bus fallback", "Safety Runtime owner", "target hardware smoke harness"],
                },
            ],
            "evidence_uri_rules": {
                "allowed_ref_types": [
                    "doc",
                    "test_log",
                    "owner_approval",
                    "platform_decision",
                    "driver_gap_review",
                    "safety_review",
                    "hardware_smoke_log",
                ],
                "required_fields": [
                    "ref_id",
                    "type",
                    "uri_or_path",
                    "owner",
                    "summary",
                    "created_at",
                    "hash_or_version",
                    "target_interface_ids",
                    "target_gate_ids",
                ],
                "candidate_allowed_uri_schemes": ["repo-relative", "artifact-store", "audit-log", "platform-decision", "hardware-lab-result"],
                "disallowed_until_policy_exists": [
                    "raw cloud URL without privacy route",
                    "mutable temp file",
                    "secret-bearing path",
                    "device node or hardware probe output captured outside Driver/HAL gap review",
                    "unredacted safety fault dump",
                ],
                "uri_rules_confirmed": False,
            },
            "retention_policy_shape": {
                "candidate_retention_classes": [
                    "hardware-owner-evidence",
                    "driver-gap-review-record",
                    "target-hardware-smoke-record",
                    "rollback-fault-record",
                ],
                "minimum_metadata": [
                    "owner",
                    "reviewer",
                    "target_interface_ids",
                    "target_gate_ids",
                    "created_at",
                    "retention_class",
                    "redaction_state",
                    "hardware_lab_trace_id",
                ],
                "delete_semantics": "TBD-target-platform; prototype does not delete anything because it stores nothing",
                "export_semantics": "TBD-target-platform; prototype does not export anything because it stores nothing",
                "retention_policy_confirmed": False,
            },
            "closure_policy_shape": {
                "target_gate_families": ["HW-ODS", "HW-ACT", "HW-ODE", "HW-OES", "DRV-GAP"],
                "minimum_closure_inputs": [
                    "evidence_submission_id",
                    "owner_signature",
                    "reviewer_identity",
                    "target_smoke_result",
                    "rollback_plan",
                    "fault_semantics",
                    "Runtime & Governance audit reference",
                ],
                "approval_signature_required": True,
                "rollback_fault_closure_confirmed": False,
                "gate_closure_authority_confirmed": False,
                "gate_closure_allowed": False,
            },
            "mandatory_gates": copy.deepcopy(HARDWARE_OWNER_EVIDENCE_RETENTION_GATES),
            "api_surface": {
                "rest": "GET /hardware/interfaces/owner-decision-evidence/retention-checklist",
                "android_binder": "getHardwareInterfaceOwnerDecisionEvidenceRetentionChecklistJson",
                "linux_cli": "hardware-interface-owner-decision-evidence-retention-checklist",
                "linux_ipc": "hardware.interfaces.owner.decision.evidence.retention.checklist",
                "linux_grpc_rpc": "CentralBrainGateway.GetHardwareInterfaceOwnerDecisionEvidenceRetentionChecklist",
            },
            "summary": {
                "owner_decision_evidence_retention_checklist_active": True,
                "owner_decision_complete": False,
                "retention_policy_confirmed": False,
                "evidence_uri_rules_confirmed": False,
                "review_workflow_owner_confirmed": False,
                "gate_closure_authority_confirmed": False,
                "deletion_export_semantics_confirmed": False,
                "rollback_fault_closure_confirmed": False,
                "approval_signature_confirmed": False,
                "owner_decision_evidence_status_contract_active": True,
                "owner_decision_evidence_contract_active": True,
                "evidence_store_active": False,
                "review_workflow_active": False,
                "delete_workflow_active": False,
                "export_workflow_active": False,
                "persisted_submission_count": 0,
                "pending_review_count": 0,
                "review_queue_updated": False,
                "owner_assigned": False,
                "gate_state_changed": False,
                "gates_closed": False,
                "activation_allowed": False,
                "storage_activation_allowed": False,
                "gate_closure_allowed": False,
                "hardware_accessed": False,
                "driver_development_triggered": False,
                "virtualization_development_triggered": False,
                "service_dispatch_triggered": False,
            },
            "req_ids": HARDWARE_OWNER_EVIDENCE_RETENTION_REQ_IDS,
        }

    def owner_decision_evidence_replacement_trigger_checklist_payload(self) -> dict[str, Any]:
        replacement_targets = [
            {
                "interface_id": item["interface_id"],
                "current_state": item["implementation_state"],
                "replacement_candidate": item["interface_id"] in {"npu-runtime", "vehicle-bus", "camera-audio-sensors", "ethernet-protocols", "shared-memory-safety-runtime"},
                "replacement_trigger": "requires target owner, Android/Linux ABI owner, Driver/HAL gap closure evidence, Safety/Policy review, smoke harness evidence, and rollback-to-empty-interface plan",
                "replacement_allowed": False,
                "adapter_activation_allowed": False,
                "driver_hal_development_triggered": False,
            }
            for item in EMPTY_INTERFACE_REGISTRY
        ]
        return {
            "replacement_trigger_checklist_state": "contract-only-replacement-trigger-checklist-open",
            "replacement_allowed": False,
            "adapter_activation_allowed": False,
            "gate_closure_allowed": False,
            "owner_decision_complete": False,
            "scope": {
                "source_endpoints": [
                    "GET /hardware/interfaces",
                    "GET /hardware/interfaces/activation-checklist",
                    "GET /hardware/interfaces/owner-decision-status",
                    "POST /hardware/interfaces/owner-decision-evidence",
                    "GET /hardware/interfaces/owner-decision-evidence/status",
                    "GET /hardware/interfaces/owner-decision-evidence/retention-checklist",
                ],
                "target_endpoint": "GET /hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist",
                "purpose": "make the future trigger for replacing empty hardware interfaces with real adapters explicit before any Driver/HAL, vendor SDK, Safety Runtime, vehicle bus, shared memory, or NPU hardware integration starts",
                "prototype_replacement": "not implemented; this checklist does not replace an interface, load an adapter, open hardware, close a gate, or start Driver/HAL work",
                "target_gate_scope": ["HW-ODS", "HW-ACT", "HW-ODE", "HW-OES", "HW-OER", "DRV-GAP"],
            },
            "replacement_targets": replacement_targets,
            "replacement_policy_shape": {
                "required_trigger_inputs": [
                    "selected_interface_id",
                    "target_owner",
                    "android_abi_owner",
                    "linux_abi_owner",
                    "driver_hal_gap_closure_evidence",
                    "safety_policy_review",
                    "target_hardware_smoke_result",
                    "rollback_to_empty_interface_plan",
                    "Runtime & Governance audit reference",
                ],
                "disallowed_trigger_inputs": [
                    "raw device node probe",
                    "unreviewed vendor SDK call",
                    "platform-specific ABI without Linux parity",
                    "hardware lab artifact without immutable evidence reference",
                    "gate closure request without rollback plan",
                ],
                "replacement_policy_confirmed": False,
                "adapter_readiness_criteria_confirmed": False,
                "driver_hal_gap_closure_evidence_confirmed": False,
                "android_linux_abi_replacement_parity_confirmed": False,
                "safety_policy_replacement_review_confirmed": False,
                "smoke_harness_replacement_evidence_confirmed": False,
            },
            "rollback_policy_shape": {
                "rollback_to_empty_interface_required": True,
                "minimum_rollback_inputs": [
                    "previous_empty_interface_contract_version",
                    "adapter_disable_switch",
                    "fault_semantics",
                    "safe degraded response",
                    "audit evidence reference",
                ],
                "rollback_to_empty_interface_plan_confirmed": False,
                "gate_closure_allowed": False,
            },
            "mandatory_gates": copy.deepcopy(HARDWARE_OWNER_EVIDENCE_REPLACEMENT_GATES),
            "api_surface": {
                "rest": "GET /hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist",
                "android_binder": "getHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklistJson",
                "linux_cli": "hardware-interface-owner-decision-evidence-replacement-trigger-checklist",
                "linux_ipc": "hardware.interfaces.owner.decision.evidence.replacement.trigger.checklist",
                "linux_grpc_rpc": "CentralBrainGateway.GetHardwareInterfaceOwnerDecisionEvidenceReplacementTriggerChecklist",
            },
            "summary": {
                "owner_decision_evidence_replacement_trigger_checklist_active": True,
                "owner_decision_complete": False,
                "replacement_policy_confirmed": False,
                "replacement_target_selected": False,
                "adapter_readiness_criteria_confirmed": False,
                "driver_hal_gap_closure_evidence_confirmed": False,
                "android_linux_abi_replacement_parity_confirmed": False,
                "rollback_to_empty_interface_plan_confirmed": False,
                "safety_policy_replacement_review_confirmed": False,
                "smoke_harness_replacement_evidence_confirmed": False,
                "owner_decision_evidence_retention_checklist_active": True,
                "owner_decision_evidence_status_contract_active": True,
                "owner_decision_evidence_contract_active": True,
                "evidence_store_active": False,
                "review_workflow_active": False,
                "delete_workflow_active": False,
                "export_workflow_active": False,
                "review_queue_updated": False,
                "owner_assigned": False,
                "gate_state_changed": False,
                "gates_closed": False,
                "activation_allowed": False,
                "replacement_allowed": False,
                "adapter_activation_allowed": False,
                "gate_closure_allowed": False,
                "hardware_accessed": False,
                "driver_development_triggered": False,
                "virtualization_development_triggered": False,
                "service_dispatch_triggered": False,
            },
            "req_ids": HARDWARE_OWNER_EVIDENCE_REPLACEMENT_REQ_IDS,
        }

    def owner_decision_evidence_selected_adapter_readiness_checklist_payload(self) -> dict[str, Any]:
        selected_adapter_candidates = [
            {
                "interface_id": item["interface_id"],
                "interface_name": item["name"],
                "current_state": item["implementation_state"],
                "selected_adapter_id": "TBD-target-platform-adapter",
                "adapter_candidate_recorded": False,
                "adapter_owner_assigned": False,
                "adapter_interface_contract_approved": False,
                "driver_hal_gap_evidence_attached": False,
                "android_linux_binding_parity_approved": False,
                "safety_policy_fault_model_reviewed": False,
                "smoke_harness_plan_attached": False,
                "rollback_to_empty_interface_reviewed": False,
                "adapter_load_allowed": False,
                "adapter_activation_allowed": False,
                "hardware_access_allowed": False,
                "driver_hal_development_triggered": False,
                "required_reserved_methods": [method["name"] for method in item["reserved_methods"]],
                "driver_gap_ids": item["driver_gap_ids"],
            }
            for item in EMPTY_INTERFACE_REGISTRY
        ]
        return {
            "selected_adapter_readiness_checklist_state": "contract-only-selected-adapter-readiness-checklist-open",
            "adapter_candidate_recorded": False,
            "adapter_load_allowed": False,
            "adapter_activation_allowed": False,
            "hardware_access_allowed": False,
            "gate_closure_allowed": False,
            "owner_decision_complete": False,
            "scope": {
                "source_endpoints": [
                    "GET /hardware/interfaces",
                    "GET /hardware/interfaces/activation-checklist",
                    "GET /hardware/interfaces/owner-decision-status",
                    "POST /hardware/interfaces/owner-decision-evidence",
                    "GET /hardware/interfaces/owner-decision-evidence/status",
                    "GET /hardware/interfaces/owner-decision-evidence/retention-checklist",
                    "GET /hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist",
                ],
                "target_endpoint": "GET /hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist",
                "purpose": "make the evidence required after a target platform proposes a real adapter candidate explicit before any adapter is loaded, activated, or allowed to replace an empty hardware interface",
                "prototype_adapter_selection": "not implemented; this checklist does not select, load, activate, smoke, or dispatch a real adapter",
                "target_gate_scope": ["HW-OET", "HW-OEA", "HW-ODS", "HW-ACT", "DRV-GAP"],
            },
            "selected_adapter_candidates": selected_adapter_candidates,
            "adapter_evidence_shape": {
                "required_evidence_refs": [
                    "adapter_owner_record",
                    "adapter_interface_contract",
                    "driver_hal_gap_closure_or_waiver",
                    "android_binder_aidl_parity_record",
                    "linux_cli_ipc_grpc_parity_record",
                    "safety_policy_fault_model_review",
                    "target_hardware_smoke_harness_plan",
                    "rollback_to_empty_interface_plan",
                    "Runtime & Governance audit reference",
                ],
                "adapter_owner_assigned": False,
                "adapter_interface_contract_approved": False,
                "driver_hal_gap_evidence_attached": False,
                "android_linux_binding_parity_approved": False,
                "safety_policy_fault_model_reviewed": False,
                "smoke_harness_plan_attached": False,
                "rollback_to_empty_interface_reviewed": False,
            },
            "adapter_load_policy_shape": {
                "load_policy_confirmed": False,
                "allowed_load_inputs": [
                    "selected_interface_id",
                    "selected_adapter_id",
                    "adapter_version",
                    "signed_owner_decision",
                    "evidence_refs",
                    "rollback_switch",
                ],
                "disallowed_load_inputs": [
                    "direct device node probe",
                    "unreviewed vendor SDK init",
                    "adapter activation without Linux parity evidence",
                    "gate closure without rollback evidence",
                    "hardware smoke result without immutable evidence reference",
                ],
                "adapter_load_allowed": False,
                "adapter_activation_allowed": False,
                "hardware_access_allowed": False,
                "gate_closure_allowed": False,
            },
            "mandatory_gates": copy.deepcopy(HARDWARE_OWNER_EVIDENCE_SELECTED_ADAPTER_GATES),
            "api_surface": {
                "rest": "GET /hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist",
                "android_binder": "getHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklistJson",
                "linux_cli": "hardware-interface-owner-decision-evidence-selected-adapter-readiness-checklist",
                "linux_ipc": "hardware.interfaces.owner.decision.evidence.selected.adapter.readiness.checklist",
                "linux_grpc_rpc": "CentralBrainGateway.GetHardwareInterfaceOwnerDecisionEvidenceSelectedAdapterReadinessChecklist",
            },
            "summary": {
                "owner_decision_evidence_selected_adapter_readiness_checklist_active": True,
                "owner_decision_evidence_replacement_trigger_checklist_active": True,
                "owner_decision_evidence_retention_checklist_active": True,
                "owner_decision_evidence_status_contract_active": True,
                "owner_decision_evidence_contract_active": True,
                "owner_decision_complete": False,
                "adapter_candidate_recorded": False,
                "adapter_owner_assigned": False,
                "adapter_interface_contract_approved": False,
                "driver_hal_gap_evidence_attached": False,
                "android_linux_binding_parity_approved": False,
                "safety_policy_fault_model_reviewed": False,
                "smoke_harness_plan_attached": False,
                "rollback_to_empty_interface_reviewed": False,
                "load_policy_confirmed": False,
                "evidence_store_active": False,
                "review_workflow_active": False,
                "review_queue_updated": False,
                "owner_assigned": False,
                "gate_state_changed": False,
                "gates_closed": False,
                "activation_allowed": False,
                "replacement_allowed": False,
                "adapter_load_allowed": False,
                "adapter_activation_allowed": False,
                "hardware_access_allowed": False,
                "gate_closure_allowed": False,
                "hardware_accessed": False,
                "driver_development_triggered": False,
                "virtualization_development_triggered": False,
                "service_dispatch_triggered": False,
            },
            "req_ids": HARDWARE_OWNER_EVIDENCE_SELECTED_ADAPTER_REQ_IDS,
        }

    def owner_decision_evidence_adapter_load_blocker_rollup_payload(self) -> dict[str, Any]:
        per_interface_blockers = [
            {
                "interface_id": item["interface_id"],
                "interface_name": item["name"],
                "current_state": item["implementation_state"],
                "driver_gap_ids": item["driver_gap_ids"],
                "required_reserved_methods": [method["name"] for method in item["reserved_methods"]],
                "owner_decision_complete": False,
                "selected_adapter_ready": False,
                "adapter_load_allowed": False,
                "adapter_activation_allowed": False,
                "hardware_access_allowed": False,
                "adapter_load_blocked": True,
                "blockers": [
                    "target owner and ABI owner decisions remain open",
                    "Driver/HAL gap closure evidence is missing",
                    "Safety/Policy fault model and rollback semantics are not reviewed",
                    "target hardware smoke harness evidence is missing",
                    "durable evidence store and review workflow are not active",
                    "selected adapter readiness is not complete",
                ],
            }
            for item in EMPTY_INTERFACE_REGISTRY
        ]
        return {
            "adapter_load_blocker_rollup_state": "contract-only-adapter-load-blockers-open",
            "adapter_load_blocker_rollup_active": True,
            "adapter_load_ready": False,
            "adapter_load_allowed": False,
            "adapter_activation_allowed": False,
            "hardware_access_allowed": False,
            "gate_closure_allowed": False,
            "owner_decision_complete": False,
            "all_blockers_cleared": False,
            "scope": {
                "source_endpoints": [
                    "GET /hardware/interfaces/activation-checklist",
                    "GET /hardware/interfaces/owner-decision-status",
                    "GET /hardware/interfaces/owner-decision-evidence/status",
                    "GET /hardware/interfaces/owner-decision-evidence/retention-checklist",
                    "GET /hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist",
                    "GET /hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist",
                ],
                "target_endpoint": "GET /hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup",
                "purpose": "aggregate every open hardware owner, evidence, replacement, and selected-adapter blocker that prevents loading or activating a real adapter",
                "prototype_adapter_load": "not implemented; this rollup never selects, loads, activates, replaces, smokes, or dispatches a real adapter",
                "target_gate_scope": ["HW-ACT", "HW-ODS", "HW-OES", "HW-OER", "HW-OET", "HW-OEA", "HW-ALB", "DRV-GAP"],
            },
            "source_checklists": [
                {
                    "source": "activation-checklist",
                    "source_endpoint": "GET /hardware/interfaces/activation-checklist",
                    "gate_prefix": "HW-ACT",
                    "blocker_summary": "activation owner, ABI, Driver/HAL gap review, Safety/Policy, smoke harness, and rollback gates remain open",
                    "required_before_adapter_load": True,
                },
                {
                    "source": "owner-decision-status",
                    "source_endpoint": "GET /hardware/interfaces/owner-decision-status",
                    "gate_prefix": "HW-ODS",
                    "blocker_summary": "target owner, Android ABI owner, Linux ABI owner, Driver/HAL gap owner, Safety/Policy owner, target smoke owner, and rollback owner remain unresolved",
                    "required_before_adapter_load": True,
                },
                {
                    "source": "owner-evidence-status",
                    "source_endpoint": "GET /hardware/interfaces/owner-decision-evidence/status",
                    "gate_prefix": "HW-OES",
                    "blocker_summary": "durable evidence store, review workflow, review queue, and gate closure authority are not active",
                    "required_before_adapter_load": True,
                },
                {
                    "source": "owner-evidence-retention-checklist",
                    "source_endpoint": "GET /hardware/interfaces/owner-decision-evidence/retention-checklist",
                    "gate_prefix": "HW-OER",
                    "blocker_summary": "URI rules, retention, delete/export semantics, gate closure, and rollback/fault closure evidence remain unconfirmed",
                    "required_before_adapter_load": True,
                },
                {
                    "source": "replacement-trigger-checklist",
                    "source_endpoint": "GET /hardware/interfaces/owner-decision-evidence/replacement-trigger-checklist",
                    "gate_prefix": "HW-OET",
                    "blocker_summary": "replacement target, adapter readiness criteria, Driver/HAL gap closure evidence, ABI parity, rollback, Safety/Policy, and smoke evidence remain unapproved",
                    "required_before_adapter_load": True,
                },
                {
                    "source": "selected-adapter-readiness-checklist",
                    "source_endpoint": "GET /hardware/interfaces/owner-decision-evidence/selected-adapter-readiness-checklist",
                    "gate_prefix": "HW-OEA",
                    "blocker_summary": "selected adapter candidate, owner, interface contract, parity evidence, Safety/Policy fault model, smoke harness, and rollback review remain incomplete",
                    "required_before_adapter_load": True,
                },
            ],
            "blocker_groups": [
                {
                    "blocker_id": "owner-and-abi",
                    "state": "open",
                    "required_gates": ["HW-ACT-001", "HW-ACT-003", "HW-ACT-004", "HW-ODS-001", "HW-ODS-002", "HW-ODS-003"],
                    "adapter_load_allowed": False,
                },
                {
                    "blocker_id": "driver-hal-gap-evidence",
                    "state": "open",
                    "required_gates": ["HW-ACT-002", "HW-ODS-004", "HW-OET-003", "HW-OEA-003"],
                    "adapter_load_allowed": False,
                },
                {
                    "blocker_id": "evidence-store-and-review",
                    "state": "open",
                    "required_gates": ["HW-OES-001", "HW-OES-002", "HW-OER-001", "HW-OER-004", "HW-OER-005"],
                    "adapter_load_allowed": False,
                },
                {
                    "blocker_id": "replacement-and-selected-adapter",
                    "state": "open",
                    "required_gates": ["HW-OET-001", "HW-OET-002", "HW-OET-004", "HW-OEA-001", "HW-OEA-002", "HW-OEA-004"],
                    "adapter_load_allowed": False,
                },
                {
                    "blocker_id": "safety-smoke-rollback",
                    "state": "open",
                    "required_gates": ["HW-ACT-005", "HW-ACT-006", "HW-ACT-007", "HW-OET-005", "HW-OET-006", "HW-OET-007", "HW-OEA-005", "HW-OEA-006", "HW-OEA-007"],
                    "adapter_load_allowed": False,
                },
                {
                    "blocker_id": "no-hardware-policy",
                    "state": "enforced",
                    "required_gates": ["HW-ACT-008", "HW-OET-008", "HW-OEA-008", "HW-ALB-008"],
                    "adapter_load_allowed": False,
                    "hardware_access_allowed": False,
                },
            ],
            "per_interface_blockers": per_interface_blockers,
            "mandatory_gates": copy.deepcopy(HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_BLOCKER_GATES),
            "api_surface": {
                "rest": "GET /hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup",
                "android_binder": "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollupJson",
                "linux_cli": "hardware-interface-owner-decision-evidence-adapter-load-blocker-rollup",
                "linux_ipc": "hardware.interfaces.owner.decision.evidence.adapter.load.blocker.rollup",
                "linux_grpc_rpc": "CentralBrainGateway.GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadBlockerRollup",
            },
            "summary": {
                "owner_decision_evidence_adapter_load_blocker_rollup_active": True,
                "owner_decision_evidence_selected_adapter_readiness_checklist_active": True,
                "owner_decision_evidence_replacement_trigger_checklist_active": True,
                "owner_decision_evidence_retention_checklist_active": True,
                "owner_decision_evidence_status_contract_active": True,
                "owner_decision_evidence_contract_active": True,
                "activation_checklist_active": True,
                "owner_decision_status_contract_active": True,
                "owner_decision_complete": False,
                "all_blockers_cleared": False,
                "adapter_load_ready": False,
                "adapter_candidate_recorded": False,
                "adapter_owner_assigned": False,
                "adapter_interface_contract_approved": False,
                "driver_hal_gap_evidence_attached": False,
                "android_linux_binding_parity_approved": False,
                "safety_policy_fault_model_reviewed": False,
                "smoke_harness_plan_attached": False,
                "rollback_to_empty_interface_reviewed": False,
                "load_policy_confirmed": False,
                "replacement_policy_confirmed": False,
                "replacement_allowed": False,
                "evidence_store_active": False,
                "review_workflow_active": False,
                "review_queue_updated": False,
                "owner_assigned": False,
                "gate_state_changed": False,
                "gates_closed": False,
                "activation_allowed": False,
                "adapter_load_allowed": False,
                "adapter_activation_allowed": False,
                "hardware_access_allowed": False,
                "gate_closure_allowed": False,
                "hardware_accessed": False,
                "driver_development_triggered": False,
                "virtualization_development_triggered": False,
                "service_dispatch_triggered": False,
            },
            "req_ids": HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_BLOCKER_REQ_IDS,
        }

    def owner_decision_evidence_adapter_load_dry_run_payload(self, request: dict[str, Any], policy: dict[str, Any]) -> dict[str, Any]:
        interface_ids = {item["interface_id"] for item in EMPTY_INTERFACE_REGISTRY}
        selected_interface_id = str(request.get("selected_interface_id") or request.get("interface_id") or "")
        selected_adapter_id = str(request.get("selected_adapter_id") or request.get("adapter_id") or "")
        adapter_version = str(request.get("adapter_version") or request.get("version") or "")
        raw_evidence_refs = request.get("evidence_refs") or request.get("attachments") or []
        evidence_refs = raw_evidence_refs if isinstance(raw_evidence_refs, list) else [raw_evidence_refs]
        raw_requested_by = request.get("requested_by") or request.get("reviewer") or request.get("caller") or {}
        requested_by = raw_requested_by if isinstance(raw_requested_by, dict) else {"name": str(raw_requested_by)}
        dry_run_request_id = str(request.get("dry_run_request_id") or f"hw-ald-{uuid.uuid4()}")
        required_ref_fields = ["ref_id", "type", "uri_or_path", "owner", "summary"]
        invalid_evidence_ref_indexes = [
            index
            for index, evidence_ref in enumerate(evidence_refs)
            if not isinstance(evidence_ref, dict) or not all(evidence_ref.get(field) for field in required_ref_fields)
        ]
        known_interface = bool(selected_interface_id) and selected_interface_id in interface_ids
        has_adapter_identity = bool(selected_adapter_id and adapter_version)
        has_evidence_refs = bool(evidence_refs) and not invalid_evidence_ref_indexes
        has_requester = bool(requested_by.get("app_id") or requested_by.get("name") or requested_by.get("role"))
        policy_allowed = policy["decision"] == "allow"
        request_shape_valid = known_interface and has_adapter_identity and has_evidence_refs and has_requester
        blocker_rollup = self.owner_decision_evidence_adapter_load_blocker_rollup_payload()
        open_blocker_ids = [
            item["blocker_id"]
            for item in blocker_rollup["blocker_groups"]
            if item["state"] in ("open", "enforced")
        ]

        if not policy_allowed:
            state = "rejected_by_policy"
        elif not request_shape_valid:
            state = "rejected_missing_request_shape"
        else:
            state = "rejected_blocked_contract_only"

        mandatory_gates = copy.deepcopy(HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_DRY_RUN_GATES)
        gate_passes = {
            "HW-ALD-001": request_shape_valid,
            "HW-ALD-003": known_interface,
            "HW-ALD-004": has_adapter_identity,
            "HW-ALD-005": has_evidence_refs,
            "HW-ALD-006": policy_allowed,
        }
        for gate in mandatory_gates:
            if gate["gate_id"] in gate_passes:
                gate["passed"] = gate_passes[gate["gate_id"]]

        return {
            "operation": "hardware-owner-decision-evidence-adapter-load-dry-run",
            "dry_run_request_id": dry_run_request_id,
            "adapter_load_dry_run_state": state,
            "dry_run_validated": request_shape_valid and policy_allowed,
            "adapter_load_blocked": True,
            "adapter_load_allowed": False,
            "adapter_activation_allowed": False,
            "hardware_access_allowed": False,
            "gate_closure_allowed": False,
            "selected_interface_id": selected_interface_id,
            "selected_adapter_id": selected_adapter_id,
            "adapter_version": adapter_version,
            "unknown_interface_id": None if known_interface else selected_interface_id or None,
            "evidence_refs": evidence_refs,
            "invalid_evidence_ref_indexes": invalid_evidence_ref_indexes,
            "requested_by": requested_by,
            "request_contract": {
                "required_fields": [
                    "selected_interface_id",
                    "selected_adapter_id",
                    "adapter_version",
                    "requested_by",
                    "evidence_refs",
                ],
                "accepted_evidence_ref_types": [
                    "owner_approval",
                    "driver_gap_review",
                    "safety_review",
                    "smoke_result",
                    "rollback_plan",
                    "platform_decision",
                ],
                "required_ref_fields": required_ref_fields,
                "prototype_persistence": "not implemented; dry-run request is validated and discarded after response",
            },
            "blocker_rollup_reference": {
                "source_endpoint": "GET /hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup",
                "adapter_load_blocker_rollup_state": blocker_rollup["adapter_load_blocker_rollup_state"],
                "adapter_load_ready": blocker_rollup["adapter_load_ready"],
                "all_blockers_cleared": blocker_rollup["all_blockers_cleared"],
                "open_blocker_ids": open_blocker_ids,
                "source_checklists": [item["source"] for item in blocker_rollup["source_checklists"]],
                "mandatory_gate_ids": [item["gate_id"] for item in blocker_rollup["mandatory_gates"]],
            },
            "validation": {
                "policy_checked": True,
                "policy": policy,
                "selected_interface_known": known_interface,
                "adapter_identity_present": has_adapter_identity,
                "evidence_refs_present": has_evidence_refs,
                "evidence_refs_shape_valid": has_evidence_refs,
                "requested_by_present": has_requester,
                "request_shape_valid": request_shape_valid,
                "audit_recorded": True,
            },
            "dry_run_result": {
                "state": state,
                "allowed_to_load_adapter": False,
                "allowed_to_activate_adapter": False,
                "allowed_to_access_hardware": False,
                "allowed_to_close_gates": False,
                "review_queue_updated": False,
                "evidence_persisted": False,
                "adapter_selected": False,
                "adapter_loaded": False,
                "adapter_activated": False,
                "hardware_accessed": False,
                "reason": "adapter-load dry-run is contract-only and rejected while blocker rollup reports open owner, evidence, replacement, selected-adapter, Safety/Policy, smoke, rollback, and no-hardware gates",
            },
            "mandatory_gates": mandatory_gates,
            "api_surface": {
                "rest": "POST /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run",
                "android_binder": "dryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoadJson",
                "linux_cli": "hardware-interface-owner-decision-evidence-adapter-load-dry-run",
                "linux_ipc": "hardware.interfaces.owner.decision.evidence.adapter.load.dry.run",
                "linux_grpc_rpc": "CentralBrainGateway.DryRunHardwareInterfaceOwnerDecisionEvidenceAdapterLoad",
            },
            "summary": {
                "owner_decision_evidence_adapter_load_dry_run_active": True,
                "owner_decision_evidence_adapter_load_blocker_rollup_active": True,
                "owner_decision_evidence_selected_adapter_readiness_checklist_active": True,
                "owner_decision_evidence_replacement_trigger_checklist_active": True,
                "owner_decision_evidence_retention_checklist_active": True,
                "owner_decision_evidence_status_contract_active": True,
                "owner_decision_evidence_contract_active": True,
                "request_shape_valid": request_shape_valid,
                "dry_run_validated": request_shape_valid and policy_allowed,
                "policy_allowed": policy_allowed,
                "owner_decision_complete": False,
                "all_blockers_cleared": False,
                "adapter_load_ready": False,
                "adapter_candidate_recorded": False,
                "adapter_owner_assigned": False,
                "adapter_interface_contract_approved": False,
                "driver_hal_gap_evidence_attached": False,
                "android_linux_binding_parity_approved": False,
                "safety_policy_fault_model_reviewed": False,
                "smoke_harness_plan_attached": False,
                "rollback_to_empty_interface_reviewed": False,
                "load_policy_confirmed": False,
                "replacement_policy_confirmed": False,
                "replacement_allowed": False,
                "evidence_store_active": False,
                "review_workflow_active": False,
                "review_queue_updated": False,
                "owner_assigned": False,
                "gate_state_changed": False,
                "gates_closed": False,
                "activation_allowed": False,
                "adapter_load_allowed": False,
                "adapter_activation_allowed": False,
                "hardware_access_allowed": False,
                "gate_closure_allowed": False,
                "hardware_accessed": False,
                "driver_development_triggered": False,
                "virtualization_development_triggered": False,
                "service_dispatch_triggered": False,
            },
            "req_ids": HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_DRY_RUN_REQ_IDS,
        }

    def owner_decision_evidence_adapter_load_dry_run_status_payload(self) -> dict[str, Any]:
        blocker_rollup = self.owner_decision_evidence_adapter_load_blocker_rollup_payload()
        open_blocker_ids = [
            item["blocker_id"]
            for item in blocker_rollup["blocker_groups"]
            if item["state"] in ("open", "enforced")
        ]
        return {
            "operation": "hardware-owner-decision-evidence-adapter-load-dry-run-status",
            "adapter_load_dry_run_status_state": "contract-only-no-store-status",
            "last_result_available": False,
            "last_result_state": "not-persisted-no-last-result",
            "persisted_dry_run_count": 0,
            "pending_review_count": 0,
            "review_queue_updated": False,
            "evidence_persisted": False,
            "adapter_load_allowed": False,
            "adapter_activation_allowed": False,
            "hardware_access_allowed": False,
            "gate_closure_allowed": False,
            "status_contract": {
                "source_request_endpoint": "POST /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run",
                "source_blocker_endpoint": "GET /hardware/interfaces/owner-decision-evidence/adapter-load-blocker-rollup",
                "prototype_persistence": "not implemented; dry-run requests are validated and discarded after response",
                "last_result_storage": "not implemented; status returns only no-store counters and the expected last-result shape",
            },
            "last_result_shape": {
                "fields": [
                    "dry_run_request_id",
                    "adapter_load_dry_run_state",
                    "dry_run_validated",
                    "selected_interface_id",
                    "selected_adapter_id",
                    "adapter_version",
                    "blocker_rollup_reference",
                    "dry_run_result",
                    "summary",
                ],
                "expected_terminal_states": [
                    "rejected_blocked_contract_only",
                    "rejected_missing_request_shape",
                    "rejected_by_policy",
                ],
                "persisted_last_result_available": False,
            },
            "blocker_rollup_reference": {
                "adapter_load_blocker_rollup_state": blocker_rollup["adapter_load_blocker_rollup_state"],
                "adapter_load_ready": blocker_rollup["adapter_load_ready"],
                "all_blockers_cleared": blocker_rollup["all_blockers_cleared"],
                "open_blocker_ids": open_blocker_ids,
                "mandatory_gate_ids": [item["gate_id"] for item in blocker_rollup["mandatory_gates"]],
            },
            "no_store_invariants": {
                "request_payload_stored": False,
                "last_result_stored": False,
                "evidence_store_active": False,
                "review_workflow_active": False,
                "review_queue_updated": False,
                "owner_assigned": False,
                "gate_state_changed": False,
                "gates_closed": False,
                "adapter_selected": False,
                "adapter_loaded": False,
                "adapter_activated": False,
                "hardware_accessed": False,
            },
            "mandatory_gates": copy.deepcopy(HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_DRY_RUN_STATUS_GATES),
            "api_surface": {
                "rest": "GET /hardware/interfaces/owner-decision-evidence/adapter-load-dry-run/status",
                "android_binder": "getHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatusJson",
                "linux_cli": "hardware-interface-owner-decision-evidence-adapter-load-dry-run-status",
                "linux_ipc": "hardware.interfaces.owner.decision.evidence.adapter.load.dry.run.status",
                "linux_grpc_rpc": "CentralBrainGateway.GetHardwareInterfaceOwnerDecisionEvidenceAdapterLoadDryRunStatus",
            },
            "summary": {
                "owner_decision_evidence_adapter_load_dry_run_status_active": True,
                "owner_decision_evidence_adapter_load_dry_run_active": True,
                "owner_decision_evidence_adapter_load_blocker_rollup_active": True,
                "last_result_available": False,
                "persisted_dry_run_count": 0,
                "pending_review_count": 0,
                "owner_decision_complete": False,
                "all_blockers_cleared": False,
                "adapter_load_ready": False,
                "adapter_candidate_recorded": False,
                "adapter_owner_assigned": False,
                "adapter_interface_contract_approved": False,
                "driver_hal_gap_evidence_attached": False,
                "android_linux_binding_parity_approved": False,
                "safety_policy_fault_model_reviewed": False,
                "smoke_harness_plan_attached": False,
                "rollback_to_empty_interface_reviewed": False,
                "load_policy_confirmed": False,
                "replacement_policy_confirmed": False,
                "evidence_store_active": False,
                "review_workflow_active": False,
                "review_queue_updated": False,
                "owner_assigned": False,
                "gate_state_changed": False,
                "gates_closed": False,
                "activation_allowed": False,
                "adapter_load_allowed": False,
                "adapter_activation_allowed": False,
                "hardware_access_allowed": False,
                "gate_closure_allowed": False,
                "hardware_accessed": False,
                "driver_development_triggered": False,
                "virtualization_development_triggered": False,
                "service_dispatch_triggered": False,
            },
            "req_ids": HARDWARE_OWNER_EVIDENCE_ADAPTER_LOAD_DRY_RUN_STATUS_REQ_IDS,
        }

    def interfaces_payload(self) -> dict[str, Any]:
        interfaces = copy.deepcopy(EMPTY_INTERFACE_REGISTRY)
        return {
            "interfaces": interfaces,
            "summary": {
                "interface_count": len(interfaces),
                "implementation_state": "empty-interface-registry",
                "hardware_accessed": False,
                "driver_development_triggered": False,
                "virtualization_development_triggered": False,
                "service_dispatch_triggered": False,
                "android_primary_path": "Binder-facing Android prototype remains primary; real hardware routes stay behind future platform HAL/native adapters.",
                "linux_sync_path": "Linux CLI/IPC/gRPC samples expose the same read-only interface catalog without touching devices.",
            },
            "global_constraints": [
                "Python prototype must not access real PCIe NPU, VHAL, camera/audio/sensor HAL, vehicle Ethernet, shared memory, or virtualization APIs.",
                "Reserved methods are interface contracts only and are not callable hardware dispatch points in this increment.",
                "Driver/HAL implementation starts only after a documented platform gap cannot be satisfied by current Android/Linux facilities.",
                "Yellow-sun portable components must keep Android and Linux binding visibility aligned.",
            ],
            "api_surface": {
                "rest": "GET /hardware/interfaces",
                "android_binder": "getHardwareInterfacesJson",
                "linux_cli": "hardware-interfaces",
                "linux_ipc": "hardware.interfaces.get",
                "linux_grpc_rpc": "CentralBrainGateway.GetHardwareInterfaces",
            },
            "req_ids": HARDWARE_REQ_IDS,
        }
