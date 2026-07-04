# Linux Deployment Sample

Req IDs: DEL-001, DEL-002, DEL-003, DEL-004, XSC-002, XSC-003, XSC-005, XSC-006, NV-P-002.

This directory is a Linux cockpit-domain deployment sample for the current
Central Brain prototype. DEL-001 remains the Android primary path; these files
document the Linux counterpart required by DEL-002..004. It does not add driver,
HAL, SOME/IP, DDS, MQTT, NPU, or virtualization code.

## Files

- `central-brain.env.example`: environment template for gateway port, semantic
  base URL, Unix socket path, gateway JSONL audit log path, and IPC precheck
  JSONL audit log path.
- `systemd/central-brain-backend.service`: backend semantic gateway service.
- `systemd/central-brain-linux-ipc.service`: Unix socket Protocol Binding
  daemon service.

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
sudo systemctl enable --now central-brain-backend.service central-brain-linux-ipc.service
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
sudo test -s /var/log/central-brain/audit.jsonl || true
sudo test -s /var/log/central-brain/ipc-audit.jsonl || true
```

## Deployment Assumptions

- The systemd units are samples for Linux delivery, not a production packaging
  format.
- The backend remains the active REST prototype binding; the IPC daemon
  preserves Uni Info Bus/SOA semantic operations, applies local Runtime &
  Governance precheck to SOA service invocations, and forwards allowed calls to
  that gateway.
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
  pre-forwarding governance decisions separately from the backend gateway audit.
- `/run/central-brain/gateway.sock` is group-readable/writable for local
  same-SoC clients. Production integration should map this group to cockpit
  service identities.
