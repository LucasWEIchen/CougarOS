#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

require_file() {
  local path="$1"
  if [[ ! -f "$ROOT_DIR/$path" ]]; then
    echo "missing Android capability policy file: $path" >&2
    exit 1
  fi
}

require_text() {
  local path="$1"
  local pattern="$2"
  if ! grep -Fq -- "$pattern" "$ROOT_DIR/$path"; then
    echo "missing Android capability policy pattern '$pattern' in $path" >&2
    exit 1
  fi
}

SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
GOVERNANCE_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainGovernanceService.java"
POLICY="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/policy/CallerCapabilityPolicy.java"
LOADER="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/policy/AndroidCapabilityPolicyLoader.java"
POLICY_XML="central-brain/android-runtime/runtime-service/src/main/res/xml/central_brain_capability_policy.xml"
DEBUG_POLICY_XML="central-brain/android-runtime/runtime-service/src/debug/res/xml/central_brain_capability_policy.xml"
POLICY_TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/policy/CallerCapabilityPolicyTest.java"
PROBE_GRADLE="central-brain/android-runtime/policy-probe/build.gradle.kts"
PROBE_MANIFEST="central-brain/android-runtime/policy-probe/src/main/AndroidManifest.xml"
PROBE_ACTIVITY="central-brain/android-runtime/policy-probe/src/main/java/com/centralbrain/policyprobe/CapabilityPolicyProbeActivity.java"
DEVICE_TEST="tools/test_central_brain_android_capability_policy.sh"

for path in \
  "$SERVICE" "$GOVERNANCE_SERVICE" "$POLICY" "$LOADER" "$POLICY_XML" "$DEBUG_POLICY_XML" "$POLICY_TEST" \
  "$PROBE_GRADLE" "$PROBE_MANIFEST" "$PROBE_ACTIVITY" "$DEVICE_TEST"; do
  require_file "$path"
done

for capability in \
  runtime.session.protocol.read \
  runtime.session.open \
  runtime.session.read.own \
  runtime.session.cancel.own \
  runtime.event.protocol.read \
  runtime.event.read.own \
  runtime.event.subscribe.own; do
  require_text "$POLICY" "$capability"
  require_text "$POLICY_XML" "$capability"
  require_text "$DEBUG_POLICY_XML" "$capability"
done
require_text "$DEBUG_POLICY_XML" 'packageName="com.centralbrain.sdk.test"'
if grep -Fq 'com.centralbrain.sdk.test' "$ROOT_DIR/$POLICY_XML"; then
  echo "instrumentation principal must not enter the production capability policy" >&2
  exit 1
fi

for capability in \
  runtime.protocol.read \
  runtime.task.submit \
  runtime.task.status.own \
  runtime.task.cancel.own \
  governance.protocol.read \
  governance.action.evaluate \
  governance.approval.request \
  governance.approval.status.own \
  governance.approval.cancel.own \
  runtime.diagnostics.read; do
  require_text "$POLICY" "$capability"
  require_text "$POLICY_XML" "$capability"
  require_text "$DEVICE_TEST" "$capability"
done

require_text "$POLICY" "PACKAGE_NOT_CONFIGURED"
require_text "$POLICY" "CURRENT_SIGNER_MISMATCH"
require_text "$POLICY" "CAPABILITY_NOT_GRANTED"
require_text "$POLICY" "requiredCurrentSignerSha256.equals"
require_text "$LOADER" '"runtime-current"'
require_text "$LOADER" 'defaultDecision must be deny'
require_text "$SERVICE" "resolveAuthorizedCaller(Capability.PROTOCOL_READ)"
require_text "$SERVICE" "resolveAuthorizedCaller(Capability.TASK_SUBMIT)"
require_text "$SERVICE" "resolveAuthorizedCaller(Capability.TASK_STATUS_OWN)"
require_text "$SERVICE" "resolveAuthorizedCaller(Capability.TASK_CANCEL_OWN)"
require_text "central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainDiagnosticService.java" "Capability.DIAGNOSTICS_READ"
require_text "$GOVERNANCE_SERVICE" "Capability.GOVERNANCE_PROTOCOL_READ"
require_text "$GOVERNANCE_SERVICE" "Capability.ACTION_EVALUATE"
require_text "$GOVERNANCE_SERVICE" "Capability.APPROVAL_REQUEST"
require_text "$GOVERNANCE_SERVICE" "Capability.APPROVAL_STATUS_OWN"
require_text "$GOVERNANCE_SERVICE" "Capability.APPROVAL_CANCEL_OWN"
require_text "$SERVICE" "capability_default=deny"
require_text "$PROBE_MANIFEST" 'android:permission="android.permission.DUMP"'
require_text "$PROBE_MANIFEST" 'android:testOnly="true"'
require_text "$PROBE_MANIFEST" 'com.centralbrain.permission.BIND_RUNTIME'
require_text "$PROBE_MANIFEST" 'com.centralbrain.permission.ACCESS_DIAGNOSTICS'
require_text "$PROBE_MANIFEST" 'com.centralbrain.permission.BIND_GOVERNANCE'
require_text "$PROBE_ACTIVITY" "capability_probe_complete=true"
require_text "$PROBE_ACTIVITY" "governance_bind_succeeded=true"
require_text "$DEVICE_TEST" "unknown_client_default_deny_verified=true"
require_text "$DEVICE_TEST" "diagnostic_capability_default_deny_verified=true"
require_text "$DEVICE_TEST" "governance_capability_default_deny_verified=true"
require_text "$DEVICE_TEST" "test_only_install_enforced=true"
require_text "central-brain/android-runtime/settings.gradle.kts" 'include(":policy-probe")'
require_text "$PROBE_GRADLE" 'variantBuilder.enable = false'

python3 - "$ROOT_DIR/$POLICY_XML" <<'PY'
import pathlib
import sys
import xml.etree.ElementTree as ET

path = pathlib.Path(sys.argv[1])
root = ET.parse(path).getroot()
if root.tag != "capability-policy":
    raise SystemExit("capability policy root must be capability-policy")
if root.attrib != {"version": "1", "defaultDecision": "deny"}:
    raise SystemExit("capability policy root attributes are not strict default-deny V1")
principals = list(root)
if len(principals) != 3:
    raise SystemExit(
        "R7B baseline must contain Runtime diagnostic, Demo and Client2 principals"
    )
expected = {
    "com.centralbrain.runtime": {"runtime.diagnostics.read"},
    "com.centralbrain.demo": {
        "runtime.protocol.read",
        "runtime.task.submit",
        "runtime.task.status.own",
        "runtime.task.cancel.own",
        "runtime.session.protocol.read",
        "runtime.session.open",
        "runtime.session.read.own",
        "runtime.session.cancel.own",
        "runtime.event.protocol.read",
        "runtime.event.read.own",
        "runtime.event.subscribe.own",
        "governance.protocol.read",
        "governance.action.evaluate",
        "governance.approval.request",
        "governance.approval.status.own",
        "governance.approval.cancel.own",
    },
    "com.tuanjie.urasclient2": {
        "runtime.protocol.read",
        "runtime.task.submit",
        "runtime.task.status.own",
        "runtime.task.cancel.own",
        "runtime.session.protocol.read",
        "runtime.session.open",
        "runtime.session.read.own",
        "runtime.session.cancel.own",
        "runtime.event.protocol.read",
        "runtime.event.read.own",
        "runtime.event.subscribe.own",
    },
}
actual_by_package = {}
for principal in principals:
    if principal.tag != "principal" or set(principal.attrib) != {"packageName", "signer"}:
        raise SystemExit("R7B principal shape is invalid")
    if principal.attrib["signer"] != "runtime-current":
        raise SystemExit("R7B principal must use runtime-current signer")
    package = principal.attrib["packageName"]
    if package in actual_by_package:
        raise SystemExit("capability policy contains a duplicate principal")
    actual = set()
    for child in principal:
        if child.tag != "capability" or set(child.attrib) != {"name"} or list(child):
            raise SystemExit("capability policy contains an invalid capability element")
        if child.text and child.text.strip():
            raise SystemExit("capability entries must not contain text")
        if child.attrib["name"] in actual:
            raise SystemExit("capability policy contains a duplicate capability")
        actual.add(child.attrib["name"])
    actual_by_package[package] = actual
if actual_by_package != expected:
    raise SystemExit(f"capability policy mismatch: {actual_by_package}")
if "*" in ET.tostring(root, encoding="unicode"):
    raise SystemExit("capability policy must not use wildcard principals")
PY

if grep -R -Fq "android.permission.INTERNET" \
    "$ROOT_DIR/central-brain/android-runtime/policy-probe"; then
  echo "capability policy probe must not request network access" >&2
  exit 1
fi
if grep -Fq ":policy-probe:" "$ROOT_DIR/tools/build_central_brain_android_runtime.sh"; then
  echo "test-only policy probe must not become a standard delivery artifact" >&2
  exit 1
fi

bash "$ROOT_DIR/tools/check_central_brain_android_job_supervisor.sh"
bash "$ROOT_DIR/tools/check_central_brain_android_aidl_contract.sh"

echo "Central Brain Android capability policy check passed"
