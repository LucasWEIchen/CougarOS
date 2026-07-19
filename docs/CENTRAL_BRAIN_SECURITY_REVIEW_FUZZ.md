# Central Brain P9-W03 Security Review and Fuzz

Status: `W03E_CALLBACK_REPLAY_VERIFIED / TARGET_FUZZ_PENDING`

Historical W03d checkpoint marker: `W03D_ANDROID_IDENTITY_VERIFIED / TARGET_FUZZ_PENDING`.

Req IDs: `S2-SAF-001`, `S2-TOL-001`, `S2-OBS-001`, `DEL-001/004/005`.

## 1. Purpose and boundary

P9-W03a establishes a deterministic hostile-input regression over three existing Android Runtime boundaries. It does
not introduce a new parser, production endpoint, fault injector, filesystem reader, network client, vehicle adapter or
hardware interface. Each case invokes the actual existing parser or validator and must return one exact typed error.

The corpus is a repeatable security regression, not coverage-guided fuzzing, penetration testing, target-device
qualification or a complete security review. Host identity/replay/signer policy and the public AIDL boundary inventory
are covered by W03b/W03c. W03d separately verifies Binder caller UID and installed debug APK signer acquisition on
Android 13 ARM64. Coverage-guided fuzzing, callback replay, production signer qualification and security-owner approval
remain evidence gaps.

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

P9-W03b separately tests caller identity/capability policy, owner-bound replay and signer-state policy using trusted
identity snapshots and deterministic host evidence. P9-W03c adds remaining schema/output/path/oversize aggregation and
makes the controlled Android debug probe available; target execution remains pending.
Coverage-guided fuzzing requires an explicit engine, seed ownership, budget, crash minimization, corpus retention and
sanitized evidence process before it can be claimed.

## 6. Current claims

`security_parser_corpus_defined=true`, `security_parser_surface_count=3`, `security_parser_case_count=18`,
`security_parser_fail_closed_regression_verified=true`, `security_coverage_guided_fuzz_complete=false`,
`security_aidl_identity_review_complete=false`, `security_signature_policy_review_complete=false`,
`security_android13_arm64_verified=false`, `security_runtime_wired=false`, `hardware_accessed=false`,
`production_ready=false`, `target_hardware_validated=false`, `implementation_stage=P9-W03`.

Tracking: `DEV-088`, `ISSUE-050`.

## 7. P9-W03b identity, replay and signer policy corpus

W03b publishes a second fixed 3-surface / 18-case corpus. It invokes the existing `CallerCapabilityPolicy`,
`DurablePrincipalFingerprint`, `TransientSessionRegistry` and `SkillSignerPolicy`; it does not add a second
authorization implementation.

| Surface | Six cases | Host evidence |
| --- | --- | --- |
| Caller policy | unresolved identity, package spoof, current-signer spoof, capability escalation, shared-UID signer confusion, principal signer rotation | exact deny reason or changed stable principal fingerprint |
| Session replay | same-digest replay, conflicting request digest, cross-owner find/events/cancel, malformed owner | same handle for exact replay; conflict/isolation failures for all hostile cases |
| Signer policy | unknown, not-yet-active, retired, revoked, malformed digest, nonpositive epoch | exact signer decision or policy violation |

`AndroidCallerIdentityResolver` remains the production evidence acquisition boundary: it reads
`Binder.getCallingUid()`, resolves all visible UID packages, and hashes current APK content signers. The host corpus
constructs immutable snapshots after that boundary, so it verifies policy semantics but does not spoof the Android
Binder kernel identity or cryptographically remeasure a target APK. Those claims remain false until separately reviewed
target-device evidence is produced.

Current W03b claims: `security_identity_replay_corpus_defined=true`,
`security_identity_replay_surface_count=3`, `security_identity_replay_case_count=18`,
`security_caller_policy_host_verified=true`, `security_session_replay_owner_policy_host_verified=true`,
`security_signer_policy_host_verified=true`, `security_binder_calling_uid_spoof_android_verified=false`,
`security_package_signature_cryptographically_verified=false`, `security_coverage_guided_fuzz_complete=false`,
`security_android13_arm64_verified=false`, `security_runtime_wired=false`, `hardware_accessed=false`,
`production_ready=false`, `target_hardware_validated=false`, `implementation_stage=P9-W03`.

Tracking: `DEV-089`, `ISSUE-050`.

## 8. P9-W03c public boundary inventory and Android debug probe

W03c freezes the current public main AIDL tree at 37 surfaces: 7 interfaces and 30 parcelables across diagnostics,
effect, event, governance, plan, production and session. The machine contract lists every relative path and kind; the
repository checker derives the same inventory from source and fails on additions, deletions or reclassification.

Eight existing validation families are aggregated: Session, Plan, Event, Effect, Checkpoint, ScenarioManifest,
ToolSchema and StructuredModelOutput. The new JVM suite rechecks the AIDL tree and exercises StructuredModelOutput
unknown-field, path-like scenario identifier and 16 KiB+1 rejection plus Session utterance oversize. It calls existing
validators and does not introduce a parallel parser or Binder endpoint.

The existing `DUMP`-protected debug-only `StructuredModelOutputProbeActivity` executes the same four aggregate checks
on Android in addition to its prior catalog/authority checks. The runtime installer requires all markers and only
reports device success after its API/ABI gates. The activity remains absent from the main/release manifest. W03c first
observed no usable transport, then the later P9 aggregate run executed the probe on API 33 ARM64. That execution does
not verify Binder caller identity; W03d provides the separate identity acquisition evidence.

Current W03c claims: `security_aidl_parcel_inventory_complete=true`, `security_aidl_interface_count=7`,
`security_aidl_parcelable_count=30`, `security_aidl_surface_count=37`, `security_validation_family_count=8`,
`security_host_path_oversize_aggregate_verified=true`, `security_android_debug_probe_available=true`,
`security_android_debug_probe_executed=true`, `security_boundary_probe_android13_arm64_verified=true`,
`security_coverage_guided_fuzz_complete=false`,
`security_binder_calling_uid_spoof_android_verified=false`,
`security_package_signature_cryptographically_verified=false`, `security_android13_arm64_verified=false`,
`security_runtime_wired=false`, `hardware_accessed=false`, `production_ready=false`,
`target_hardware_validated=false`, `implementation_stage=P9-W03`.

Tracking: `DEV-090`, `ISSUE-050`.

## 9. P9-W03d Binder identity and current-signer device evidence

W03d adds one debug-only, explicitly addressed and signature-permission-protected AIDL service. The AIDL source is
identical in Runtime debug and SDK androidTest source sets. It exposes only three verification calls and returns only
boolean outcomes; UID, package name, certificate bytes and signer digest are never returned to the caller or printed.

For every call the Runtime reads `Binder.getCallingUid()` and immediately resolves the immutable
`CallerIdentitySnapshot` through `AndroidCallerIdentityResolver`. PackageManager supplies all packages associated with
that UID and their current APK content signer SHA-256 digests. The server compares that trusted snapshot with expected
and deliberately spoofed values supplied by the test. It never treats a caller-supplied UID, package or signer as the
identity source.

The SDK instrumentation runs in `com.centralbrain.sdk.test`, confirms its UID differs from
`com.centralbrain.runtime`, supplies the Runtime UID/package and a 64-zero digest as spoof values, and independently
hashes its installed current signer. API 33 ARM64 execution verified all three negative/positive bindings. Release
assembly also passed with no probe Service or debug AIDL in the release source set.

Reproduction:

```bash
JAVA_HOME="$PWD/.tools/jdk" ANDROID_HOME="$PWD/.tools/android-sdk" \
  bash tools/test_central_brain_android_security_identity.sh \
  --require-api-33 --clean-runtime-install
```

`--clean-runtime-install` is explicit because a previously installed Runtime signed by a different development key
cannot be upgraded in place. It removes only `com.centralbrain.runtime`; without the flag the test fails rather than
silently removing an installed package.

Current W03d claims: `security_identity_device_probe_verified=true`,
`security_distinct_app_uids_verified=true`,
`security_binder_calling_uid_spoof_android_verified=true`,
`security_package_signature_cryptographically_verified=true`,
`security_same_signer_debug_binding_verified=true`, `security_production_signer_verified=false`,
`security_coverage_guided_fuzz_complete=false`, `security_runtime_wired=false`, `hardware_accessed=false`,
`production_ready=false`, `target_hardware_validated=false`, `implementation_stage=P9-W03`.

The cryptographic claim is narrowly scoped to current signer digest acquisition for the installed debug APK. It is not
production certificate-chain validation, signer-owner approval, release admission, code transparency or target
hardware qualification. Tracking: `DEV-111`, `ISSUE-050`.

## 10. P9-W03e task callback replay device evidence

W03e exercises the production task AIDL rather than a parallel probe protocol. A debug-only capability-policy overlay admits the SDK test package while
the main/release policy remains unchanged. Demo and SDK instrumentation run under distinct UIDs. Demo first creates an owner-A task; SDK owner-B then uses
the same idempotency key and must receive a distinct task with callbacks bound only to that task.

For one owner, an active exact replay must return the same task. The SDK `TaskCallbackReplayGuard` drops duplicate/stale update sequences created by replay
attachment, rejects a cross-task or malformed callback, and admits only one completion/failure terminal. A conflicting payload with the same key must be
rejected before callback attachment, while a post-terminal exact replay may return the retained terminal exactly once to its new callback.

API 33 ARM64 verified all four cases. Evidence contains only booleans/count-free task comparisons; task IDs, UIDs, signer material and device identity are
not published. Current claims: `security_task_callback_replay_android_verified=true`,
`security_callback_sequence_replay_suppressed=true`, `security_callback_terminal_replay_unique=true`,
`security_idempotency_conflict_callback_silent=true`, `security_cross_uid_callback_owner_isolation_verified=true`,
`security_debug_test_principal_release_excluded=true`, `security_coverage_guided_fuzz_complete=false`,
`security_production_signer_verified=false`, `production_ready=false`, `target_hardware_validated=false`.
Tracking: `DEV-112`, `ISSUE-050`.
