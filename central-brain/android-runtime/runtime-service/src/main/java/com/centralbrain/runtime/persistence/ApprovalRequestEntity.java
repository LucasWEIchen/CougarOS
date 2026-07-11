package com.centralbrain.runtime.persistence;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "approval_request",
        indices = @Index(
                name = "index_approval_owner_idempotency",
                value = {"owner_fingerprint", "idempotency_key"},
                unique = true))
public final class ApprovalRequestEntity {
    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "approval_id")
    public String approvalId = "";

    @NonNull
    @ColumnInfo(name = "owner_fingerprint")
    public String ownerFingerprint = "";

    @NonNull
    @ColumnInfo(name = "action_id")
    public String actionId = "";

    @NonNull
    @ColumnInfo(name = "risk_class", defaultValue = "'UNKNOWN'")
    public String riskClass = "";

    @NonNull
    public String state = "";

    @NonNull
    @ColumnInfo(name = "reason_code", defaultValue = "''")
    public String reasonCode = "";

    @NonNull
    @ColumnInfo(name = "idempotency_key", defaultValue = "''")
    public String idempotencyKey = "";

    @ColumnInfo(name = "created_at_wall_ms", defaultValue = "0")
    public long createdAtWallMs;

    @ColumnInfo(name = "expires_at_wall_ms", defaultValue = "0")
    public long expiresAtWallMs;

    @ColumnInfo(name = "updated_at_wall_ms")
    public long updatedAtWallMs;
}
