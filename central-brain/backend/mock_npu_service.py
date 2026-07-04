#!/usr/bin/env python3
"""Prototype backend for the vehicle central brain.

This service intentionally uses only the Python standard library so it can run
inside the current WSL workspace without adding package dependencies.
"""

from __future__ import annotations

import argparse
import json
import os
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import urlparse


STARTED_AT = time.time()


def read_text(path: Path) -> str | None:
    try:
        return path.read_text(encoding="utf-8").strip()
    except OSError:
        return None


def discover_pci_devices(limit: int = 12) -> list[dict[str, str]]:
    devices_dir = Path("/sys/bus/pci/devices")
    if not devices_dir.exists():
        return []

    devices: list[dict[str, str]] = []
    for device_path in sorted(devices_dir.iterdir()):
        if len(devices) >= limit:
            break
        devices.append(
            {
                "slot": device_path.name,
                "vendor": read_text(device_path / "vendor") or "unknown",
                "device": read_text(device_path / "device") or "unknown",
                "class": read_text(device_path / "class") or "unknown"
            }
        )
    return devices


def npu_status() -> dict[str, Any]:
    requested_vendor = os.environ.get("CENTRAL_BRAIN_NPU_VENDOR_ID")
    requested_device = os.environ.get("CENTRAL_BRAIN_NPU_DEVICE")
    pci_devices = discover_pci_devices()
    matched = [
        dev for dev in pci_devices
        if requested_vendor and dev.get("vendor", "").lower() == requested_vendor.lower()
    ]

    mode = "mock"
    if requested_device:
        mode = "configured-device"
    if matched:
        mode = "pci-vendor-detected"

    return {
        "mode": mode,
        "runtime": "mock-npu",
        "device_node": requested_device,
        "requested_vendor": requested_vendor,
        "pci_devices_sample": pci_devices,
        "matched_devices": matched,
        "model_slots": [
            {
                "model": "central-intent-v0",
                "state": "loaded",
                "backend": mode
            },
            {
                "model": "vehicle-scene-v0",
                "state": "loaded",
                "backend": mode
            }
        ]
    }


def health_payload() -> dict[str, Any]:
    return {
        "system": "central-brain",
        "version": "0.1.0",
        "status": "ok",
        "safety_state": "normal",
        "uptime_s": round(time.time() - STARTED_AT, 3),
        "service_bus": {
            "registry": "ok",
            "discovery": "ok",
            "schema": "ok",
            "policy": "mock",
            "lifecycle": "ok"
        },
        "ai_backend": npu_status()
    }


def services_payload() -> dict[str, Any]:
    return {
        "services": [
            {
                "name": "vehicle-state",
                "version": "0.1.0",
                "contract": "GET /vehicle/state",
                "permissions": ["vehicle.read"],
                "safety_state": "normal"
            },
            {
                "name": "npu-inference",
                "version": "0.1.0",
                "contract": "POST /ai/infer",
                "permissions": ["ai.infer"],
                "safety_state": "normal"
            },
            {
                "name": "service-registry",
                "version": "0.1.0",
                "contract": "GET /services",
                "permissions": ["service.read"],
                "safety_state": "normal"
            }
        ]
    }


def vehicle_state_payload() -> dict[str, Any]:
    now = time.time()
    return {
        "timestamp_ms": int(now * 1000),
        "signals": {
            "Vehicle.Speed": {
                "value": 0,
                "unit": "km/h",
                "quality": "mock"
            },
            "Vehicle.Cabin.HVAC.Station.Row1.Left.Temperature": {
                "value": 22.5,
                "unit": "celsius",
                "quality": "mock"
            },
            "Vehicle.Cabin.Seat.Row1.Left.Position": {
                "value": "comfort",
                "quality": "mock"
            },
            "Vehicle.Powertrain.TractionBattery.StateOfCharge.Current": {
                "value": 78,
                "unit": "percent",
                "quality": "mock"
            }
        },
        "context": {
            "driving_state": "parked",
            "occupants": 1,
            "network": "local-wsl"
        }
    }


def inference_payload(request: dict[str, Any]) -> dict[str, Any]:
    started = time.time()
    model = str(request.get("model") or "central-intent-v0")
    input_value = request.get("input", {})
    policy = request.get("policy", {})
    required_state = policy.get("safety_state_required", "normal")

    if required_state != "normal":
        return {
            "request_id": str(uuid.uuid4()),
            "model": model,
            "runtime": "mock-npu",
            "status": "rejected",
            "result": {
                "reason": "prototype only accepts normal safety state"
            },
            "metrics": {
                "queue_ms": 0.2,
                "inference_ms": 0.0
            }
        }

    time.sleep(0.03)
    inference_ms = (time.time() - started) * 1000
    return {
        "request_id": str(uuid.uuid4()),
        "model": model,
        "runtime": "mock-npu",
        "status": "ok",
        "result": {
            "intent": "vehicle_state_query",
            "confidence": 0.91,
            "summary": "mock inference accepted",
            "input_echo": input_value
        },
        "metrics": {
            "queue_ms": 0.4,
            "inference_ms": round(inference_ms, 3)
        }
    }


class Handler(BaseHTTPRequestHandler):
    server_version = "CentralBrainMock/0.1"

    def log_message(self, fmt: str, *args: Any) -> None:
        print("[%s] %s" % (self.log_date_time_string(), fmt % args), flush=True)

    def send_json(self, status: int, payload: dict[str, Any]) -> None:
        encoded = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def do_GET(self) -> None:
        path = urlparse(self.path).path
        if path == "/health":
            self.send_json(200, health_payload())
        elif path == "/services":
            self.send_json(200, services_payload())
        elif path == "/vehicle/state":
            self.send_json(200, vehicle_state_payload())
        elif path == "/npu/status":
            self.send_json(200, npu_status())
        else:
            self.send_json(404, {"status": "error", "message": "unknown endpoint"})

    def do_POST(self) -> None:
        path = urlparse(self.path).path
        length = int(self.headers.get("Content-Length", "0"))
        raw_body = self.rfile.read(length) if length else b"{}"
        try:
            request = json.loads(raw_body.decode("utf-8"))
        except json.JSONDecodeError as exc:
            self.send_json(400, {"status": "error", "message": str(exc)})
            return

        if path == "/ai/infer":
            self.send_json(200, inference_payload(request))
        else:
            self.send_json(404, {"status": "error", "message": "unknown endpoint"})


def main() -> int:
    parser = argparse.ArgumentParser(description="Run the central brain mock NPU backend.")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=int(os.environ.get("CENTRAL_BRAIN_PORT", "8787")))
    args = parser.parse_args()

    server = ThreadingHTTPServer((args.host, args.port), Handler)
    print(f"Central brain mock backend listening on http://{args.host}:{args.port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("Stopping central brain mock backend", flush=True)
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
