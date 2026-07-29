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
import com.centralbrain.sdk.model.DevelopmentModelInput;
import com.centralbrain.sdk.model.DevelopmentModelInputContract;
import com.centralbrain.sdk.model.DevelopmentModelInputReceipt;
import com.centralbrain.sdk.model.ICentralBrainDevelopmentModelProjection;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
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
                public DevelopmentModelInputReceipt stageOwnModelInput(
                        DevelopmentModelInput input) {
                    DevelopmentModelInputContract.validateMetadata(input);
                    String owner = authorize(Capability.ORCHESTRATION_START_OWN);
                    if (database.runtimeStateDao().findSessionOwned(
                            input.sessionId, owner) == null) {
                        throw new IllegalArgumentException(
                                "CB_DEVELOPMENT_MODEL_INPUT: Session not found for caller");
                    }
                    byte[] imageBytes =
                            input.inputMode
                                            == DevelopmentModelInputContract
                                                    .INPUT_TEXT_AND_IMAGE
                                    ? readAndValidateImage(input)
                                    : new byte[0];
                    try {
                        DevelopmentModelInputReceipt receipt =
                                DevelopmentModelInputStore.getInstance().stage(
                                        owner, input, imageBytes, System.currentTimeMillis());
                        Log.i(TAG, "development_model_input_staged=true"
                                + " scenario_id=" + input.scenarioId
                                + " input_mode=" + input.inputMode
                                + " image_bytes=" + input.imageByteCount
                                + " raw_model_text_logged=false"
                                + " image_persisted=false"
                                + " hardware_accessed=false");
                        return receipt;
                    } finally {
                        Arrays.fill(imageBytes, (byte) 0);
                    }
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
                            + " image_consumed="
                            + (result != null && result.imageConsumed)
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

    private static byte[] readAndValidateImage(DevelopmentModelInput input) {
        try (FileInputStream stream =
                     new FileInputStream(input.imageFd.getFileDescriptor());
             ByteArrayOutputStream output =
                     new ByteArrayOutputStream((int) input.imageByteCount)) {
            byte[] buffer = new byte[16 * 1024];
            long remaining = input.imageByteCount;
            while (remaining > 0L) {
                int read = stream.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    throw new IllegalArgumentException(
                            "CB_DEVELOPMENT_MODEL_INPUT: image ended early");
                }
                output.write(buffer, 0, read);
                remaining -= read;
            }
            if (stream.read() != -1) {
                throw new IllegalArgumentException(
                        "CB_DEVELOPMENT_MODEL_INPUT: image exceeds declared size");
            }
            byte[] bytes = output.toByteArray();
            if (!input.imageSha256.equals(hex(sha256().digest(bytes)))) {
                Arrays.fill(bytes, (byte) 0);
                throw new IllegalArgumentException(
                        "CB_DEVELOPMENT_MODEL_INPUT: image SHA-256 mismatch");
            }
            boolean png = bytes.length >= 8
                    && (bytes[0] & 0xff) == 0x89
                    && bytes[1] == 0x50
                    && bytes[2] == 0x4e
                    && bytes[3] == 0x47
                    && bytes[4] == 0x0d
                    && bytes[5] == 0x0a
                    && bytes[6] == 0x1a
                    && bytes[7] == 0x0a;
            boolean jpeg = bytes.length >= 3
                    && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8
                    && (bytes[2] & 0xff) == 0xff;
            if (("image/png".equals(input.imageMimeType) && !png)
                    || ("image/jpeg".equals(input.imageMimeType) && !jpeg)) {
                Arrays.fill(bytes, (byte) 0);
                throw new IllegalArgumentException(
                        "CB_DEVELOPMENT_MODEL_INPUT: image signature mismatch");
            }
            return bytes;
        } catch (IOException failure) {
            throw new IllegalArgumentException(
                    "CB_DEVELOPMENT_MODEL_INPUT: image FD read failed", failure);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static String hex(byte[] bytes) {
        char[] output = new char[bytes.length * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xff;
            output[index * 2] = alphabet[value >>> 4];
            output[index * 2 + 1] = alphabet[value & 0xf];
        }
        return new String(output);
    }
}
