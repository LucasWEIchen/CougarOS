#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-UX-003, S2-HMI-003, S2-SAF-001, S2-EFF-001, APP-004, XSC-001/005/006.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="$ROOT_DIR/apk-labs/client2-central-brain"
LAYOUT="$PROJECT/patches/main_layout.central_brain_panel.xml"
RECOVERY="$PROJECT/bridge/src/com/centralbrain/client2/CockpitRecoveryState.java"
STATE="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiState.java"
REDUCER="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiReducer.java"
COORDINATOR="$PROJECT/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
DEVICE_TEST="$ROOT_DIR/tools/test_client2_central_brain_binder.sh"

for path in "$LAYOUT" "$RECOVERY" "$STATE" "$REDUCER" "$COORDINATOR" "$DEVICE_TEST"; do
  test -f "$path"
done

python3 - "$LAYOUT" <<'PY'
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"
root = ET.parse(sys.argv[1]).getroot()

def attr(node, name):
    return node.attrib.get(ANDROID + name, "")

def node_by_id(identifier):
    expected = {f"@+id/{identifier}", f"@id/{identifier}"}
    matches = [node for node in root.iter() if attr(node, "id") in expected]
    if len(matches) != 1:
        raise SystemExit(f"expected one recovery view {identifier}, found {len(matches)}")
    return matches[0]

texts = {
    "centralBrainApprovalStateText": "Approval：UNAVAILABLE",
    "centralBrainPartialStateText": "Outcome evidence：NO EVIDENCE",
    "centralBrainCompensationStateText": "Compensation：UNAVAILABLE",
}
for identifier, marker in texts.items():
    if marker not in attr(node_by_id(identifier), "text"):
        raise SystemExit(f"{identifier} lacks fail-closed default {marker}")

buttons = {
    "centralBrainApproveButton": "central_brain_recovery_approve",
    "centralBrainRejectButton": "central_brain_recovery_reject",
    "centralBrainRetryButton": "central_brain_recovery_retry",
    "centralBrainUndoButton": "central_brain_recovery_undo",
}
for identifier, tag in buttons.items():
    node = node_by_id(identifier)
    if node.tag != "Button" or attr(node, "tag") != tag or attr(node, "enabled") != "false":
        raise SystemExit(f"{identifier} must be a disabled fail-closed command")
PY

for marker in \
  'public final class CockpitRecoveryState' \
  'SESSION_STATE_PARTIALLY_COMPLETED' \
  'return false;' \
  'EffectObservation.retryable is not delivered through Event V1' \
  'UndoHandle is not published to Client2' \
  'case "ApprovalRequested"' \
  'case "EffectVerified"' \
  'case "EffectFailed"' \
  'case "CompensationObserved"'; do
  grep -Fq -- "$marker" "$RECOVERY"
done

if grep -Eq '^import android\.' "$RECOVERY" "$STATE" "$REDUCER"; then
  echo "Client2 recovery state must remain Android-view independent" >&2
  exit 1
fi
if grep -Eiq 'approvalId|undoId|effectId|observationId|planDigest|actionDigest|payloadDigest|displayText|utterance' \
    "$RECOVERY"; then
  echo "Client2 recovery projection must not retain raw identity, digest or text payloads" >&2
  exit 1
fi

for marker in \
  'private final CockpitRecoveryState recoveryState' \
  'public CockpitRecoveryState getRecoveryState()' \
  'CockpitRecoveryState recoveryState = CockpitRecoveryState.initial()'; do
  grep -Fq -- "$marker" "$STATE"
done
for marker in \
  'next.recoveryState = current.getRecoveryState().scenarioRequested()' \
  'next.recoveryState = current.getRecoveryState().snapshot(event.sessionState)' \
  'next.recoveryState = current.getRecoveryState().runtimeEvent('; do
  grep -Fq -- "$marker" "$REDUCER"
done
for marker in \
  'renderRecoveryState(current, concise)' \
  'simulated.isApprovalInputEnabled() || recovery.isApproveEnabled()' \
  'simulated.isApprovalInputEnabled() || recovery.isRejectEnabled()' \
  'Response service：NOT PUBLISHED' \
  'Undo handle：NOT PUBLISHED' \
  'cockpit_recovery_commands_enabled=false'; do
  grep -Fq -- "$marker" "$COORDINATOR"
done

if grep -Eiq 'CarPropertyManager|android\.car|System\.loadLibrary|ioctl|sysfs|/dev/' \
    "$RECOVERY" "$STATE" "$REDUCER" "$COORDINATOR"; then
  echo "Client2 recovery UX bypasses the typed Session/Event boundary" >&2
  exit 1
fi

for marker in \
  'cockpit_approval_details_fail_closed_verified=true' \
  'cockpit_partial_outcome_projection_verified=true' \
  'cockpit_compensation_projection_verified=true' \
  'cockpit_recovery_commands_disabled_verified=true' \
  'cockpit_recovery_outside_dismiss_preserved=true'; do
  grep -Fq -- "$marker" "$DEVICE_TEST"
done

for doc_marker in \
  'README.md|P4 Client2 approval/recovery UX' \
  'docs/CENTRAL_BRAIN_REQUIREMENTS.md|P4-W07` Approval/partial/retry/undo UX' \
  'docs/CENTRAL_BRAIN_REQUIREMENTS.md|P4-W07 approval/partial/retry/undo UX trace' \
  'docs/CENTRAL_BRAIN_REQUIREMENTS.md|DEV-057 P4-W07 recovery command details' \
  'docs/CENTRAL_BRAIN_REQUIREMENTS.md|P4-W07 进展：Client2 recovery UX' \
  'docs/CENTRAL_BRAIN_REQUIREMENTS.md|P4-W07 Approval and Recovery UX' \
  'docs/CENTRAL_BRAIN_REQUIREMENTS.md|P4-W07 Approval/recovery UX Driver/HAL Boundary' \
  'docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md|Client2 P4-W07 Approval and Recovery Interfaces' \
  'docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md|R7C-E-010' \
  'docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md|P4-W07 approval/recovery UX evidence'; do
  path="${doc_marker%%|*}"
  marker="${doc_marker#*|}"
  grep -Fq -- "$marker" "$ROOT_DIR/$path"
done

bash "$ROOT_DIR/tools/check_central_brain_android_client2_hmi_reducer.sh"

printf '%s\n' \
  'cockpit_recovery_state_reducer_owned=true' \
  'cockpit_approval_details_fail_closed=true' \
  'cockpit_partial_outcome_projection=true' \
  'cockpit_compensation_projection=true' \
  'cockpit_approval_response_service_published=false' \
  'cockpit_retry_service_published=false' \
  'cockpit_undo_service_published=false' \
  'cockpit_recovery_commands_enabled=false' \
  'hardware_accessed=false'
echo "Central Brain Android Client2 approval/recovery UX check passed"
