#!/usr/bin/env python3
"""Vehicle signal catalog contract for the Central Brain prototype.

Req IDs: XSC-002, XSC-004, XSC-006, NV-F-004, NV-F-005, FW-U-001, FW-U-002,
FW-U-003, FW-U-004, NV-P-002, NV-P-003, DEL-001, DEL-002, DEL-005.

This module exposes a read-only signal catalog. It must not open VHAL,
SocketCAN, DBC/ARXML files, vendor gateways, Driver/HAL, or virtualization
interfaces in the Python prototype.
"""

from __future__ import annotations

import copy
from typing import Any


VEHICLE_SIGNAL_REQ_IDS = [
    "XSC-002",
    "XSC-004",
    "XSC-006",
    "FW-U-001",
    "FW-U-002",
    "FW-U-003",
    "FW-U-004",
    "NV-F-004",
    "NV-F-005",
    "NV-P-002",
    "NV-P-003",
    "DEL-001",
    "DEL-002",
    "DEL-005",
    "KH-003",
    "KH-006",
]


VEHICLE_SIGNAL_ACTIVATION_REQ_IDS = sorted(
    set(VEHICLE_SIGNAL_REQ_IDS + ["NV-F-003", "NV-P-001", "KH-007"])
)

VEHICLE_SIGNAL_VALIDATION_REQ_IDS = VEHICLE_SIGNAL_ACTIVATION_REQ_IDS


SIGNAL_CATALOG: list[dict[str, Any]] = [
    {
        "signal_path": "Vehicle.Speed",
        "domain": "vehicle-motion",
        "adapter": "vehicle-signal-adapter",
        "value_type": "float",
        "unit": "km/h",
        "direction": "read-only",
        "mock_value": 0,
        "quality": "mock",
        "freshness_ms": 100,
        "permissions": ["vehicle.read"],
        "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        "source_contract": "VSS-style mock snapshot",
        "future_android_source": "Vehicle HAL or vendor AIDL service mapped through Vehicle Signal Adapter",
        "future_linux_source": "SocketCAN, SOME/IP, DBC/ARXML, or vendor signal gateway mapped through Vehicle Signal Adapter",
        "driver_gap_ids": ["DRV-GAP-002"],
        "req_ids": ["NV-F-004", "NV-F-005", "FW-U-002", "DEL-001", "DEL-002"],
    },
    {
        "signal_path": "Vehicle.Cabin.HVAC.Station.Row1.Left.Temperature",
        "domain": "hvac",
        "adapter": "vehicle-signal-adapter",
        "value_type": "float",
        "unit": "celsius",
        "direction": "read-write-contract",
        "mock_value": 22.5,
        "quality": "mock",
        "freshness_ms": 500,
        "permissions": ["vehicle.read", "vehicle.control"],
        "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        "source_contract": "VSS-style mock snapshot plus Uni Info Bus Action request for writes",
        "future_android_source": "HVAC HAL/AIDL or vendor service behind SOA + Policy",
        "future_linux_source": "DBC/ARXML or vendor HVAC gateway behind SOA + Policy",
        "driver_gap_ids": ["DRV-GAP-002"],
        "req_ids": ["NV-F-004", "NV-F-005", "FW-U-004", "DEL-001", "DEL-002"],
    },
    {
        "signal_path": "Vehicle.Cabin.Seat.Row1.Left.Position",
        "domain": "seat",
        "adapter": "vehicle-signal-adapter",
        "value_type": "enum",
        "unit": None,
        "direction": "read-write-contract",
        "mock_value": "comfort",
        "quality": "mock",
        "freshness_ms": 1000,
        "permissions": ["vehicle.read", "vehicle.control"],
        "allowed_safety_states": ["normal", "diagnostic_readonly"],
        "source_contract": "VSS-style mock snapshot plus Uni Info Bus Action request for writes",
        "future_android_source": "Seat control HAL/AIDL or vendor cabin service behind SOA + Policy",
        "future_linux_source": "DBC/ARXML or vendor body controller gateway behind SOA + Policy",
        "driver_gap_ids": ["DRV-GAP-002"],
        "req_ids": ["NV-F-004", "NV-F-005", "FW-U-004", "DEL-001", "DEL-002"],
    },
    {
        "signal_path": "Vehicle.Body.Door.Row1.Left.IsOpen",
        "domain": "door",
        "adapter": "vehicle-signal-adapter",
        "value_type": "boolean",
        "unit": None,
        "direction": "read-only",
        "mock_value": False,
        "quality": "mock",
        "freshness_ms": 250,
        "permissions": ["vehicle.read"],
        "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        "source_contract": "VSS-style mock snapshot",
        "future_android_source": "Body control HAL/AIDL or vendor service mapped through Vehicle Signal Adapter",
        "future_linux_source": "SocketCAN, DBC/ARXML, or vendor body controller gateway",
        "driver_gap_ids": ["DRV-GAP-002"],
        "req_ids": ["NV-F-004", "NV-F-005", "FW-U-001", "FW-U-002", "DEL-001", "DEL-002"],
    },
    {
        "signal_path": "Vehicle.Cabin.Lights.AmbientLight.IsOn",
        "domain": "lighting",
        "adapter": "vehicle-signal-adapter",
        "value_type": "boolean",
        "unit": None,
        "direction": "read-write-contract",
        "mock_value": True,
        "quality": "mock",
        "freshness_ms": 500,
        "permissions": ["vehicle.read", "vehicle.control"],
        "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        "source_contract": "VSS-style mock snapshot plus Uni Info Bus Action request for writes",
        "future_android_source": "Lighting HAL/AIDL or vendor body service behind SOA + Policy",
        "future_linux_source": "DBC/ARXML or vendor lighting gateway behind SOA + Policy",
        "driver_gap_ids": ["DRV-GAP-002"],
        "req_ids": ["NV-F-004", "NV-F-005", "FW-U-004", "DEL-001", "DEL-002"],
    },
    {
        "signal_path": "Vehicle.Powertrain.TractionBattery.StateOfCharge.Current",
        "domain": "powertrain",
        "adapter": "vehicle-signal-adapter",
        "value_type": "float",
        "unit": "percent",
        "direction": "read-only",
        "mock_value": 78,
        "quality": "mock",
        "freshness_ms": 1000,
        "permissions": ["vehicle.read"],
        "allowed_safety_states": ["normal", "degraded", "diagnostic_readonly"],
        "source_contract": "VSS-style mock snapshot",
        "future_android_source": "Powertrain or energy HAL/AIDL source behind Vehicle Signal Adapter",
        "future_linux_source": "DBC/ARXML or vendor gateway mapped through Vehicle Signal Adapter",
        "driver_gap_ids": ["DRV-GAP-002"],
        "req_ids": ["NV-F-004", "NV-F-005", "FW-U-002", "DEL-001", "DEL-002"],
    },
]


class VehicleSignalRegistry:
    """Read-only catalog for vehicle/body signal adapter contracts."""

    def _signals_with_path(self) -> list[dict[str, Any]]:
        signals = copy.deepcopy(SIGNAL_CATALOG)
        for signal in signals:
            signal["path"] = signal["signal_path"]
        return signals

    def summary_payload(self) -> dict[str, Any]:
        signals = self._signals_with_path()
        domains = sorted({signal["domain"] for signal in signals})
        return {
            "signal_count": len(signals),
            "domains": domains,
            "catalog_state": "read-only-mock-signal-catalog",
            "vss_style_paths": True,
            "dbc_arxml_loaded": False,
            "real_vehicle_bus_connected": False,
            "hardware_accessed": False,
            "driver_development_triggered": False,
            "virtualization_development_triggered": False,
            "service_dispatch_triggered": False,
            "req_ids": VEHICLE_SIGNAL_REQ_IDS,
        }

    def catalog_payload(self) -> dict[str, Any]:
        signals = self._signals_with_path()
        return {
            "signals": signals,
            "summary": self.summary_payload(),
            "adapter_boundary": {
                "semantic_owner": "Uni Info Bus Context/State/Event/Action",
                "native_adapter": "Vehicle Signal Adapter",
                "soa_service": "vehicle-state",
                "driver_hal": "not-dispatched",
                "vehicle_bus": "not-connected",
                "dbc_arxml": "not-loaded",
                "virtualization": "not-developed",
            },
            "api_surface": {
                "rest": "GET /vehicle/signals",
                "android_binder": "getVehicleSignalsJson",
                "linux_cli": "vehicle-signals",
                "linux_ipc": "vehicle.signals.list",
                "linux_grpc_rpc": "CentralBrainGateway.GetVehicleSignals",
            },
            "integration_constraints": [
                "Signal reads and action requests must keep Uni Info Bus semantic envelopes and Runtime & Governance policy checks.",
                "Writes remain contract-only through /uib/actions/request and must not call Driver/HAL in the Python prototype.",
                "DBC/ARXML, VHAL, SocketCAN, SOME/IP, vendor gateway, and Driver/HAL integration are future activation triggers, not current behavior.",
                "Yellow-sun portable components must expose the same signal catalog through Android and Linux bindings.",
            ],
            "driver_gap_ids": ["DRV-GAP-002"],
            "req_ids": VEHICLE_SIGNAL_REQ_IDS,
        }

    def activation_payload(self) -> dict[str, Any]:
        return {
            "activation_state": "criteria-only-not-activated",
            "read_bridge_activated": False,
            "activation_options": [
                {
                    "source_type": "dbc-arxml",
                    "target_boundary": "ECU Proxy / Signal Adapter",
                    "required_inputs": [
                        "target vehicle DBC or ARXML source files",
                        "signal-to-VSS path mapping table",
                        "unit, range, endian, scaling, and freshness validation rules",
                        "ownership decision for generated schema artifacts",
                    ],
                    "android_activation_path": "Vehicle Signal Adapter validates schema before any VHAL/HAL bridge is enabled",
                    "linux_activation_path": "Vehicle Signal Adapter validates schema before any SocketCAN or vendor gateway bridge is enabled",
                    "minimum_development_if_triggered": "schema parser/validator plus read-only adapter mapping; no write path until Policy/Safety gates are approved",
                    "driver_development_triggered": False,
                    "req_ids": ["NV-F-005", "XSC-004", "DEL-005"],
                },
                {
                    "source_type": "android-vhal-or-vendor-aidl",
                    "target_boundary": "Android Vehicle Signal Adapter",
                    "required_inputs": [
                        "target VHAL or vendor AIDL contract",
                        "Binder identity to Runtime & Governance Policy mapping",
                        "permission and Safety State mapping for each signal family",
                        "mock-to-real fallback policy for unavailable signals",
                    ],
                    "android_activation_path": "Android system/privileged service calls the adapter after Runtime & Governance checks",
                    "linux_activation_path": "not applicable except for parity contract review",
                    "minimum_development_if_triggered": "Android read bridge shim behind Vehicle Signal Adapter",
                    "driver_development_triggered": False,
                    "req_ids": ["NV-F-004", "NV-F-005", "NV-P-002", "KH-006", "DEL-001", "DEL-005"],
                },
                {
                    "source_type": "linux-socketcan",
                    "target_boundary": "Linux Vehicle Signal Adapter",
                    "required_inputs": [
                        "target CAN interface name and access policy",
                        "DBC/ARXML or equivalent frame-to-signal mapping",
                        "service account, socket permission, and audit policy",
                        "freshness, timeout, degraded-state, and replay behavior",
                    ],
                    "android_activation_path": "not applicable except for parity contract review",
                    "linux_activation_path": "Linux daemon reads through adapter after governance precheck and schema validation",
                    "minimum_development_if_triggered": "read-only SocketCAN bridge plus validation smoke; no control writes",
                    "driver_development_triggered": False,
                    "req_ids": ["NV-F-004", "NV-F-005", "NV-P-002", "KH-003", "DEL-002", "DEL-005"],
                },
                {
                    "source_type": "vendor-gateway-or-someip",
                    "target_boundary": "Protocol Binding plus Vehicle Signal Adapter",
                    "required_inputs": [
                        "vendor gateway API or SOME/IP service catalog",
                        "authentication, credential, and service discovery owner",
                        "QoS/backpressure behavior for signal reads",
                        "failure-mode mapping into Safety State and audit events",
                    ],
                    "android_activation_path": "Binder-facing contract remains stable while vendor bridge is hidden behind adapter",
                    "linux_activation_path": "IPC/gRPC-facing contract remains stable while vendor bridge is hidden behind adapter",
                    "minimum_development_if_triggered": "read-only gateway binding plus Protocol Binding registration",
                    "driver_development_triggered": False,
                    "req_ids": ["NV-F-004", "NV-F-005", "NV-P-001", "NV-P-003", "XSC-006", "DEL-002", "DEL-005"],
                },
            ],
            "mandatory_gates": [
                {
                    "gate_id": "VS-ACT-001",
                    "name": "schema-source-accepted",
                    "evidence_required": "DBC/ARXML, VHAL/AIDL contract, SocketCAN mapping, or vendor gateway contract is checked into the target integration workspace",
                },
                {
                    "gate_id": "VS-ACT-002",
                    "name": "policy-and-safety-mapping",
                    "evidence_required": "each signal family has permissions, allowed Safety States, fallback behavior, and audit tags",
                },
                {
                    "gate_id": "VS-ACT-003",
                    "name": "android-linux-contract-parity",
                    "evidence_required": "Android Binder and Linux CLI/IPC/gRPC expose the same semantic paths and Req IDs",
                },
                {
                    "gate_id": "VS-ACT-004",
                    "name": "no-write-before-read-bridge",
                    "evidence_required": "write/control paths remain routed through /uib/actions/request and are not enabled by the read bridge",
                },
                {
                    "gate_id": "VS-ACT-005",
                    "name": "driver-hal-scope-review",
                    "evidence_required": "DRV-GAP-002 is updated with owner, ABI, minimal development, and validation smoke before any Driver/HAL work starts",
                },
            ],
            "summary": {
                "criteria_state": "ready-for-integration-review",
                "read_bridge_activated": False,
                "dbc_arxml_loaded": False,
                "vhal_connected": False,
                "socketcan_connected": False,
                "vendor_gateway_connected": False,
                "hardware_accessed": False,
                "driver_development_triggered": False,
                "virtualization_development_triggered": False,
                "service_dispatch_triggered": False,
            },
            "api_surface": {
                "rest": "GET /vehicle/signals/activation",
                "android_binder": "getVehicleSignalActivationJson",
                "linux_cli": "vehicle-signal-activation",
                "linux_ipc": "vehicle.signals.activation.get",
                "linux_grpc_rpc": "CentralBrainGateway.GetVehicleSignalActivation",
            },
            "non_goals": [
                "No DBC/ARXML parsing or code generation in the Python prototype.",
                "No VHAL, SocketCAN, SOME/IP, vendor gateway, Driver/HAL, Safety Runtime, or real vehicle bus access.",
                "No signal write/control activation; writes remain policy-checked Action contracts only.",
                "No virtualization development.",
            ],
            "driver_gap_ids": ["DRV-GAP-002"],
            "req_ids": VEHICLE_SIGNAL_ACTIVATION_REQ_IDS,
        }

    def validation_payload(self) -> dict[str, Any]:
        return {
            "validation_state": "metadata-only-not-activated",
            "read_bridge_activated": False,
            "schema_source_attached": False,
            "adapter_owner_confirmed": False,
            "parity_evidence_attached": False,
            "drv_gap_002_evidence_attached": False,
            "validation_envelope": {
                "schema_source_metadata": {
                    "accepted_source_types": [
                        "DBC",
                        "ARXML",
                        "Android VHAL",
                        "vendor AIDL",
                        "Linux SocketCAN mapping",
                        "SOME/IP or vendor gateway contract",
                    ],
                    "required_fields": [
                        "source_owner",
                        "source_version",
                        "signal_to_vss_mapping_version",
                        "unit_range_scaling_policy",
                        "freshness_timeout_policy",
                        "quality_and_degraded_state_policy",
                    ],
                    "current_state": "missing-target-source-metadata",
                },
                "adapter_ownership": {
                    "required_owner": "Vehicle Signal Adapter owner for Android and Linux targets",
                    "required_abi_owner": "platform ABI/API owner for VHAL/AIDL, SocketCAN, or vendor gateway bridge",
                    "current_state": "unassigned-in-prototype",
                },
                "android_linux_parity": {
                    "required_evidence": [
                        "Android Binder method present",
                        "Linux CLI command present",
                        "Linux IPC operation present",
                        "Linux gRPC/RPC name present",
                        "same Req IDs and no-hardware summary fields across transports",
                    ],
                    "current_state": "contract-surface-present-evidence-pending-target-review",
                },
                "driver_gap_002": {
                    "gap_id": "DRV-GAP-002",
                    "required_evidence": [
                        "owner",
                        "ABI or vendor API contract",
                        "minimal read-only development scope",
                        "validation smoke command",
                        "replacement trigger for target Android/Linux platform",
                    ],
                    "current_state": "gap-linked-no-driver-work-started",
                },
                "write_path_guard": {
                    "required_evidence": "read bridge validation cannot enable control writes; write/control remains policy-checked /uib/actions/request only",
                    "current_state": "guard-active-in-contract",
                },
            },
            "mandatory_gates": [
                {
                    "gate_id": "VS-VAL-001",
                    "name": "schema-source-metadata-present",
                    "evidence_required": "target DBC/ARXML, VHAL/AIDL, SocketCAN, SOME/IP, or vendor gateway source metadata is attached with owner and version",
                    "passed": False,
                },
                {
                    "gate_id": "VS-VAL-002",
                    "name": "signal-to-vss-policy-reviewed",
                    "evidence_required": "signal mapping, units, ranges, freshness, quality, and degraded behavior are reviewed for Android and Linux",
                    "passed": False,
                },
                {
                    "gate_id": "VS-VAL-003",
                    "name": "adapter-and-abi-owner-confirmed",
                    "evidence_required": "Vehicle Signal Adapter owner and platform ABI/API owner are named before read bridge development starts",
                    "passed": False,
                },
                {
                    "gate_id": "VS-VAL-004",
                    "name": "android-linux-binding-parity-proven",
                    "evidence_required": "Binder, CLI, IPC, and gRPC/RPC expose the same validation contract and Req IDs",
                    "passed": False,
                },
                {
                    "gate_id": "VS-VAL-005",
                    "name": "drv-gap-002-evidence-recorded",
                    "evidence_required": "DRV-GAP-002 has owner, ABI, minimal implementation scope, and validation smoke before Driver/HAL work starts",
                    "passed": False,
                },
                {
                    "gate_id": "VS-VAL-006",
                    "name": "no-write-before-read-bridge",
                    "evidence_required": "control writes remain disabled outside /uib/actions/request policy checks",
                    "passed": True,
                },
            ],
            "summary": {
                "validation_state": "metadata-only-not-activated",
                "read_bridge_activated": False,
                "schema_source_attached": False,
                "adapter_owner_confirmed": False,
                "parity_evidence_attached": False,
                "drv_gap_002_evidence_attached": False,
                "hardware_accessed": False,
                "driver_development_triggered": False,
                "virtualization_development_triggered": False,
                "service_dispatch_triggered": False,
            },
            "api_surface": {
                "rest": "GET /vehicle/signals/validation",
                "android_binder": "getVehicleSignalValidationJson",
                "linux_cli": "vehicle-signal-validation",
                "linux_ipc": "vehicle.signals.validation.get",
                "linux_grpc_rpc": "CentralBrainGateway.GetVehicleSignalValidation",
            },
            "non_goals": [
                "No DBC/ARXML parsing, VHAL/AIDL connection, SocketCAN bridge, SOME/IP bridge, vendor gateway, or Driver/HAL implementation.",
                "No hardware access, no service dispatch, no virtualization development, and no control-write activation.",
                "This endpoint records readiness evidence requirements only; it cannot activate a real read bridge.",
            ],
            "driver_gap_ids": ["DRV-GAP-002"],
            "req_ids": VEHICLE_SIGNAL_VALIDATION_REQ_IDS,
        }
