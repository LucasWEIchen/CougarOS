#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-UX-001, S2-HMI-003/006, S2-EVT-001, APP-004, XSC-001/005/006.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT="$ROOT_DIR/apk-labs/client2-central-brain"
LAYOUT="$PROJECT/patches/main_layout.central_brain_panel.xml"
TIMELINE="$PROJECT/bridge/src/com/centralbrain/client2/CockpitExecutionTimeline.java"
STATE="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiState.java"
REDUCER="$PROJECT/bridge/src/com/centralbrain/client2/CockpitHmiReducer.java"
COORDINATOR="$PROJECT/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
DEVICE_TEST="$ROOT_DIR/tools/test_client2_central_brain_binder.sh"

for path in "$LAYOUT" "$TIMELINE" "$STATE" "$REDUCER" "$COORDINATOR" "$DEVICE_TEST"; do
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
        raise SystemExit(f"expected one execution view {identifier}, found {len(matches)}")
    return matches[0]

required = {
    "centralBrainExecutionSurface",
    "centralBrainExecutionSummaryText",
    "centralBrainTimelineIntentText",
    "centralBrainTimelineContextText",
    "centralBrainTimelinePlanText",
    "centralBrainTimelinePolicyText",
    "centralBrainTimelineGraphText",
    "centralBrainTimelineEffectText",
    "centralBrainTimelineReadbackText",
    "centralBrainExecutionActionsText",
    "centralBrainExecutionChainText",
}
for identifier in required:
    node_by_id(identifier)

surface = node_by_id("centralBrainExecutionSurface")
if surface.tag != "ScrollView" or attr(surface, "layout_height") != "match_parent":
    raise SystemExit("execution timeline must scroll inside the fixed HMI surface")

expected_defaults = {
    "centralBrainTimelinePlanText": "03 Plan：NOT PUBLISHED",
    "centralBrainTimelineGraphText": "05 Graph：NOT WIRED",
    "centralBrainTimelineEffectText": "06 Effect：NOT DISPATCHED",
    "centralBrainTimelineReadbackText": "07 Readback：UNAVAILABLE",
}
for identifier, text in expected_defaults.items():
    if attr(node_by_id(identifier), "text") != text:
        raise SystemExit(f"{identifier} must keep fail-closed default {text}")
PY

for marker in \
  'public static final int MAX_TRACE_ITEMS = 8' \
  'public enum Phase { INTENT, CONTEXT, PLAN, POLICY, GRAPH, EFFECT, READBACK }' \
  'EventContract.validateEvent(event)' \
  'Status.NOT_PUBLISHED' \
  'Status.NOT_WIRED' \
  'Status.NOT_DISPATCHED' \
  'event.observationQuality == EventContract.QUALITY_FRESH'; do
  grep -Fq -- "$marker" "$TIMELINE"
done

if grep -Eq '^import android\.' "$TIMELINE" "$STATE" "$REDUCER"; then
  echo "execution timeline domain state must remain Android-view independent" >&2
  exit 1
fi

for marker in \
  'next.executionTimeline = current.getExecutionTimeline()' \
  '.snapshot(event.sessionState, event.activePlanRevision)' \
  '.runtimeEvent(event.timelineEvent)' \
  'CockpitExecutionTimeline.ProjectedEvent.from(runtimeEvent)'; do
  grep -Fq -- "$marker" "$REDUCER"
done

for marker in \
  'renderExecutionTimeline(current, presentationMode)' \
  'cockpit_execution_timeline_implemented=true' \
  'cockpit_execution_timeline_reducer_owned=true' \
  'cockpit_execution_typed_event_projection=true' \
  'cockpit_execution_plan_published=false' \
  'cockpit_execution_effect_dispatch_enabled=false' \
  'cockpit_execution_readback_available=false' \
  'Media STOP：' \
  'Navigation CANCEL：'; do
  grep -Fq -- "$marker" "$COORDINATOR"
done

if grep -Eiq \
    'CarPropertyManager|android\.car|System\.loadLibrary|ioctl|sysfs|/dev/' \
    "$TIMELINE" "$STATE" "$REDUCER" "$COORDINATOR"; then
  echo "execution timeline bypasses the typed Session/Event boundary" >&2
  exit 1
fi

for marker in \
  'cockpit_execution_timeline_verified=true' \
  'cockpit_execution_plan_not_published_verified=true' \
  'cockpit_execution_graph_not_wired_verified=true' \
  'cockpit_execution_effect_not_dispatched_verified=true' \
  'cockpit_execution_readback_unavailable_verified=true' \
  'cockpit_execution_media_navigation_projection_verified=true' \
  'cockpit_execution_typed_event_trace_verified=true'; do
  grep -Fq -- "$marker" "$DEVICE_TEST"
done

for doc_marker in \
  'README.md|P4 Client2 observable execution timeline' \
  'docs/CENTRAL_BRAIN_REQUIREMENTS.md|P4-W06` Plan/effect execution timeline' \
  'docs/CENTRAL_BRAIN_REQUIREMENTS.md|P4-W06 observable execution timeline trace' \
  'docs/CENTRAL_BRAIN_REQUIREMENTS.md|DEV-056 P4-W06 timeline projection' \
  'docs/CENTRAL_BRAIN_REQUIREMENTS.md|P4-W06 进展：Client2 Execution surface' \
  'docs/CENTRAL_BRAIN_REQUIREMENTS.md|P4-W06 Observable Execution Timeline' \
  'docs/CENTRAL_BRAIN_REQUIREMENTS.md|P4-W06 Observable execution timeline Driver/HAL Boundary' \
  'docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md|Client2 P4-W06 Observable Execution Timeline Interfaces' \
  'docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md|R7C-E-009' \
  'docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md|P4-W06 observable execution timeline evidence'; do
  path="${doc_marker%%|*}"
  marker="${doc_marker#*|}"
  grep -Fq -- "$marker" "$ROOT_DIR/$path"
done

bash "$ROOT_DIR/tools/check_central_brain_android_client2_hmi_reducer.sh"

printf '%s\n' \
  'cockpit_execution_timeline_implemented=true' \
  'cockpit_execution_timeline_reducer_owned=true' \
  'cockpit_execution_typed_event_projection=true' \
  'cockpit_execution_trace_capacity=8' \
  'cockpit_execution_plan_published=false' \
  'cockpit_execution_effect_dispatch_enabled=false' \
  'cockpit_execution_readback_available=false' \
  'hardware_accessed=false'
echo "Central Brain Android Client2 observable execution timeline check passed"
