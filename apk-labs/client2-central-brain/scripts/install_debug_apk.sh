#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SIGNED_APK="$ROOT_DIR/builds/client2-central-brain/signed/client2-central-brain.debug.apk"
PACKAGE_NAME="com.tuanjie.urasclient2"
SERIAL="${ANDROID_SERIAL:-}"
REPLACE_CONFLICTING_CLIENT2=false

usage() {
  cat <<'EOF'
Usage: install_debug_apk.sh [options]

Options:
  --serial SERIAL                   Select an adb device explicitly.
  --replace-conflicting-client2     Remove an installed Client2 only when
                                    Android reports a signer mismatch.
  -h, --help                        Show this help.
EOF
}

while (($# > 0)); do
  case "$1" in
    --serial)
      [[ $# -ge 2 ]] || { echo "--serial requires a value" >&2; exit 2; }
      SERIAL="$2"
      shift 2
      ;;
    --replace-conflicting-client2)
      REPLACE_CONFLICTING_CLIENT2=true
      shift
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

if [[ ! -f "$SIGNED_APK" ]]; then
  echo "Missing signed APK. Build it first with tools/build_client2_central_brain_demo.sh" >&2
  exit 1
fi

ADB="${ADB:-adb}"
ADB_DEVICE=("$ADB")
if [[ -n "$SERIAL" ]]; then
  ADB_DEVICE+=( -s "$SERIAL" )
fi

APK_ARGUMENT="$SIGNED_APK"
if [[ "$ADB" == *.exe ]] && command -v wslpath >/dev/null; then
  APK_ARGUMENT="$(wslpath -w "$SIGNED_APK")"
fi

set +e
INSTALL_OUTPUT="$("${ADB_DEVICE[@]}" install -r "$APK_ARGUMENT" 2>&1)"
INSTALL_STATUS=$?
set -e
if [[ $INSTALL_STATUS -eq 0 ]]; then
  printf '%s\n' "$INSTALL_OUTPUT"
  echo "Installed: $SIGNED_APK"
  exit 0
fi
if ! grep -Eq 'INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match' \
    <<<"$INSTALL_OUTPUT"; then
  printf '%s\n' "$INSTALL_OUTPUT" >&2
  exit "$INSTALL_STATUS"
fi
if [[ "$REPLACE_CONFLICTING_CLIENT2" != true ]]; then
  echo "SIGNER_MIGRATION_REQUIRED package=$PACKAGE_NAME" >&2
  echo "No package was removed. Re-run with --replace-conflicting-client2 only after approving data loss." >&2
  exit 1
fi

echo "Replacing signer-incompatible $PACKAGE_NAME; existing app data will be removed." >&2
UNINSTALL_OUTPUT="$("${ADB_DEVICE[@]}" uninstall "$PACKAGE_NAME")"
printf '%s\n' "$UNINSTALL_OUTPUT"
grep -Fq 'Success' <<<"$UNINSTALL_OUTPUT" \
  || { echo "Client2 uninstall failed" >&2; exit 1; }
"${ADB_DEVICE[@]}" install "$APK_ARGUMENT"
printf '%s\n' \
  "client2_signer_migration_performed=true" \
  "client2_conflicting_package_removed=true" \
  "client2_replacement_package=$PACKAGE_NAME" \
  "automatic_uninstall_enabled=false" \
  "Installed: $SIGNED_APK"
