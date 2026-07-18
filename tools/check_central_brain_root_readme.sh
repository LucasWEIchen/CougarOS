#!/usr/bin/env bash
set -euo pipefail

# Req IDs: APP-004, XSC-001..006, NV-F-001/011/012,
# NV-G-003/005/006/007, NV-P-002, KH-003/006, DEL-001/003/004/005.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
README="$ROOT_DIR/README.md"
SETTINGS="$ROOT_DIR/central-brain/android-runtime/settings.gradle.kts"

[[ -f "$README" ]] || { echo "missing repository README" >&2; exit 1; }
[[ -f "$SETTINGS" ]] || { echo "missing Android Gradle settings" >&2; exit 1; }

require_text() {
  local marker="$1"
  grep -Fq -- "$marker" "$README" \
    || { echo "root README marker missing: $marker" >&2; exit 1; }
}

for heading in \
  '# CougarOS Central Brain' \
  '## 当前状态' \
  '## GitHub 同步与仓库完整性' \
  '## README 维护规则' \
  '## 软件总架构' \
  '## 开发进度总表' \
  '### 已开发并验证' \
  '### 未开发或外部阻塞' \
  '## 核心调用链' \
  '## 仓库目录与模块映射' \
  '## 语言与所有权边界' \
  '## Android 交付产物' \
  '## 安全和集成边界' \
  '## 关键架构文档' \
  '## 本地受控输入与非发布内容' \
  '## 近期修改日志'; do
  require_text "$heading"
done

for marker in \
  '用户提供的架构图是需求基线，不是示意图' \
  'github_source_of_truth=true' \
  'github_sync_required=true' \
  'maintained_project_files_synced=true' \
  'github_homepage_architecture_current=true' \
  '每个完成的开发增量必须在同一轮完成 Git commit、push 和远端检查' \
  'python_prototype_runtime_maintained=false' \
  'central-brain/android-runtime/' \
  'central-brain-sdk' \
  'runtime-service' \
  'native-runtime' \
  'demo-hmi' \
  'policy-probe' \
  'apk-labs/client2-central-brain/' \
  'vendor.npu.empty' \
  'CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md' \
  'CENTRAL_BRAIN_COCKPIT_HMI_UX_DESIGN_MOCKUPS.md' \
  'CENTRAL_BRAIN_PYTHON_PROTOTYPE_RETIREMENT.md' \
  'tools/check_central_brain_python_prototype_retirement.sh' \
  'central_brain_github_remote_testing.json' \
  'android13-hwtest-v0.5.0-rc.2' \
  'physical_controller_application_evidence_available=true' \
  'cockpit_hmi_design_mockups_ready=true' \
  'aios_intent_orchestration_ux_ready=true' \
  'cockpit_hmi_1920x1080_safe_frame_verified=true' \
  'cockpit_hmi_translucent_material_ready=true' \
  'session_contract_v1_defined=true' \
  'session_parcel_physical_android13_arm64_verified=true' \
  'sdk_facade_v2_available=true' \
  'session_runtime_service_published=true' \
  'active_session_reconnect_resubscribe_verified=true' \
  'room_schema_version=4' \
  'session_runtime_persistence_wired=true' \
  'session_runtime_process_death_rehydration=true' \
  'runtime_contract_v2_defined=true' \
  'runtime_contract_v2_verified=true' \
  'runtime_contract_v2_physical_android13_arm64_verified=true' \
  'frozen_v1_hashes_unchanged=true' \
  'vehicle_signal_schema_defined=true' \
  'vehicle_signal_path_allowlist_count=12' \
  'vehicle_signal_schema_android13_arm64_verified=true' \
  'vehicle_signal_provider_wired=false' \
  'vehicle_property_mapping_configured=false' \
  'vehicle_capability_catalog_defined=true' \
  'vehicle_capability_count=8' \
  'vehicle_capability_catalog_android13_arm64_verified=true' \
  'vehicle_production_capability_authorized_count=0' \
  'vehicle_capability_adapter_registry_wired=false' \
  'vehicle_digital_twin_store_defined=true' \
  'vehicle_digital_twin_android13_arm64_verified=true' \
  'vehicle_digital_twin_persistence_wired=false' \
  'vehicle_digital_twin_adapter_wired=false' \
  'context_snapshot_defined=true' \
  'context_snapshot_android13_arm64_verified=true' \
  'context_snapshot_production_trusted=false' \
  'context_snapshot_production_wired=false' \
  'scenario_manifest_schema_version=1' \
  'scenario_catalog_count=3' \
  'scenario_manifest_android13_arm64_verified=true' \
  'scenario_manifest_artifact_crypto_verified=false' \
  'scenario_catalog_production_trusted=false' \
  'scenario_resolver_defined=true' \
  'scenario_resolution_schema_version=1' \
  'scenario_resolver_android13_arm64_verified=true' \
  'scenario_resolver_model_invoked=false' \
  'scenario_resolver_runtime_wired=false' \
  'scenario_compiler_wired=false' \
  'scenario_plan_compiler_defined=true' \
  'scenario_plan_schema_version=1' \
  'scenario_plan_compiler_android13_arm64_verified=true' \
  'scenario_plan_compiler_runtime_wired=false' \
  'scenario_plan_runtime_published=false' \
  'scenario_runtime_wired=false' \
  'scenario_graph_execution_enabled=false' \
  'simulated_effect_adapter_base_defined=true' \
  'simulated_effect_adapter_android13_arm64_verified=true' \
  'simulated_effect_adapter_debug_only=true' \
  'simulated_effect_adapter_release_source_absent=true' \
  'simulated_effect_adapter_production_registered=false' \
  'simulated_effect_adapter_runtime_wired=false' \
  'simulated_hvac_adapter_defined=true' \
  'simulated_hvac_typed_target_verified=true' \
  'simulated_hvac_desired_reported_verified=true' \
  'simulated_hvac_android13_arm64_verified=true' \
  'simulated_hvac_debug_only=true' \
  'simulated_hvac_release_source_absent=true' \
  'simulated_hvac_production_registered=false' \
  'simulated_hvac_runtime_wired=false' \
  'simulated_seat_adapter_defined=true' \
  'simulated_seat_typed_target_verified=true' \
  'simulated_seat_recline_safety_verified=true' \
  'simulated_seat_dispatch_revalidation_verified=true' \
  'simulated_seat_progress_verified=true' \
  'simulated_seat_android13_arm64_verified=true' \
  'simulated_seat_debug_only=true' \
  'simulated_seat_release_source_absent=true' \
  'simulated_seat_production_registered=false' \
  'simulated_seat_runtime_wired=false' \
  'simulated_media_adapter_defined=true' \
  'simulated_navigation_adapter_defined=true' \
  'simulated_media_nav_typed_target_verified=true' \
  'simulated_media_state_verified=true' \
  'simulated_navigation_synthetic_observation_verified=true' \
  'simulated_navigation_query_digest_only=true' \
  'simulated_media_nav_replaceable_backend_verified=true' \
  'simulated_media_nav_android13_arm64_verified=true' \
  'simulated_media_nav_debug_only=true' \
  'simulated_media_nav_release_source_absent=true' \
  'simulated_media_nav_production_registered=false' \
  'simulated_media_nav_runtime_wired=false' \
  'external_activity_started=false' \
  'location_uploaded=false' \
  'network_accessed=false' \
  'debug_simulation_controller_defined=true' \
  'debug_simulation_controller_aidl_version=1' \
  'debug_simulation_controller_signature_permission_enforced=true' \
  'debug_simulation_controller_capability_enforced=true' \
  'debug_simulation_controller_android13_arm64_verified=true' \
  'debug_simulation_controller_debug_only=true' \
  'debug_simulation_controller_release_source_absent=true' \
  'debug_simulation_controller_production_exported=false' \
  'debug_simulation_controller_runtime_wired=false' \
  'agent_graph_runtime_defined=true' \
  'agent_graph_state_machine_verified=true' \
  'agent_graph_compensation_fail_closed_verified=true' \
  'agent_graph_android13_arm64_verified=true' \
  'agent_graph_executor_dispatch_enabled=false' \
  'agent_graph_runtime_persistence_wired=false' \
  'agent_graph_runtime_binder_published=false' \
  'agent_graph_runtime_production_wired=false' \
  'typed_node_executor_contract_defined=true' \
  'typed_node_executor_schema_count=11' \
  'typed_node_executor_debug_count=7' \
  'typed_node_executor_exact_class_verified=true' \
  'typed_node_executor_effect_fail_closed_verified=true' \
  'typed_node_executor_unsupported_fail_closed_verified=true' \
  'typed_node_executor_android13_arm64_verified=true' \
  'typed_node_executor_graph_dispatch_enabled=false' \
  'typed_node_executor_production_wired=false' \
  'checkpoint_serializer_defined=true' \
  'checkpoint_serializer_registered_dto_verified=true' \
  'checkpoint_serializer_canonical_digest_verified=true' \
  'checkpoint_serializer_size_depth_limit_verified=true' \
  'checkpoint_serializer_security_corpus_verified=true' \
  'checkpoint_serializer_android13_arm64_verified=true' \
  'checkpoint_serializer_java_serialization_enabled=false' \
  'node_retry_policy_defined=true' \
  'node_timeout_policy_defined=true' \
  'backoff_deterministic_bounded_verified=true' \
  'timeout_deadline_clamp_verified=true' \
  'retry_attempt_budget_verified=true' \
  'effect_idempotency_reconcile_gate_verified=true' \
  'retry_deadline_fail_closed_verified=true' \
  'retry_timeout_policy_android13_arm64_verified=true' \
  'retry_timeout_policy_runtime_wired=false' \
  'approval_interrupt_record_defined=true' \
  'approval_interrupt_binding_verified=true' \
  'approval_interrupt_checkpoint_roundtrip_verified=true' \
  'approval_interrupt_trusted_decision_verified=true' \
  'approval_resume_owner_plan_context_policy_verified=true' \
  'approval_resume_safety_revalidation_verified=true' \
  'approval_resume_expiry_verified=true' \
  'approval_interrupt_android13_arm64_verified=true' \
  'approval_interrupt_persistence_wired=false' \
  'approval_grant_service_published=false' \
  'effect_batch_defined=true' \
  'effect_dependency_plan_verified=true' \
  'effect_resource_conflict_serialized=true' \
  'effect_adapter_registry_profile_isolation_verified=true' \
  'effect_prepare_all_required_verified=true' \
  'effect_optional_degradation_verified=true' \
  'effect_independent_observation_verified=true' \
  'effect_coordinator_android13_arm64_verified=true' \
  'effect_coordinator_graph_wired=false' \
  'effect_coordinator_persistence_wired=false' \
  'production_effect_adapter_registered=false' \
  'production_effect_dispatch_enabled=false' \
  'effect_verification_reconciliation_wired=false' \
  'effect_verifier_defined=true' \
  'effect_verification_policies_verified=true' \
  'effect_state_separation_verified=true' \
  'effect_unknown_reconciliation_verified=true' \
  'effect_verified_redispatch_blocked=true' \
  'effect_production_readback_fail_closed=true' \
  'effect_verification_android13_arm64_verified=true' \
  'effect_verification_reconciliation_runtime_wired=false' \
  'effect_verification_scheduler_wired=false' \
  'effect_verification_persistence_wired=false' \
  'effect_verification_production_readback_wired=false' \
  'effect_verification_graph_wired=false' \
  'compensation_planner_defined=true' \
  'compensation_absolute_before_verified=true' \
  'compensation_reverse_dependency_verified=true' \
  'compensation_irreversible_rejected=true' \
  'undo_ttl_governance_verified=true' \
  'undo_new_governed_task_verified=true' \
  'undo_idempotent_replay_verified=true' \
  'undo_production_fail_closed=true' \
  'compensation_undo_android13_arm64_verified=true' \
  'compensation_undo_runtime_wired=false' \
  'compensation_undo_persistence_wired=false' \
  'undo_binder_service_published=false' \
  'compensation_dispatch_enabled=false' \
  'graph_restart_reconciler_defined=true' \
  'graph_restart_room_v4_repository_verified=true' \
  'graph_restart_process_death_verified=true' \
  'graph_restart_idempotent_reopen_verified=true' \
  'graph_restart_audit_exactly_once_verified=true' \
  'graph_restart_historical_digest_replay_verified=true' \
  'graph_restart_side_effect_count=0' \
  'graph_restart_runtime_wired=false' \
  'graph_restart_binder_published=false' \
  'graph_restart_executor_dispatch_enabled=false' \
  'graph_restart_effect_dispatch_enabled=false' \
  'graph_restart_production_wired=false' \
  'implementation_stage=P7-W01' \
  'episodic_memory_store_defined=true' \
  'episodic_memory_summary_result_only_verified=true' \
  'episodic_memory_read_fail_closed=true' \
  'episodic_memory_raw_continuous_signal_stored=false' \
  'episodic_memory_persistence_wired=false' \
  'episodic_memory_runtime_wired=false' \
  'episodic_memory_model_context_published=false' \
  'episodic_memory_production_read_authority_wired=false' \
  'context_budget_manager_defined=true' \
  'context_budget_category_allocation_verified=true' \
  'context_budget_dual_limit_verified=true' \
  'context_budget_deterministic_overflow_verified=true' \
  'context_budget_required_fail_closed=true' \
  'context_budget_android13_arm64_verified=false' \
  'memory_consent_controller_defined=true' \
  'memory_consent_source_visibility_verified=true' \
  'memory_consent_disable_verified=true' \
  'memory_consent_preference_clear_verified=true' \
  'memory_consent_moving_restriction_verified=true' \
  'memory_consent_android13_arm64_verified=false' \
  'memory_consent_hmi_projection_only=true' \
  'memory_consent_repository_mutation_wired=false' \
  'memory_consent_production_authority_wired=false' \
  'memory_consent_runtime_wired=false' \
  'memory_consent_model_context_published=false' \
  'event_broker_interface_defined=true' \
  'event_broker_typed_topics_verified=true' \
  'event_broker_append_before_notify_verified=true' \
  'event_broker_bounded_replay_filter_verified=true' \
  'event_broker_identity_policy_verified=true' \
  'event_broker_subscription_lifecycle_verified=true' \
  'event_broker_android13_arm64_verified=false' \
  'event_broker_process_local=true' \
  'event_broker_durable_persistence_wired=false' \
  'event_broker_dds_transport_wired=false' \
  'event_broker_production_published=false' \
  'event_broker_runtime_wired=false' \
  'event_qos_contract_defined=true' \
  'event_qos_policy_count=4' \
  'event_qos_critical_no_silent_drop_verified=true' \
  'event_qos_deadline_priority_verified=true' \
  'event_qos_consumer_isolation_verified=true' \
  'event_qos_android13_arm64_verified=false' \
  'event_qos_process_local=true' \
  'event_qos_broker_wired=false' \
  'event_qos_durable_persistence_wired=false' \
  'event_qos_production_middleware_wired=false' \
  'trigger_rule_manifest_defined=true' \
  'trigger_rule_manifest_verified=true' \
  'trigger_threshold_window_debounce_verified=true' \
  'trigger_cooldown_scope_verified=true' \
  'trigger_input_fail_closed_verified=true' \
  'trigger_suggestion_only_verified=true' \
  'trigger_engine_android13_arm64_verified=false' \
  'trigger_engine_process_local=true' \
  'trigger_cooldown_persistence_wired=false' \
  'trigger_source_adapter_wired=false' \
  'trigger_auto_execution_enabled=false' \
  'trigger_runtime_wired=false' \
  'proactive_consent_policy_defined=true' \
  'proactive_grant_binding_verified=true' \
  'proactive_high_critical_generic_grant_blocked=true' \
  'proactive_grant_ttl_revoke_verified=true' \
  'proactive_policy_fail_closed_verified=true' \
  'proactive_consent_android13_arm64_verified=false' \
  'proactive_policy_process_local=true' \
  'proactive_grant_persistence_wired=false' \
  'proactive_consent_authority_wired=false' \
  'proactive_auto_execution_enabled=false' \
  'proactive_runtime_wired=false' \
  'context_source_adapter_contract_defined=true' \
  'context_source_count=3' \
  'context_source_allowlist_verified=true' \
  'context_source_runtime_health_verified=true' \
  'context_source_simulated_vehicle_verified=true' \
  'context_source_time_verified=true' \
  'context_source_freshness_quality_verified=true' \
  'context_source_fail_closed_verified=true' \
  'context_source_android13_arm64_verified=false' \
  'context_source_production_registry_published=false' \
  'context_source_runtime_wired=false' \
  'context_source_trigger_engine_wired=false' \
  'active_suggestion_controller_defined=true' \
  'active_suggestion_full_card_verified=true' \
  'active_suggestion_merge_replay_verified=true' \
  'active_suggestion_moving_minimal_verified=true' \
  'active_suggestion_never_ask_verified=true' \
  'active_suggestion_android13_arm64_verified=false' \
  'active_suggestion_hmi_projection_only=true' \
  'active_suggestion_production_source_wired=false' \
  'active_suggestion_preference_repository_wired=false' \
  'active_suggestion_voice_engine_wired=false' \
  'context_budget_decision_only=true' \
  'context_budget_text_payload_accepted=false' \
  'context_budget_tokenizer_wired=false' \
  'context_budget_summarizer_wired=false' \
  'context_budget_production_authority_wired=false' \
  'context_budget_runtime_wired=false' \
  'tool_manifest_contract_defined=true' \
  'tool_manifest_schema_version=1' \
  'tool_manifest_contract_digest_verified=true' \
  'tool_schema_exact_scalar_validation_verified=true' \
  'tool_manifest_health_fail_closed=true' \
  'tool_manifest_android13_arm64_verified=false' \
  'tool_registry_contract_defined=true' \
  'tool_resolver_contract_defined=true' \
  'tool_health_dynamic_snapshot_defined=true' \
  'tool_registry_digest_verified=true' \
  'tool_registry_version_conflict_rejected=true' \
  'tool_resolver_states_separated=true' \
  'tool_resolver_unhealthy_no_fallback=true' \
  'tool_registry_android13_arm64_verified=false' \
  'tool_registry_published=false' \
  'tool_resolver_published=false' \
  'tool_registry_runtime_wired=false' \
  'tool_rule_set_contract_defined=true' \
  'tool_rule_solver_runtime_wired=false' \
  'tool_executor_contract_defined=true' \
  'tool_invocation_context_defined=true' \
  'built_in_allowlist_enforced=true' \
  'built_in_signer_artifact_bound=true' \
  'tool_executor_host_execution_verified=true' \
  'tool_executor_deadline_cancel_verified=true' \
  'tool_executor_output_limit_verified=true' \
  'tool_executor_audit_bounded_verified=true' \
  'tool_executor_android13_arm64_verified=false' \
  'tool_executor_runtime_wired=false' \
  'skill_artifact_verifier_contract_defined=true' \
  'skill_signer_policy_contract_defined=true' \
  'skill_version_policy_contract_defined=true' \
  'skill_artifact_hash_verified=true' \
  'skill_manifest_digest_verified=true' \
  'skill_signer_policy_verified=true' \
  'skill_runtime_version_verified=true' \
  'skill_capability_policy_verified=true' \
  'skill_revocation_downgrade_fail_closed=true' \
  'skill_package_verifier_android13_arm64_verified=false' \
  'trusted_skill_evidence_source_configured=false' \
  'package_signature_cryptographically_verified=false' \
  'dynamic_skill_loading_enabled=false' \
  'skill_package_verifier_runtime_wired=false' \
  'tool_execution_enabled=false' \
  'production_tool_execution_enabled=false' \
  'production_tool_registered=false' \
  'production_tool_artifact_loaded=false' \
  'client2_session_event_primary_api=true' \
  'client2_session_event_typed_callback=true' \
  'client2_scenario_alias_map_count=14' \
  'client2_session_reconnect_replay_verified=true' \
  'client2_session_duplicate_event_suppressed=true' \
  'cockpit_hmi_state_reducer_implemented=true' \
  'cockpit_hmi_state_immutable=true' \
  'cockpit_hmi_lifecycle_owner_java=true' \
  'client2_legacy_smali_controller_retired=true' \
  'client2_hmi_session_replacement_verified=true' \
  'client2_hmi_checkpoint_resume_verified=true' \
  'client2_hmi_hidden_state_recreation_verified=true' \
  'client2_hmi_checkpoint_text_persisted=false' \
  'legacy_text_callback_authoritative=false' \
  'cockpit_hmi_four_stage_shell_implemented=true' \
  'cockpit_hmi_intent_first_primary=true' \
  'cockpit_hmi_safe_frame_1920x1080_verified=true' \
  'cockpit_hmi_device_drawer_scaffolded=true' \
  'cockpit_hvac_surface_implemented=true' \
  'cockpit_hvac_reducer_owned=true' \
  'cockpit_hvac_debounce_ms=300' \
  'cockpit_hvac_governed_manual_session=true' \
  'cockpit_hvac_desired_reported_separation_verified=true' \
  'cockpit_hvac_reported_readback_available=false' \
  'cockpit_hvac_verified_before_readback=false' \
  'hvac_manual_typed_parameter_field=false' \
  'cockpit_seat_surface_implemented=true' \
  'cockpit_seat_reducer_owned=true' \
  'cockpit_seat_debounce_ms=300' \
  'cockpit_seat_governed_manual_session=true' \
  'cockpit_seat_unknown_restricted_fail_closed=true' \
  'cockpit_seat_reported_readback_available=false' \
  'cockpit_execution_timeline_implemented=true' \
  'cockpit_execution_timeline_reducer_owned=true' \
  'cockpit_execution_typed_event_projection=true' \
  'cockpit_execution_trace_capacity=8' \
  'cockpit_execution_plan_published=false' \
  'cockpit_execution_effect_dispatch_enabled=false' \
  'cockpit_execution_readback_available=false' \
  'cockpit_recovery_state_reducer_owned=true' \
  'cockpit_approval_details_fail_closed=true' \
  'cockpit_partial_outcome_projection=true' \
  'cockpit_compensation_projection=true' \
  'cockpit_recovery_commands_enabled=false' \
  'cockpit_driving_ux_policy_implemented=true' \
  'cockpit_unknown_driving_restricted=true' \
  'cockpit_moving_long_text_hidden=true' \
  'cockpit_restricted_parameter_editing_disabled=true' \
  'cockpit_high_risk_controls_disabled=true' \
  'cockpit_runtime_policy_authority_independent=true' \
  'cockpit_engineer_simulation_drawer_implemented=true' \
  'cockpit_engineer_signature_permission_required=true' \
  'cockpit_engineer_capability_required=true' \
  'cockpit_engineer_context_revisioned=true' \
  'cockpit_engineer_runtime_release_service_absent=true' \
  'cockpit_engineer_effect_authorization_source=false' \
  'cockpit_engineer_production_available=false' \
  'cockpit_scenario_control_state_reducer_owned=true' \
  'cockpit_scenario_catalog_normalized=true' \
  'cockpit_scenario_manual_shared_client=true' \
  'cockpit_scenario_device_session_synchronized=true' \
  'cockpit_scenario_plan_publication_inferred=false' \
  'cockpit_scenario_effect_dispatch_enabled=false' \
  'cockpit_scenario_readback_available=false' \
  'cockpit_display_matrix_defined=true' \
  'cockpit_display_profile_count=3' \
  'cockpit_touch_target_min_dp=48' \
  'cockpit_accessibility_semantics_runtime_owned=true' \
  'cockpit_accessibility_state_not_color_only=true' \
  'cockpit_display_large_text_1_3_verified=true' \
  'cockpit_display_unsupported_fail_closed=true' \
  'cockpit_display_matrix_android13_arm64_verified=true' \
  'cockpit_display_effect_authorization_source=false' \
  'p4_w12_application_acceptance_complete=true' \
  'p4_android13_arm64_aggregate_verified=true' \
  'p4_navigation_show_hide_verified=true' \
  'p4_natural_scenario_sync_verified=true' \
  'p4_manual_hvac_seat_admission_verified=true' \
  'p4_moving_unknown_fail_closed_verified=true' \
  'p4_runtime_client_process_recovery_verified=true' \
  'p4_ui_tree_verified=true' \
  'p4_crash_buffer_clean=true' \
  'runtime_release_simulation_surface_absent=true' \
  'p4_plan_effect_projection_host_verified=true' \
  'p4_automatic_plan_runtime_published=false' \
  'p4_production_effect_dispatch_enabled=false' \
  'p4_approval_response_service_published=false' \
  'p4_undo_service_published=false' \
  'p4_vehicle_readback_available=false' \
  'client2_production_release_artifact_available=false' \
  'hmi_d4_demo_control_loop_complete=false' \
  'event_v2_cursor_ack_required=true' \
  'event_v2_interface_published=false' \
  'plan_contract_v1_defined=true' \
  'plan_parcel_physical_android13_arm64_verified=true' \
  'plan_runtime_published=false' \
  'event_contract_v1_defined=true' \
  'event_parcel_physical_android13_arm64_verified=true' \
  'event_runtime_service_published=true' \
  'event_callback_service_published=true' \
  'effect_contract_v1_defined=true' \
  'effect_parcel_physical_android13_arm64_verified=true' \
  'effect_runtime_service_published=false' \
  'approval_response_service_published=false' \
  'undo_service_published=false' \
  'ICentralBrainSessionRuntime V1（已发布）' \
  'cockpit_demo_control_loop_implemented=false' \
  'S2-HMI-001..006' \
  '意图输入（设计稿已交付）' \
  '计划与 Policy（设计稿已交付）' \
  '中控 AIOS 演示闭环' \
  '七阶段执行 timeline' \
  'production_ready=false' \
  'target_hardware_validated=false' \
  'driver_development_triggered=false' \
  'virtualization_development_triggered=false' \
  'GitHub 不连接目标 ADB' \
  '不修改厂商 Android Framework'; do
  require_text "$marker"
done

required_paths=(
  .github/ISSUE_TEMPLATE/hardware-test.yml
  .github/workflows/central-brain-remote-test-contract.yml
  .githooks/pre-push
  apk-labs/client2-central-brain/README.md
  central-brain/android-runtime/settings.gradle.kts
  central-brain/android-runtime/central-brain-sdk/build.gradle.kts
  central-brain/android-runtime/native-runtime/build.gradle.kts
  central-brain/android-runtime/runtime-service/build.gradle.kts
  central-brain/contracts/central_brain_android_b3_blackbox_acceptance.json
  central-brain/contracts/central_brain_android_r7c_acceptance.json
  central-brain/contracts/central_brain_github_remote_testing.json
  central-brain/contracts/central_brain_runtime_contract_v2.json
  central-brain/delivery/android-hybrid/central-brain.android-hybrid-delivery-profile.json
  docs/CENTRAL_BRAIN_SOFTWARE_ARCHITECTURE.md
  docs/CENTRAL_BRAIN_COMPLETE_SOFTWARE_DEVELOPMENT_DESIGN.md
  docs/CENTRAL_BRAIN_COCKPIT_HMI_CONTROL_LOOP_PLAN.md
  docs/CENTRAL_BRAIN_COCKPIT_HMI_UX_DESIGN_MOCKUPS.md
  docs/ui/cockpit-hmi-design/index.html
  docs/ui/cockpit-hmi-design/styles.css
  docs/ui/cockpit-hmi-design/app.js
  docs/assets/cockpit-hmi-design/01-intent.png
  docs/assets/cockpit-hmi-design/02-plan.png
  docs/assets/cockpit-hmi-design/03-execution.png
  docs/assets/cockpit-hmi-design/04-result.png
  docs/CENTRAL_BRAIN_INTERFACE_DESIGN.md
  docs/CENTRAL_BRAIN_ARCHITECTURE_REQUIREMENTS.md
  docs/CENTRAL_BRAIN_ROADMAP.md
  docs/CENTRAL_BRAIN_PYTHON_PROTOTYPE_RETIREMENT.md
  tools/check_central_brain_android_runtime_evolution.sh
  tools/check_central_brain_android_session_contract.sh
  tools/check_central_brain_android_plan_contract.sh
  tools/check_central_brain_android_event_contract.sh
  tools/check_central_brain_android_effect_contract.sh
  tools/check_central_brain_android_vehicle_digital_twin.sh
  tools/check_central_brain_android_context_snapshot.sh
  tools/check_central_brain_android_scenario_manifest.sh
  tools/check_central_brain_android_scenario_resolver.sh
  tools/check_central_brain_android_scenario_plan_compiler.sh
  tools/check_central_brain_android_simulated_effect_adapter.sh
  tools/check_central_brain_android_simulated_hvac_adapter.sh
  tools/check_central_brain_android_simulated_seat_adapter.sh
  tools/check_central_brain_android_simulated_media_navigation_adapters.sh
  tools/check_central_brain_android_debug_simulation_controller.sh
  tools/check_central_brain_android_agent_graph_runtime.sh
  tools/check_central_brain_android_typed_node_executors.sh
  tools/check_central_brain_android_checkpoint_serializer.sh
  tools/check_central_brain_android_retry_timeout_policy.sh
  tools/check_central_brain_android_approval_interrupt.sh
  tools/check_central_brain_android_effect_coordinator.sh
  tools/check_central_brain_android_graph_restart_recovery.sh
  tools/check_central_brain_android_client2_intent_shell.sh
  tools/check_central_brain_android_client2_execution_timeline.sh
  tools/check_central_brain_android_client2_recovery_ux.sh
  tools/check_central_brain_runtime_contract_v2.sh
  tools/check_central_brain_aios_stage2_design.sh
  tools/check_central_brain_cockpit_hmi_design.sh
  tools/check_central_brain_github_repository_completeness.sh
  tools/check_central_brain_python_prototype_retirement.sh
  tools/check_central_brain_github_remote_testing.sh
)
for path in "${required_paths[@]}"; do
  [[ -f "$ROOT_DIR/$path" ]] \
    || { echo "README-mapped repository file is missing: $path" >&2; exit 1; }
done

if rg -n \
    'central-brain/(backend|android-console|bindings/(android|linux)|linux-cli|deploy/linux)|CENTRAL_BRAIN_(SOFTWARE_DETAILED_DESIGN|PLATFORM_DELTA|PROTOTYPE_|ANDROID_SYSTEM_SERVICE_INTEGRATION)|ollama_simulated_npu' \
    "$README"; then
  echo "root README references a retired Python prototype path" >&2
  exit 1
fi

python3 -B - "$ROOT_DIR" "$README" "$SETTINGS" <<'PY'
import pathlib
import re
import sys

root = pathlib.Path(sys.argv[1])
readme = pathlib.Path(sys.argv[2]).read_text(encoding="utf-8")
settings = pathlib.Path(sys.argv[3]).read_text(encoding="utf-8")

modules = re.findall(r'include\(":([a-z0-9-]+)"\)', settings)
expected = {
    "central-brain-sdk",
    "native-runtime",
    "runtime-service",
    "demo-hmi",
    "policy-probe",
}
if set(modules) != expected:
    raise SystemExit(f"unexpected Android Gradle modules: {modules}")
for module in modules:
    if f"`{module}`" not in readme:
        raise SystemExit(f"Android Gradle module missing from README: {module}")

relative_links = []
for target in re.findall(r"\[[^\]]+\]\(([^)]+)\)", readme):
    if target.startswith(("http://", "https://", "#")):
        continue
    target = target.split("#", 1)[0]
    if target:
        relative_links.append(target)
for target in relative_links:
    if not (root / target).exists():
        raise SystemExit(f"README relative link does not exist: {target}")

recent = readme.split("## 近期修改日志", 1)[1]
commit_links = re.findall(
    r"https://github\.com/LucasWEIchen/CougarOS/commit/", recent
)
if len(commit_links) < 8:
    raise SystemExit("README recent log must retain at least eight commit links")

developed = readme.split("### 已开发并验证", 1)[1].split(
    "### 未开发或外部阻塞", 1
)[0]
if developed.count("`DEVELOPED`") < 9:
    raise SystemExit("README developed table must contain at least nine modules")

remaining = readme.split("### 未开发或外部阻塞", 1)[1].split(
    "## 核心调用链", 1
)[0]
remaining_rows = sum(
    remaining.count(status)
    for status in ("`IN_PROGRESS`", "`NOT_STARTED`", "`EXTERNAL_BLOCKED`", "`OUT_OF_SCOPE`")
)
if remaining_rows < 12:
    raise SystemExit("README remaining-work table must contain at least twelve modules")
if "Runtime Contract v2" not in developed or "`DEVELOPED`" not in developed:
    raise SystemExit("README developed table must include the completed Runtime Contract v2 aggregate")
if "P7-W01" not in remaining or "ModelRequest/Result v2" not in remaining:
    raise SystemExit("README remaining-work table must identify P7-W01 ModelRequest/Result v2 as the next unfinished scope")
if "P6 EventBroker interface/in-process" not in developed:
    raise SystemExit("README developed table must include the completed P6-W01 EventBroker")
if "P6 Event Backpressure/QoS" not in developed:
    raise SystemExit("README developed table must include the completed P6-W02 Event Backpressure/QoS")
if "P6 TriggerRule/TriggerEngine" not in developed:
    raise SystemExit("README developed table must include the completed P6-W03 TriggerRule/TriggerEngine")
if "P6 Proactive consent/policy" not in developed:
    raise SystemExit("README developed table must include the completed P6-W04 Proactive consent/policy")
if "P6 Context source adapters" not in developed:
    raise SystemExit("README developed table must include the completed P6-W05 Context source adapters")
if "P6 Active suggestion UX" not in developed:
    raise SystemExit("README developed table must include the completed P6-W06 Active suggestion UX")
if "P5 Tool Executor boundary" not in developed:
    raise SystemExit("README developed table must include the completed P5 Tool Executor boundary")
if "P5 Skill package verifier" not in developed:
    raise SystemExit("README developed table must include the completed P5 Skill package verifier")
if "P5 ContextBudgetManager" not in developed:
    raise SystemExit("README developed table must include the completed P5 ContextBudgetManager")
if "P5 Memory consent HMI/API" not in developed:
    raise SystemExit("README developed table must include the completed P5 Memory consent HMI/API")
if "P5 WorkingMemoryStore" not in developed:
    raise SystemExit("README developed table must include the completed P5 WorkingMemoryStore")
if "P5 ProfileMemoryStore" not in developed:
    raise SystemExit("README developed table must include the completed P5 ProfileMemoryStore")
if "P5 EpisodicMemoryStore" not in developed:
    raise SystemExit("README developed table must include the completed P5 EpisodicMemoryStore")

for group in (
    "APP-004",
    "XSC-001..006",
    "NV-F-001/011/012",
    "NV-G-003/005/006/007",
    "DEL-001/003/004/005",
):
    if group not in readme:
        raise SystemExit(f"README Req ID group missing: {group}")

print("root_readme_android_gradle_modules_verified=true")
print(f"root_readme_relative_links_verified={len(relative_links)}")
print(f"root_readme_recent_commit_links={len(commit_links)}")
PY

printf '%s\n' \
  'Central Brain repository architecture README check passed' \
  'root_readme_architecture_documented=true' \
  'root_readme_module_mapping_documented=true' \
  'root_readme_development_progress_documented=true' \
  'github_homepage_architecture_current=true' \
  'python_prototype_runtime_maintained=false' \
  'production_ready=false' \
  'target_hardware_validated=false'
