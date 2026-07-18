# Central Brain P9-W03 Security Review and Fuzz

Status: `IN_PROGRESS / W03A_HOST_CORPUS_VERIFIED`

Req IDs: `S2-SAF-001`, `S2-TOL-001`, `S2-OBS-001`, `DEL-001/004/005`.

## 1. Purpose and boundary

P9-W03a establishes a deterministic hostile-input regression over three existing Android Runtime boundaries. It does
not introduce a new parser, production endpoint, fault injector, filesystem reader, network client, vehicle adapter or
hardware interface. Each case invokes the actual existing parser or validator and must return one exact typed error.

The corpus is a repeatable security regression, not coverage-guided fuzzing, penetration testing, target-device
qualification or a complete security review. AIDL identity/caller spoof, Binder replay, package signer policy and
Android 13 ARM64 execution remain in P9-W03b/W03c.

## 2. Published artifacts

| Artifact | Role |
| --- | --- |
| `central-brain/contracts/central_brain_android_p9_parser_security_corpus.json` | machine-readable case catalog and claim boundary |
| `ParserSecurityCorpusContract` | immutable Java catalog, exact counts, expected typed errors and canonical digest |
| `ParserSecurityCorpusContractTest` | constructs hostile inputs and invokes the real Checkpoint, ScenarioManifest and ToolSchema boundaries |
| `tools/check_central_brain_android_parser_security_corpus.sh` | JSON/Java/test/docs synchronization and prohibited-wiring gate |

Attack bytes and strings remain test-local. Main source contains only case metadata; it cannot parse input, authorize an
action, dispatch an Effect or read platform state.

## 3. Fixed corpus

| Surface | Six required classes | Expected behavior |
| --- | --- | --- |
| Checkpoint | malformed JSON, duplicate field, unknown field, oversize, digest tamper, privileged path key | exact `CheckpointException.ErrorCode`; no partial envelope |
| ScenarioManifest | source-name traversal, unknown field, duplicate field, oversize, trailing JSON, depth bomb | exact `ParseException.ErrorCode`; no manifest/catalog entry |
| ToolSchema | missing field, unknown field, null, exact-type confusion, value bound, aggregate payload oversize | exact `ValidationException.ErrorCode`; no validated map |

The JSON and Java catalogs must contain the same ordered 18 tuples:

```text
case_id | surface | threat_class | expected_error_code
```

Duplicate IDs, a fourth surface, a seventh case in a surface or an expected-error change fails the repository checker.
The catalog digest binds schema, profile and all ordered tuples.

## 4. Fail-closed criteria

1. Every hostile input throws the domain-specific typed exception.
2. The exact error code equals the catalog entry; generic success or a different error fails the test.
3. No test mutates Runtime Service registration, Binder publication, Graph dispatch, Effect dispatch or production policy.
4. The main catalog performs no random generation, clock read, Android API call, file/network access or hardware access.
5. Host success does not raise Android, target, production or hardware qualification.

## 5. Verification

```bash
JAVA_HOME="$PWD/.tools/jdk" ANDROID_HOME="$PWD/.tools/android-sdk" \
  central-brain/android-runtime/gradlew -p central-brain/android-runtime \
  :runtime-service:testDebugUnitTest \
  --tests com.centralbrain.runtime.security.ParserSecurityCorpusContractTest

bash tools/check_central_brain_android_parser_security_corpus.sh
```

P9-W03b must separately test Binder/caller identity spoof and replay using trusted identity snapshots and signature
policy. P9-W03c must add remaining schema/output/path/oversize aggregation and the controlled Android debug probe.
Coverage-guided fuzzing requires an explicit engine, seed ownership, budget, crash minimization, corpus retention and
sanitized evidence process before it can be claimed.

## 6. Current claims

`security_parser_corpus_defined=true`, `security_parser_surface_count=3`, `security_parser_case_count=18`,
`security_parser_fail_closed_regression_verified=true`, `security_coverage_guided_fuzz_complete=false`,
`security_aidl_identity_review_complete=false`, `security_signature_policy_review_complete=false`,
`security_android13_arm64_verified=false`, `security_runtime_wired=false`, `hardware_accessed=false`,
`production_ready=false`, `target_hardware_validated=false`, `implementation_stage=P9-W03`.

Tracking: `DEV-088`, `ISSUE-050`.
