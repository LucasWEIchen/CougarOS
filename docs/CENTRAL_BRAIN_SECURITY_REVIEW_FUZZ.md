# Central Brain P9-W03 Security Review and Fuzz

Status: `W03C_SOFTWARE_BOUNDARIES_VERIFIED / TARGET_FUZZ_PENDING`

Req IDs: `S2-SAF-001`, `S2-TOL-001`, `S2-OBS-001`, `DEL-001/004/005`.

## 1. Purpose and boundary

P9-W03a establishes a deterministic hostile-input regression over three existing Android Runtime boundaries. It does
not introduce a new parser, production endpoint, fault injector, filesystem reader, network client, vehicle adapter or
hardware interface. Each case invokes the actual existing parser or validator and must return one exact typed error.

The corpus is a repeatable security regression, not coverage-guided fuzzing, penetration testing, target-device
qualification or a complete security review. Host identity/replay/signer policy and the public AIDL boundary inventory
are covered by W03b/W03c; Binder caller spoof, APK signer cryptographic measurement, Android 13 ARM64 execution and
coverage-guided fuzzing remain external evidence gaps.

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

The existing `DUMP`-protected debug-only `StructuredModelOutputProbeActivity` now executes the same four aggregate
checks on Android in addition to its prior catalog/authority checks. The runtime installer requires all new markers and
only reports device success after its existing API/ABI gates. The activity remains absent from the main/release
manifest. This increment first observed `online=0/offline=1`; the pre-commit recheck found no transport
(`online=0/offline=0/unauthorized=0/other=0`). Probe availability is true, but execution and Android 13 ARM64
verification remain false.

Current W03c claims: `security_aidl_parcel_inventory_complete=true`, `security_aidl_interface_count=7`,
`security_aidl_parcelable_count=30`, `security_aidl_surface_count=37`, `security_validation_family_count=8`,
`security_host_path_oversize_aggregate_verified=true`, `security_android_debug_probe_available=true`,
`security_android_debug_probe_executed=false`, `security_coverage_guided_fuzz_complete=false`,
`security_binder_calling_uid_spoof_android_verified=false`,
`security_package_signature_cryptographically_verified=false`, `security_android13_arm64_verified=false`,
`security_runtime_wired=false`, `hardware_accessed=false`, `production_ready=false`,
`target_hardware_validated=false`, `implementation_stage=P9-W03`.

Tracking: `DEV-090`, `ISSUE-050`.
