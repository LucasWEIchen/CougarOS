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
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/** Build-owned debug frame and text envelope for the Client2 multimodal demo. */
public final class CockpitMultimodalInput implements AutoCloseable {
    public static final String UI_SCENARIO_ID = "cabin.multimodal";
    public static final String SMOKING_UI_SCENARIO_ID = "cabin.smoking";
    public static final String INPUT_TEXT = "处理一下";
    public static final String PNG_MIME_TYPE = "image/png";
    public static final String JPEG_MIME_TYPE = "image/jpeg";
    public static final String FILE_NAME = "2025-SUV-OMS-cabin-photo.png";
    public static final long IMAGE_BYTE_COUNT = 2_244_206L;
    public static final String IMAGE_SHA256 =
            "93441797b96c512a7b87905e4d326fbacdbf3a80e4d336d018a41224a0cd8438";
    public static final String SMOKING_INPUT_TEXT = "检测吸烟";
    public static final int SMOKING_POSITIVE_IMAGE_COUNT = 100;
    public static final int SMOKING_NEGATIVE_IMAGE_COUNT = 100;
    public static final int SMOKING_DATASET_IMAGE_COUNT =
            SMOKING_POSITIVE_IMAGE_COUNT + SMOKING_NEGATIVE_IMAGE_COUNT;

    private static final Random SMOKING_RANDOM = new Random();

    private static final class Profile {
        private final String uiScenarioId;
        private final String resourceName;
        private final String inputText;
        private final String mimeType;
        private final String fileName;
        private final long imageByteCount;
        private final String imageSha256;
        private final int datasetOrdinal;

        private Profile(
                String uiScenarioId,
                String resourceName,
                String inputText,
                String mimeType,
                String fileName,
                long imageByteCount,
                String imageSha256,
                int datasetOrdinal) {
            this.uiScenarioId = uiScenarioId;
            this.resourceName = resourceName;
            this.inputText = inputText;
            this.mimeType = mimeType;
            this.fileName = fileName;
            this.imageByteCount = imageByteCount;
            this.imageSha256 = imageSha256;
            this.datasetOrdinal = datasetOrdinal;
        }

        private Profile resolved(long byteCount, String sha256) {
            return new Profile(
                    uiScenarioId,
                    resourceName,
                    inputText,
                    mimeType,
                    fileName,
                    byteCount,
                    sha256,
                    datasetOrdinal);
        }
    }

    private static final Profile CABIN_ASSISTANCE = new Profile(
            UI_SCENARIO_ID,
            "central_brain_cabin_frame",
            INPUT_TEXT,
            PNG_MIME_TYPE,
            FILE_NAME,
            IMAGE_BYTE_COUNT,
            IMAGE_SHA256,
            0);

    private final Profile profile;
    private byte[] imageBytes;

    private CockpitMultimodalInput(Profile profile, byte[] imageBytes) {
        this.profile = profile;
        this.imageBytes = imageBytes;
    }

    public static CockpitMultimodalInput load(Context context) {
        return load(context, UI_SCENARIO_ID);
    }

    public static CockpitMultimodalInput load(Context context, String uiScenarioId) {
        Profile profile = profile(uiScenarioId, SMOKING_RANDOM);
        Context appContext = Objects.requireNonNull(context, "context")
                .getApplicationContext();
        int resourceId = appContext.getResources().getIdentifier(
                profile.resourceName, "raw", appContext.getPackageName());
        if (resourceId == 0) {
            throw violation("controlled cabin frame resource is unavailable");
        }
        byte[] bytes = readBounded(appContext, resourceId, profile.imageByteCount);
        String actualSha256 = sha256(bytes);
        if (profile.imageByteCount > 0L
                && (bytes.length != profile.imageByteCount
                        || !profile.imageSha256.equals(actualSha256))) {
            Arrays.fill(bytes, (byte) 0);
            throw violation("controlled cabin frame integrity mismatch");
        }
        if (!matchesImageType(profile.mimeType, bytes)) {
            Arrays.fill(bytes, (byte) 0);
            throw violation("controlled cabin frame MIME signature mismatch");
        }
        if (profile.imageByteCount == 0L) {
            profile = profile.resolved(bytes.length, actualSha256);
        }
        return new CockpitMultimodalInput(profile, bytes);
    }

    public DevelopmentModelInput openParcelable(
            String sessionId, String canonicalScenarioId) {
        byte[] bytes = requireOpen();
        DevelopmentModelInput input = new DevelopmentModelInput();
        input.schemaVersion = DevelopmentModelInputContract.SCHEMA_VERSION;
        input.inputMode = DevelopmentModelInputContract.INPUT_TEXT_AND_IMAGE;
        input.sessionId = sessionId;
        input.scenarioId = canonicalScenarioId;
        input.inputText = profile.inputText;
        input.imageMimeType = profile.mimeType;
        input.imageFileName = profile.fileName;
        input.imageByteCount = bytes.length;
        input.imageSha256 = profile.imageSha256;
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

    public String getInputText() { return profile.inputText; }
    public String getImageMimeType() { return profile.mimeType; }
    public String getImageFileName() { return profile.fileName; }
    public String getImageSha256() { return profile.imageSha256; }
    public String getUiScenarioId() { return profile.uiScenarioId; }
    public int getDatasetOrdinal() { return profile.datasetOrdinal; }

    public static boolean isMultimodalScenario(String uiScenarioId) {
        return UI_SCENARIO_ID.equals(uiScenarioId)
                || SMOKING_UI_SCENARIO_ID.equals(uiScenarioId);
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

    private static byte[] readBounded(
            Context context,
            int resourceId,
            long expectedBytes) {
        try (InputStream input = context.getResources().openRawResource(resourceId);
             ByteArrayOutputStream output =
                     new ByteArrayOutputStream((int) Math.min(
                             expectedBytes > 0L ? expectedBytes : 512L * 1024L,
                             DevelopmentModelInputContract.MAX_IMAGE_BYTES))) {
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

    private static Profile profile(String uiScenarioId, Random random) {
        if (UI_SCENARIO_ID.equals(uiScenarioId)) {
            return CABIN_ASSISTANCE;
        }
        if (SMOKING_UI_SCENARIO_ID.equals(uiScenarioId)) {
            int ordinal = selectSmokingOrdinal(random);
            return new Profile(
                    SMOKING_UI_SCENARIO_ID,
                    smokingResourceNameForOrdinal(ordinal),
                    SMOKING_INPUT_TEXT,
                    JPEG_MIME_TYPE,
                    smokingClientFileNameForOrdinal(ordinal),
                    0L,
                    "",
                    ordinal + 1);
        }
        throw violation("multimodal scenario is not registered");
    }

    static int selectSmokingOrdinal(Random random) {
        return Objects.requireNonNull(random, "random")
                .nextInt(SMOKING_DATASET_IMAGE_COUNT);
    }

    static String smokingResourceNameForOrdinal(int ordinal) {
        requireSmokingOrdinal(ordinal);
        boolean positive = ordinal < SMOKING_POSITIVE_IMAGE_COUNT;
        int categoryOrdinal = positive
                ? ordinal + 1
                : ordinal - SMOKING_POSITIVE_IMAGE_COUNT + 1;
        return String.format(
                Locale.ROOT,
                "central_brain_smoking_%s_%03d",
                positive ? "positive" : "negative",
                categoryOrdinal);
    }

    static String smokingClientFileNameForOrdinal(int ordinal) {
        requireSmokingOrdinal(ordinal);
        return String.format(
                Locale.ROOT,
                "cabin-evaluation-frame-%03d.jpg",
                ordinal + 1);
    }

    private static void requireSmokingOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= SMOKING_DATASET_IMAGE_COUNT) {
            throw violation("smoking dataset ordinal is out of bounds");
        }
    }

    private static boolean matchesImageType(String mimeType, byte[] bytes) {
        if (PNG_MIME_TYPE.equals(mimeType)) {
            return bytes.length >= 8
                    && (bytes[0] & 0xff) == 0x89
                    && bytes[1] == 0x50
                    && bytes[2] == 0x4e
                    && bytes[3] == 0x47;
        }
        if (JPEG_MIME_TYPE.equals(mimeType)) {
            return bytes.length >= 4
                    && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8
                    && (bytes[bytes.length - 2] & 0xff) == 0xff
                    && (bytes[bytes.length - 1] & 0xff) == 0xd9;
        }
        return false;
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
