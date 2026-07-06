# Linux Deployment Sample

Req IDs: DEL-001, DEL-002, DEL-003, DEL-004, XSC-002, XSC-003, XSC-005, XSC-006, NV-P-002, NV-P-003.

This directory is a Linux cockpit-domain deployment sample for the current
Central Brain prototype. DEL-001 remains the Android primary path; these files
document the Linux counterpart required by DEL-002..004. It does not add driver,
HAL, SOME/IP, DDS, MQTT, NPU, or virtualization code.

## Files

- `central-brain.env.example`: environment template for gateway port, semantic
  base URL, Unix socket paths, gRPC/RPC sample host/port, gateway JSONL audit
  log path, shared governance audit log path, IPC fallback audit log path, and
  gRPC/RPC fallback audit log path.
- `central-brain.package-profile.json`: machine-readable Linux cockpit-domain
  sample profile for install root, service identity, environment file,
  runtime/log directories, service units, Req IDs, hardening, and non-goals.
- `systemd/central-brain-backend.service`: backend semantic gateway service.
- `systemd/central-brain-governance.service`: Linux Runtime & Governance socket
  sample for shared `governance.precheck`, `governance.runtime.get`, and
  `audit.recent.get` diagnostics.
- `systemd/central-brain-linux-ipc.service`: Unix socket Protocol Binding
  daemon service that uses the governance socket before falling back locally.
- `systemd/central-brain-linux-grpc.service`: gRPC/RPC contract sample service
  that mirrors the proto envelope over JSON TCP and uses the governance socket
  before falling back locally.
- `../../../tools/check_central_brain_linux_systemd_hardening.sh`: static check
  for service identity, log/runtime write paths, and systemd sandbox directives
  on the Linux delivery units.
- `../../../tools/check_central_brain_linux_package_profile.sh`: static check
  that the package profile, env example, and systemd units stay aligned.

## Integration Path

Copy the repo to the target path used by the sample units:

```bash
sudo mkdir -p /opt/central-brain
sudo cp -a /path/to/appDev /opt/central-brain/appDev
```

Create the service user and environment file:

```bash
sudo useradd --system --home /opt/central-brain --shell /usr/sbin/nologin centralbrain
sudo mkdir -p /etc/central-brain
sudo cp /opt/central-brain/appDev/central-brain/deploy/linux/central-brain.env.example \
  /etc/central-brain/central-brain.env
sudo chown -R centralbrain:centralbrain /opt/central-brain/appDev
```

Install and start the units:

```bash
sudo cp /opt/central-brain/appDev/central-brain/deploy/linux/systemd/*.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now central-brain-backend.service central-brain-governance.service central-brain-linux-ipc.service central-brain-linux-grpc.service
```

Validate the semantic gateway and local IPC binding:

```bash
curl -fsS http://127.0.0.1:8787/health
CENTRAL_BRAIN_BASE_URL=http://127.0.0.1:8787 \
  python3 /opt/central-brain/appDev/central-brain/linux-cli/central_brain_cli.py state
CENTRAL_BRAIN_IPC_SOCKET=/run/central-brain/gateway.sock \
  python3 /opt/central-brain/appDev/central-brain/bindings/linux/ipc/central_brain_ipc_client.py state
CENTRAL_BRAIN_IPC_SOCKET=/run/central-brain/gateway.sock \
  python3 /opt/central-brain/appDev/central-brain/bindings/linux/ipc/central_brain_ipc_client.py governance-precheck
CENTRAL_BRAIN_IPC_SOCKET=/run/central-brain/gateway.sock \
  python3 /opt/central-brain/appDev/central-brain/bindings/linux/ipc/central_brain_ipc_client.py infer-denied
CENTRAL_BRAIN_GRPC_HOST=127.0.0.1 CENTRAL_BRAIN_GRPC_PORT=18788 \
  python3 /opt/central-brain/appDev/central-brain/bindings/linux/grpc/central_brain_grpc_client.py infer-denied
sudo test -s /var/log/central-brain/audit.jsonl || true
sudo test -s /var/log/central-brain/governance-audit.jsonl || true
sudo test -s /var/log/central-brain/ipc-audit.jsonl || true
sudo test -s /var/log/central-brain/grpc-audit.jsonl || true
```

Validate the unit hardening contract before installing to a target image:

```bash
bash tools/check_central_brain_linux_package_profile.sh
bash tools/check_central_brain_linux_systemd_hardening.sh
```

## Deployment Assumptions

- The systemd units are samples for Linux delivery, not a production packaging
  format. `central-brain.package-profile.json` is a sample profile for target
  integration review; it is not a dpkg/rpm recipe or security certification.
- The systemd units set `NoNewPrivileges`, `PrivateTmp`, `PrivateDevices`,
  `ProtectSystem=strict`, `ProtectHome`, `RestrictSUIDSGID`,
  `LockPersonality`, `PYTHONDONTWRITEBYTECODE`, and explicit
  `ReadWritePaths`. These are sample hardening defaults for DEL-002/DEL-004,
  not a substitute for target distribution packaging, LSM policy, or security
  certification.
- The backend remains the active REST prototype binding; the IPC daemon
  preserves Uni Info Bus/SOA semantic operations, calls the shared Linux
  Runtime & Governance socket for SOA precheck when configured, falls back to
  local precheck if that socket is unavailable, and forwards allowed calls to
  that gateway.
- The gRPC/RPC sample is intentionally a dependency-free JSON TCP wrapper
  around the proto contract because this workspace does not include `grpcio`.
  It validates NV-P-003 RPC names, GatewayRequest/GatewayResponse fields, Req
  IDs, and shared governance precheck behavior; production can replace the
  transport with real gRPC.
- `governance-precheck` exposes the same XSC-005/NV-G-002/NV-G-004..007
  decision envelope for Linux clients without dispatching a service; by default
  it does not reserve the QoS fixed-window slot.
- Permission enforcement in this sample is process/user based plus Runtime &
  Governance policy checks. Android permission parity is documented in
  `docs/CENTRAL_BRAIN_PLATFORM_DELTA.md`.
- `CENTRAL_BRAIN_AUDIT_LOG` enables a JSONL Runtime & Governance audit sample
  for XSC-005/NV-G-007/DEL-002. It is intentionally a local integration aid;
  production still needs rotation, export, and access-control hardening.
- `CENTRAL_BRAIN_IPC_AUDIT_LOG` enables the Linux IPC binding to persist
  local fallback pre-forwarding governance decisions separately from the backend
  gateway audit.
- `CENTRAL_BRAIN_GOVERNANCE_AUDIT_LOG` enables the shared Linux governance
  socket sample to persist precheck decisions used by IPC binding clients and
  expose them through direct `audit.recent.get` diagnostics.
- `CENTRAL_BRAIN_GRPC_AUDIT_LOG` enables the Linux gRPC/RPC sample to persist
  local fallback pre-forwarding governance decisions separately from the backend
  gateway and IPC binding audit logs.
- `/run/central-brain/gateway.sock` is group-readable/writable for local
  same-SoC clients. Production integration should map this group to cockpit
  service identities.
