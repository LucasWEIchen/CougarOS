package com.centralbrain.client2;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.ParcelFileDescriptor;

import com.centralbrain.sdk.model.DevelopmentModelInput;
import com.centralbrain.sdk.model.DevelopmentModelInputContract;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

/** Build-owned debug frame and text envelope for the Client2 multimodal demo. */
public final class CockpitMultimodalInput implements AutoCloseable {
    public static final String UI_SCENARIO_ID = "cabin.multimodal";
    public static final String INPUT_TEXT = "处理一下";
    public static final String MIME_TYPE = "image/png";
    public static final String FILE_NAME = "2025-SUV-OMS-cabin-photo.png";
    public static final long IMAGE_BYTE_COUNT = 2_244_206L;
    public static final String IMAGE_SHA256 =
            "93441797b96c512a7b87905e4d326fbacdbf3a80e4d336d018a41224a0cd8438";

    private byte[] imageBytes;

    private CockpitMultimodalInput(byte[] imageBytes) {
        this.imageBytes = imageBytes;
    }

    public static CockpitMultimodalInput load(Context context) {
        Context appContext = Objects.requireNonNull(context, "context")
                .getApplicationContext();
        int resourceId = appContext.getResources().getIdentifier(
                "central_brain_cabin_frame", "raw", appContext.getPackageName());
        if (resourceId == 0) {
            throw violation("controlled cabin frame resource is unavailable");
        }
        byte[] bytes = readBounded(appContext, resourceId);
        if (bytes.length != IMAGE_BYTE_COUNT || !IMAGE_SHA256.equals(sha256(bytes))) {
            Arrays.fill(bytes, (byte) 0);
            throw violation("controlled cabin frame integrity mismatch");
        }
        if (bytes.length < 8
                || (bytes[0] & 0xff) != 0x89
                || bytes[1] != 0x50
                || bytes[2] != 0x4e
                || bytes[3] != 0x47) {
            Arrays.fill(bytes, (byte) 0);
            throw violation("controlled cabin frame is not PNG");
        }
        return new CockpitMultimodalInput(bytes);
    }

    public DevelopmentModelInput openParcelable(
            String sessionId, String canonicalScenarioId) {
        byte[] bytes = requireOpen();
        DevelopmentModelInput input = new DevelopmentModelInput();
        input.schemaVersion = DevelopmentModelInputContract.SCHEMA_VERSION;
        input.inputMode = DevelopmentModelInputContract.INPUT_TEXT_AND_IMAGE;
        input.sessionId = sessionId;
        input.scenarioId = canonicalScenarioId;
        input.inputText = INPUT_TEXT;
        input.imageMimeType = MIME_TYPE;
        input.imageFileName = FILE_NAME;
        input.imageByteCount = bytes.length;
        input.imageSha256 = IMAGE_SHA256;
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            input.imageFd = pipe[0];
            Thread writer = new Thread(() -> {
                try (ParcelFileDescriptor.AutoCloseOutputStream output =
                             new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    output.write(bytes);
                    output.flush();
                } catch (IOException ignored) {
                    // The reader closed early after rejecting the bounded envelope.
                }
            }, "central-brain-multimodal-fd-writer");
            writer.setDaemon(true);
            writer.start();
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "CB_CLIENT2_MULTIMODAL_INPUT: pipe creation failed", failure);
        }
        DevelopmentModelInputContract.validateMetadata(input);
        return input;
    }

    public Bitmap decodePreview() {
        byte[] bytes = requireOpen();
        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        if (bitmap == null) {
            throw violation("controlled cabin frame decode failed");
        }
        return bitmap;
    }

    public long getImageByteCount() {
        return requireOpen().length;
    }

    @Override
    public void close() {
        if (imageBytes != null) {
            Arrays.fill(imageBytes, (byte) 0);
            imageBytes = null;
        }
    }

    private byte[] requireOpen() {
        if (imageBytes == null) {
            throw violation("multimodal input is closed");
        }
        return imageBytes;
    }

    private static byte[] readBounded(Context context, int resourceId) {
        try (InputStream input = context.getResources().openRawResource(resourceId);
             ByteArrayOutputStream output =
                     new ByteArrayOutputStream((int) IMAGE_BYTE_COUNT)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > DevelopmentModelInputContract.MAX_IMAGE_BYTES) {
                    throw violation("controlled cabin frame exceeds input bound");
                }
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "CB_CLIENT2_MULTIMODAL_INPUT: resource read failed", failure);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            char[] output = new char[digest.length * 2];
            char[] alphabet = "0123456789abcdef".toCharArray();
            for (int index = 0; index < digest.length; index++) {
                int value = digest[index] & 0xff;
                output[index * 2] = alphabet[value >>> 4];
                output[index * 2 + 1] = alphabet[value & 0xf];
            }
            return new String(output);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_CLIENT2_MULTIMODAL_INPUT: " + message);
    }
}
