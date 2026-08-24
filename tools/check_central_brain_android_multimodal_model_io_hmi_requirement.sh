#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, S2-HMI-003/007/008, S2-MDL-002, S2-OBS-002,
# S2-SAF-001, XSC-001/005/006, DEL-001/003/004.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTRACT="$ROOT_DIR/central-brain/contracts/central_brain_android_multimodal_model_io_hmi_requirement_v1.json"
LAYOUT="$ROOT_DIR/apk-labs/client2-central-brain/patches/main_layout.central_brain_panel.xml"
CLIENT="$ROOT_DIR/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitControlCoordinator.java"
INPUT="$ROOT_DIR/apk-labs/client2-central-brain/bridge/src/com/centralbrain/client2/CockpitMultimodalInput.java"
SDK_AIDL="$ROOT_DIR/central-brain/android-runtime/central-brain-sdk/src/debug/aidl/com/centralbrain/sdk/model/DevelopmentModelInput.aidl"
RUNTIME_STORE="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/model/DevelopmentModelInputStore.java"
SCENARIO="$ROOT_DIR/central-brain/android-runtime/runtime-service/src/main/assets/scenarios/scene.cabin.multimodal.assist.v1.json"

python3 -m json.tool "$CONTRACT" >/dev/null
python3 - "$CONTRACT" <<'PY'
import json
import sys

contract = json.load(open(sys.argv[1], encoding="utf-8"))
assert contract["schema_version"] == 1
assert contract["profile_id"] == "android13-client2-multimodal-model-io-hmi-requirement-v1"
assert contract["maturity"] == "bundled_random_frame_debug_implementation_android13_arm64_verified"
assert {"S2-HMI-008", "S2-HMI-012", "S2-HMI-013", "S2-HMI-014",
        "S2-MDL-002", "S2-OBS-002"}.issubset(
    contract["requirement_ids"])

surface = contract["surface"]
assert surface["model_input_visible"] is True
assert surface["model_output_visible"] is True
assert surface["source_must_be_actual_model_exchange"] is True
assert surface["button_label_as_model_input_allowed"] is False
assert surface["fixture_as_live_input_allowed"] is False
assert surface["controlled_frame_actual_model_exchange_allowed"] is True

text = contract["text"]
assert text["render_directly"] is True
assert text["maximum_code_points_per_item"] == 4096
assert text["truncation_must_be_explicit"] is True

image = contract["image"]
assert image["maximum_images_per_model_input"] == 1
assert image["allowlisted_mime_types"] == ["image/png", "image/jpeg"]
assert image["presentation_surface"] == "LEFT_INDEPENDENT_OVERLAY"
assert image["inline_in_trace_dialog"] is False
assert image["display_width_dp"] == 496
assert image["display_height_dp"] == 279
assert image["display_scale_type"] == "FIT_CENTER"
assert image["preserve_aspect_ratio"] is True
assert image["crop_allowed"] is False
assert image["text_and_image_visible_together"] is True
assert image["model_latency_visible_below_image"] is True
assert image["model_latency_source"] == "MODEL_PROJECTION_LATENCY_MS"
assert image["tap_opens_center_preview"] is True
assert image["preview_max_screen_width_ratio"] == 0.9
assert image["preview_max_screen_height_ratio"] == 0.85
assert image["tap_outside_dismisses_preview"] is True
assert image["back_dismisses_preview"] is True
assert image["tap_image_dismisses_preview"] is False

restriction = contract["driving_restriction"]
assert restriction["fullscreen_preview_allowed_states"] == ["PARKED", "IDLE"]
assert restriction["fullscreen_preview_blocked_states"] == [
    "MOVING_RESTRICTED", "UNKNOWN_RESTRICTED", "FAULT_RESTRICTED"]

privacy = contract["lifecycle_and_privacy"]
assert privacy["ui_process_memory_only"] is True
for key in (
    "room_persistence_allowed",
    "shared_preferences_persistence_allowed",
    "checkpoint_persistence_allowed",
    "raw_text_logging_allowed",
    "raw_image_logging_allowed",
    "github_evidence_payload_allowed",
):
    assert privacy[key] is False

claims = contract["claim_state"]
assert claims["requirement_defined"] is True
for key in (
    "model_io_hmi_implemented",
    "frontend_multimodal_ingress_bound",
    "actual_model_input_projected_to_hmi",
    "actual_model_output_projected_to_hmi",
    "image_independent_overlay_rendered",
    "model_latency_rendered_below_image",
    "image_center_preview_interaction_implemented",
    "android13_arm64_model_io_hmi_verified",
):
    assert claims[key] is True
assert claims["repository_software_requirements_complete"] is True
assert claims["open_repository_software_requirement_count"] == 0
assert claims["production_ready"] is False
assert claims["target_hardware_validated"] is False
assert claims["implementation_stage"] == "P4-R14-DATASET-IMAGE-LATENCY-SEAT"
evidence = contract["development_evidence"]
assert evidence["android_api"] == 33
assert evidence["abi"] == "arm64-v8a"
assert evidence["display"] == "1920x1080"
assert evidence["image_byte_count"] == 2244206
assert evidence["actual_model_exchange"] is True
assert evidence["image_consumption_projection_verified"] is True
assert evidence["vehicle_bus_accessed"] is False
PY

bash "$ROOT_DIR/tools/check_central_brain_production_document_set.sh" >/dev/null
grep -Fq '`P4-R4` Multimodal model I/O live HMI' \
  "$ROOT_DIR/docs/CENTRAL_BRAIN_REQUIREMENTS.md"

for marker in \
  'centralBrainMultimodalButton' \
  'centralBrainModelInputText' \
  'centralBrainModelInputImageOverlay' \
  'centralBrainModelInputImage' \
  'centralBrainModelLatencyText' \
  'centralBrainImagePreviewOverlay' \
  'centralBrainImagePreview'; do
  grep -Fq "$marker" "$LAYOUT" \
    || { echo "P4-R4 UI marker missing: $marker" >&2; exit 1; }
done
grep -Fq 'onModelInputAccepted' "$CLIENT"
grep -Fq 'MODEL OUTPUT' "$CLIENT"
grep -Fq 'PREVIEW_BLOCKED' "$CLIENT"
grep -Fq 'tap' "$CONTRACT"
grep -Fq 'ParcelFileDescriptor imageFd' "$SDK_AIDL"
grep -Fq 'consumeOwn' "$RUNTIME_STORE"
grep -Fq 'scene.cabin.multimodal.assist.v1' "$SCENARIO"
test -f "$ROOT_DIR/apk-labs/client2-central-brain/patches/res/raw/central_brain_cabin_frame.png"
printf '%s  %s\n' \
  '93441797b96c512a7b87905e4d326fbacdbf3a80e4d336d018a41224a0cd8438' \
  "$ROOT_DIR/apk-labs/client2-central-brain/patches/res/raw/central_brain_cabin_frame.png" \
  | sha256sum -c - >/dev/null

printf '%s\n' \
  'multimodal_model_io_hmi_requirement_defined=true' \
  'model_io_hmi_implemented=true' \
  'frontend_multimodal_ingress_bound=true' \
  'image_independent_overlay_implemented=true' \
  'model_latency_below_image_implemented=true' \
  'image_center_preview_interaction_implemented=true' \
  'android13_arm64_model_io_hmi_verified=true' \
  'repository_software_requirements_complete=true' \
  'open_repository_software_requirement_count=0' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'implementation_stage=P4-R14-DATASET-IMAGE-LATENCY-SEAT'
