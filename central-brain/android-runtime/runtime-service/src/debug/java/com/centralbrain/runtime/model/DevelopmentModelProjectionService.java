package com.centralbrain.runtime.model;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.centralbrain.runtime.BuildConfig;
import com.centralbrain.runtime.R;
import com.centralbrain.runtime.identity.AndroidCallerIdentityResolver;
import com.centralbrain.runtime.identity.CallerIdentitySnapshot;
import com.centralbrain.runtime.identity.DurablePrincipalFingerprint;
import com.centralbrain.runtime.persistence.CentralBrainDatabase;
import com.centralbrain.runtime.policy.AndroidCapabilityPolicyLoader;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy;
import com.centralbrain.runtime.policy.CallerCapabilityPolicy.Capability;
import com.centralbrain.sdk.CentralBrainClient;
import com.centralbrain.sdk.DevelopmentModelProjectionClient;
import com.centralbrain.sdk.model.DevelopmentModelProjection;
import com.centralbrain.sdk.model.ICentralBrainDevelopmentModelProjection;

import java.util.UUID;

/** Debug-only Binder exposing a caller-owned, non-durable model response projection. */
public final class DevelopmentModelProjectionService extends Service {
    private static final String TAG = "CbDevModelProjection";

    private AndroidCallerIdentityResolver identityResolver;
    private CallerCapabilityPolicy capabilityPolicy;
    private CentralBrainDatabase database;

    private final ICentralBrainDevelopmentModelProjection.Stub binder =
            new ICentralBrainDevelopmentModelProjection.Stub() {
                @Override
                public int getProtocolVersion() {
                    authorize(Capability.ORCHESTRATION_PROTOCOL_READ);
                    return ICentralBrainDevelopmentModelProjection.INTERFACE_VERSION;
                }

                @Override
                public String getProtocolHash() {
                    authorize(Capability.ORCHESTRATION_PROTOCOL_READ);
                    return ICentralBrainDevelopmentModelProjection.INTERFACE_HASH;
                }

                @Override
                public DevelopmentModelProjection getOwnProjection(String sessionId) {
                    requireSessionId(sessionId);
                    String owner = authorize(Capability.ORCHESTRATION_READ_OWN);
                    if (database.runtimeStateDao().findSessionOwned(sessionId, owner) == null) {
                        throw new IllegalArgumentException(
                                "CB_DEVELOPMENT_MODEL_PROJECTION: Session not found for caller");
                    }
                    DevelopmentModelProjection result =
                            DevelopmentModelProjectionStore.getInstance()
                                    .getOwn(owner, sessionId);
                    Log.i(TAG, "development_model_projection_read=true"
                            + " projection_available=" + (result != null)
                            + " raw_model_text_logged=false"
                            + " durable_model_text_stored=false"
                            + " hardware_accessed=false");
                    return result;
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        if (!BuildConfig.DEBUG) {
            throw new IllegalStateException("development model projection in non-debug build");
        }
        identityResolver = new AndroidCallerIdentityResolver(this);
        capabilityPolicy = AndroidCapabilityPolicyLoader.load(
                this,
                R.xml.central_brain_capability_policy,
                identityResolver.resolveOwnIdentity());
        database = CentralBrainDatabase.open(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (intent == null
                || !DevelopmentModelProjectionClient.ACTION.equals(
                        intent.getAction())) {
            return null;
        }
        return binder;
    }

    @Override
    public void onDestroy() {
        if (database != null) {
            database.close();
        }
        super.onDestroy();
    }

    private String authorize(Capability capability) {
        enforceCallingOrSelfPermission(
                CentralBrainClient.BIND_PERMISSION,
                "Central Brain signature permission required");
        CallerIdentitySnapshot caller = identityResolver.resolveCallingIdentity();
        CallerCapabilityPolicy.Decision decision = capabilityPolicy.evaluate(caller, capability);
        if (!decision.isAllowed()) {
            throw new SecurityException("Central Brain capability denied: " + capability.getId());
        }
        return DurablePrincipalFingerprint.from(caller);
    }

    private static void requireSessionId(String value) {
        try {
            if (!UUID.fromString(value).toString().equals(value)) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(
                    "CB_DEVELOPMENT_MODEL_PROJECTION: invalid sessionId");
        }
    }
}
