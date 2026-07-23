#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-MDL-001/002, S2-OBS-001/002, S2-SAF-001,
# XSC-001/005/006, DEL-001/003/004/005. Stage: P7-R5-MMDEV.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME="central-brain/android-runtime/runtime-service"
ENGINE="$RUNTIME/src/debug/java/com/centralbrain/runtime/model/OpenClawInferenceEngine.java"
ENDPOINT="$RUNTIME/src/main/java/com/centralbrain/runtime/model/OpenClawEndpointConfig.java"
TEST="$RUNTIME/src/testDebug/java/com/centralbrain/runtime/model/OpenClawInferenceEngineTest.java"
RUNNER="tools/run_central_brain_wsl_openclaw_multimodal_probe.sh"
CONTRACT="central-brain/contracts/central_brain_android_openclaw_multimodal_gateway_v1.json"
DESIGN="docs/CENTRAL_BRAIN_OPENCLAW_MULTIMODAL_DEVELOPMENT.md"
IMAGE="central-brain/test-assets/multimodal/2025-SUV-OMS-cabin-photo.png"

for file in "$ENGINE" "$ENDPOINT" "$TEST" "$RUNNER" "$CONTRACT" "$DESIGN" "$IMAGE"; do
  [[ -f "$ROOT_DIR/$file" ]] || { echo "missing multimodal gateway file: $file" >&2; exit 1; }
done

require_text() {
  grep -Fq -- "$2" "$ROOT_DIR/$1" \
    || { echo "missing multimodal marker '$2' in $1" >&2; exit 1; }
}

for marker in \
  'MAX_IMAGE_BYTES = 6 * 1024 * 1024' \
  'registerScenarioImageAttachment' \
  '"image/png"' '"image/jpeg"' \
  'attachment.addProperty("type", "image")' \
  'attachment.addProperty("mimeType"' \
  'attachment.addProperty("fileName"' \
  'params.add("attachments", attachments)' \
  'raw_image_logged=false'; do
  require_text "$ENGINE" "$marker"
done
require_text "$ENDPOINT" 'MAX_MULTIMODAL_CHAT_FRAME_BYTES = 8_500_000'
for marker in \
  'boundedImageAttachmentIsDigestBoundAndForwardedWithText' \
  'invalidImageMimeSignatureSizeAndDigestConflictFailClosed'; do
  require_text "$TEST" "$marker"
done
for marker in \
  'qwen3.6:27b' 'attachments:' 'text_present=true image_present=true' \
  'multimodal_probe_complete=true implementation_stage=P7-R5-MMDEV'; do
  require_text "$RUNNER" "$marker"
done

python3 -B - "$ROOT_DIR/$CONTRACT" <<'PY'
import json
import pathlib
import sys

contract = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
assert contract["schema_version"] == 2
assert contract["implementation_stage"] == "P7-R5-MMDEV"
route = contract["development_route"]
assert route["text_and_image_in_same_chat_send"] is True
assert route["maximum_images_per_request"] == 1
assert route["allowlisted_mime_types"] == ["image/png", "image/jpeg"]
assert route["maximum_image_bytes"] == 6291456
assert route["maximum_authenticated_chat_frame_bytes"] == 8500000
assert route["preauthentication_frame_limit_unchanged"] is True
probe = contract["controlled_probe"]
assert probe["image_sha256"] == "93441797b96c512a7b87905e4d326fbacdbf3a80e4d336d018a41224a0cd8438"
assert probe["image_stored_in_repository"] is True
assert probe["occupant_count"] == 3
assert probe["visible_occupant_holding_bottle"] is True
assert probe["all_visible_occupants_belted"] is True
assert probe["multimodal_probe_complete"] is True
validation = contract["development_validation"]
assert validation["frontend_controlled_frame_binder_contract_bound"] is True
assert validation["android13_arm64_multimodal_verified"] is True
assert validation["client2_model_input_output_hmi_verified"] is True
assert validation["simulated_effect_loop_verified"] is True
boundaries = contract["open_boundaries"]
for key in boundaries:
    assert boundaries[key] is False
PY

[[ "$(stat -c '%s' "$ROOT_DIR/$IMAGE")" == "2244206" ]] \
  || { echo "controlled multimodal image size mismatch" >&2; exit 1; }
[[ "$(sha256sum "$ROOT_DIR/$IMAGE" | awk '{print $1}')" \
    == "93441797b96c512a7b87905e4d326fbacdbf3a80e4d336d018a41224a0cd8438" ]] \
  || { echo "controlled multimodal image digest mismatch" >&2; exit 1; }

for doc in README.md docs/CENTRAL_BRAIN_GRANULAR_REQUIREMENT_CATALOG.md \
  docs/CENTRAL_BRAIN_ROADMAP.md docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md \
  docs/CENTRAL_BRAIN_ARCHITECTURE_DEVIATIONS.md docs/CENTRAL_BRAIN_ARCHITECTURE_ISSUES.md \
  docs/CENTRAL_BRAIN_DELIVERY_TARGETS.md docs/CENTRAL_BRAIN_DRIVER_INTERFACE_SUPPORT.md; do
  require_text "$doc" 'P7-R5-MMDEV'
done

printf '%s\n' \
  'android_openclaw_multimodal_attachment_implemented=true' \
  'wsl_openclaw_ollama_multimodal_verified=true' \
  'frontend_controlled_frame_binder_contract_bound=true' \
  'android13_arm64_multimodal_verified=true' \
  'frontend_live_camera_ingress_bound=false' \
  'direct_npu_accessed=false' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P7-R5-MMDEV'
