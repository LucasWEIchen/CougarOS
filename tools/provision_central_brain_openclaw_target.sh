#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MDL-001, S2-SAF-001, S2-OBS-001, XSC-005/006, DEL-003/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SERIAL="${ANDROID_SERIAL:-}"

usage() {
  cat <<'EOF'
Usage: provision_central_brain_openclaw_target.sh [--serial SERIAL]

Prompts for the transitional OpenClaw credential without echoing it. The
credential is passed to the DUMP-protected debug Activity and held only in the
Runtime Service process. Restarting or replacing that process clears it.
EOF
}

while (($# > 0)); do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "--serial requires a value" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -f "$ROOT_DIR/env.sh" ]]; then
  # shellcheck source=/dev/null
  source "$ROOT_DIR/env.sh"
fi
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${ANDROID_HOME:?ANDROID_HOME or ANDROID_SDK_ROOT must point to the Android SDK}"
ADB="${ADB:-$ANDROID_HOME/platform-tools/adb}"
[[ -x "$ADB" ]] || { echo "adb is not executable" >&2; exit 1; }

if [[ -z "$SERIAL" ]]; then
  mapfile -t DEVICES < <("$ADB" devices | tr -d '\r' \
    | awk 'NR > 1 && $2 == "device" {print $1}')
  [[ ${#DEVICES[@]} -eq 1 ]] \
    || { echo "expected exactly one online adb device" >&2; exit 1; }
  SERIAL="${DEVICES[0]}"
fi
DEVICE=("$ADB" -s "$SERIAL")

SDK="$("${DEVICE[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
ABI="$("${DEVICE[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
[[ "$SDK" == 33 && "$ABI" == arm64-v8a ]] \
  || { echo "target must be Android API 33 ARM64" >&2; exit 1; }

# This production image suppresses application INFO tags unless explicitly enabled.
"${DEVICE[@]}" shell setprop log.tag.CentralBrainOpenClaw VERBOSE

read -r -s -p "OpenClaw target credential: " TOKEN </dev/tty
printf '\n' >/dev/tty
if [[ ${#TOKEN} -lt 8 || ${#TOKEN} -gt 256 || "$TOKEN" =~ [^\!-\~] ]]; then
  unset TOKEN
  echo "credential must be 8..256 printable non-space ASCII characters" >&2
  exit 1
fi

"${DEVICE[@]}" logcat -c >/dev/null
START_OUTPUT="$("${DEVICE[@]}" shell am start -W \
  -n com.centralbrain.runtime/.model.OpenClawCredentialProvisioningActivity \
  --es openclaw_token "$TOKEN")"
unset TOKEN
grep -Fq 'Status: ok' <<<"$START_OUTPUT" \
  || { echo "credential provisioning Activity failed" >&2; exit 1; }

for _ in {1..20}; do
  SAFE_LOG="$("${DEVICE[@]}" logcat -d -s CentralBrainOpenClaw:I '*:S' \
    | tail -n 20)"
  if grep -Fq 'openclaw_credential_provisioned=true' <<<"$SAFE_LOG" \
      && grep -Fq 'token_logged=false' <<<"$SAFE_LOG" \
      && grep -Fq 'persisted=false' <<<"$SAFE_LOG"; then
    printf '%s\n' \
      'openclaw_credential_provisioned=true' \
      'credential_echoed=false' \
      'credential_persisted=false' \
      'credential_logged=false' \
      'production_ready=false'
    exit 0
  fi
  sleep 0.25
done

echo "credential provisioning did not emit the safe completion marker" >&2
exit 1
