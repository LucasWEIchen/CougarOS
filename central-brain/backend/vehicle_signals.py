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

    def summary_payload(self) -> dict[str, Any]:
        signals = copy.deepcopy(SIGNAL_CATALOG)
        for signal in signals:
            signal["path"] = signal["signal_path"]
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
        signals = copy.deepcopy(SIGNAL_CATALOG)
        for signal in signals:
            signal["path"] = signal["signal_path"]
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
