#!/usr/bin/env bash
set -euo pipefail

# Req IDs: S2-GRF-001, NV-G-003/006/007, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRAPH_ROOT="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/graph"
VALUE="$GRAPH_ROOT/CheckpointValue.java"
CONTRACT="$GRAPH_ROOT/CheckpointSerializer.java"
ENVELOPE="$GRAPH_ROOT/CheckpointEnvelope.java"
SERIALIZER="$GRAPH_ROOT/JsonPrimitiveCheckpointSerializer.java"
TEST="central-brain/android-runtime/runtime-service/src/test/java/com/centralbrain/runtime/graph/CheckpointSerializerTest.java"
PROBE="central-brain/android-runtime/runtime-service/src/debug/java/com/centralbrain/runtime/graph/CheckpointSerializerProbeActivity.java"
MANIFEST="central-brain/android-runtime/runtime-service/src/debug/AndroidManifest.xml"
MAIN_MANIFEST="central-brain/android-runtime/runtime-service/src/main/AndroidManifest.xml"
GRAPH_RUNTIME="$GRAPH_ROOT/AgentGraphRuntime.java"
RUNTIME_SERVICE="central-brain/android-runtime/runtime-service/src/main/java/com/centralbrain/runtime/CentralBrainRuntimeService.java"
INSTALLER="tools/install_central_brain_android_runtime.sh"

require_text() {
  if [[ "$1" == "README.md" || "$1" == "$ROOT_DIR/README.md" ]]; then
    grep -Fq -- 'docs/CENTRAL_BRAIN_REQUIREMENTS.md' "$ROOT_DIR/README.md" \
      || { echo "canonical README link missing" >&2; exit 1; }
    return 0
  fi
  case "$1" in
    *docs/CENTRAL_BRAIN_REQUIREMENTS.md|*docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md|*docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md)
      local canonical_doc_path="$1"
      [[ "$canonical_doc_path" = /* ]] || canonical_doc_path="$ROOT_DIR/$canonical_doc_path"
      grep -Fq -- 'production_document_scope=true' "$canonical_doc_path" \
        || { echo "canonical production document marker missing: $canonical_doc_path" >&2; exit 1; }
      return 0
      ;;
  esac
  local file="$1"
  local marker="$2"
  grep -Fq -- "$marker" "$ROOT_DIR/$file" \
    || { echo "P3-W03 marker missing in $file: $marker" >&2; exit 1; }
}

for file in "$VALUE" "$CONTRACT" "$ENVELOPE" "$SERIALIZER" "$TEST" \
    "$PROBE" "$MANIFEST" "$MAIN_MANIFEST" "$GRAPH_RUNTIME" \
    "$RUNTIME_SERVICE" "$INSTALLER"; do
  [[ -f "$ROOT_DIR/$file" ]] \
    || { echo "P3-W03 file missing: $file" >&2; exit 1; }
done

for marker in \
  'public final class CheckpointValue' \
  'MAX_ABSOLUTE_INTEGER = 1_000_000_000_000L' \
  'MAX_DECIMAL_SCALE = 6' \
  'MAX_CONTAINER_ITEMS = 64' \
  'public static CheckpointValue enumName' \
  'checkpoint privileged material keys are forbidden' \
  'Collections.unmodifiableList' \
  'Collections.unmodifiableMap'; do
  require_text "$VALUE" "$marker"
done

for marker in \
  'public interface CheckpointSerializer' \
  'MAX_CHECKPOINT_BYTES = 64 * 1_024' \
  'MAX_PAYLOAD_DEPTH = 8' \
  'interface PayloadCodec' \
  'final class Registration' \
  'CheckpointEnvelope create(' \
  'CheckpointEnvelope deserialize(byte[] encoded)' \
  'decodePayload(CheckpointEnvelope envelope'; do
  require_text "$CONTRACT" "$marker"
done

for marker in \
  'public final class CheckpointEnvelope' \
  'schemaVersion' \
  'type' \
  'nodeId' \
  'planDigest' \
  'contextDigest' \
  'payload' \
  'digest' \
  'createdAtEpochMs'; do
  require_text "$ENVELOPE" "$marker"
done

for marker in \
  'public final class JsonPrimitiveCheckpointSerializer' \
  'reader.setStrictness(Strictness.STRICT)' \
  'duplicate checkpoint registration' \
  'checkpoint digest does not match' \
  'checkpoint JSON is not canonical' \
  'checkpoint payload nesting is too deep' \
  'checkpoint token budget exceeded' \
  'payload.getClass() != registration.getPayloadClass()' \
  'canonicalPositiveLong(' \
  'field(builder, "createdAt", quote(Long.toString(createdAtEpochMs)))' \
  'DIGEST_DOMAIN = "central-brain.checkpoint.v1"'; do
  require_text "$SERIALIZER" "$marker"
done
require_text "$TEST" '1_700_000_000_000L'
require_text "$PROBE" 'CURRENT_EPOCH_SAMPLE_MS = 1_700_000_000_000L'
require_text "$PROBE" '"\"createdAt\":\"1700000000000\""'

for test_name in \
  registeredDtoRoundTripsThroughImmutableDigestBoundEnvelope \
  canonicalJsonAndDigestAreDeterministicAcrossEquivalentDtos \
  unknownTypeVersionAndPayloadClassFailClosed \
  malformedDuplicateUnknownAndTrailingJsonAreRejected \
  oversizeDepthAndTokenBudgetsAreEnforced \
  tamperedDigestAndNonCanonicalJsonAreRejected \
  securityCorpusCannotRequestClassReflectionOrJavaSerialization \
  codecRejectsUnknownOrWrongTypedDtoFields; do
  require_text "$TEST" "$test_name"
done

for marker in \
  checkpoint_serializer_probe_complete \
  checkpoint_serializer_defined \
  checkpoint_serializer_registered_dto_verified \
  checkpoint_serializer_canonical_digest_verified \
  checkpoint_serializer_malformed_unknown_rejected \
  checkpoint_serializer_size_depth_limit_verified \
  checkpoint_serializer_security_corpus_verified \
  checkpoint_serializer_android13_arm64_verified; do
  require_text "$PROBE" "$marker="
  require_text "$INSTALLER" "$marker=true"
done
for marker in \
  checkpoint_serializer_java_serialization_enabled=false \
  agent_graph_runtime_persistence_wired=false \
  agent_graph_executor_dispatch_enabled=false \
  effect_dispatch_enabled=false \
  model_invoked=false \
  network_accessed=false \
  hardware_accessed=false; do
  require_text "$PROBE" "$marker"
  require_text "$INSTALLER" "$marker"
done

require_text "$MANIFEST" '.graph.CheckpointSerializerProbeActivity'
if grep -Fq 'CheckpointSerializerProbeActivity' "$ROOT_DIR/$MAIN_MANIFEST"; then
  echo "P3-W03 debug probe leaked into the production manifest" >&2
  exit 1
fi
if grep -Fq 'CheckpointSerializer' "$ROOT_DIR/$GRAPH_RUNTIME" "$ROOT_DIR/$RUNTIME_SERVICE"; then
  echo "P3-W03 checkpoint serializer was wired into Graph or a production Service" >&2
  exit 1
fi
if grep -R -Eiq \
    'ObjectInputStream|ObjectOutputStream|java[.]io[.]Serializable|implements[[:space:]]+Serializable|Class[.]forName|java[.]lang[.]reflect|android[.]os[.](Binder|Bundle|Parcel)|android[.]car|CarPropertyManager|VehicleHal|VehicleProperty|java[.]net|okhttp|http://|https://|ioctl|sysfs|/dev/' \
    "$ROOT_DIR/$VALUE" "$ROOT_DIR/$CONTRACT" "$ROOT_DIR/$ENVELOPE" \
    "$ROOT_DIR/$SERIALIZER"; then
  echo "P3-W03 main contract references arbitrary serialization, reflection, Binder blobs, network, vehicle, or hardware APIs" >&2
  exit 1
fi
if grep -Eiq 'new Gson|fromJson|toJson|JsonObject|JsonElement' "$ROOT_DIR/$SERIALIZER"; then
  echo "P3-W03 must use the strict bounded streaming parser, not object deserialization" >&2
  exit 1
fi
if grep -Fq '.toList()' "$ROOT_DIR/$PROBE"; then
  echo "P3-W03 debug probe uses Stream.toList(), which is unavailable on the API 33 target" >&2
  exit 1
fi

require_text "central-brain/android-runtime/README.md" "P3-W03 CheckpointSerializer"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" '`P3-W03` CheckpointSerializer'
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P3-W03 CheckpointSerializer trace"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "P3-W03 implemented checkpoint contract"
require_text "docs/CENTRAL_BRAIN_SOFTWARE_DEVELOPMENT.md" "Android P3-W03 CheckpointSerializer"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "Android P3-W03 CheckpointSerializer"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P3-W03 CheckpointSerializer Driver/HAL Boundary"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P3-W03 checkpoint serializer"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P3-W03 进展"
require_text "docs/CENTRAL_BRAIN_REQUIREMENTS.md" "P3-W03 CheckpointSerializer"

printf '%s\n' \
  "Central Brain Android Checkpoint Serializer check passed" \
  "checkpoint_serializer_defined=true" \
  "checkpoint_serializer_registered_dto_verified=true" \
  "checkpoint_serializer_canonical_digest_verified=true" \
  "checkpoint_serializer_size_depth_limit_verified=true" \
  "checkpoint_serializer_security_corpus_verified=true" \
  "checkpoint_serializer_java_serialization_enabled=false" \
  "agent_graph_runtime_persistence_wired=false" \
  "agent_graph_executor_dispatch_enabled=false" \
  "effect_dispatch_enabled=false" \
  "model_invoked=false" \
  "hardware_accessed=false"
