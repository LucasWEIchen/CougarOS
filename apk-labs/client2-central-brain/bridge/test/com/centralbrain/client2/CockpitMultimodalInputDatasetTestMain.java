package com.centralbrain.client2;

import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/** Host-side contract checks for the bundled smoking evaluation frame catalog. */
public final class CockpitMultimodalInputDatasetTestMain {
    private CockpitMultimodalInputDatasetTestMain() {}

    public static void main(String[] args) {
        check(CockpitMultimodalInput.SMOKING_POSITIVE_IMAGE_COUNT == 100,
                "positive frame count changed");
        check(CockpitMultimodalInput.SMOKING_NEGATIVE_IMAGE_COUNT == 100,
                "negative frame count changed");
        check(CockpitMultimodalInput.SMOKING_DATASET_IMAGE_COUNT == 200,
                "combined frame count changed");

        Set<String> resourceNames = new HashSet<>();
        Set<String> clientFileNames = new HashSet<>();
        for (int ordinal = 0;
                ordinal < CockpitMultimodalInput.SMOKING_DATASET_IMAGE_COUNT;
                ordinal++) {
            String resourceName =
                    CockpitMultimodalInput.smokingResourceNameForOrdinal(ordinal);
            String clientFileName =
                    CockpitMultimodalInput.smokingClientFileNameForOrdinal(ordinal);
            check(resourceNames.add(resourceName), "duplicate Android resource name");
            check(clientFileNames.add(clientFileName), "duplicate client file name");
            check(!clientFileName.contains("positive")
                            && !clientFileName.contains("negative")
                            && !clientFileName.contains("smoking"),
                    "ground-truth label leaked into the model-visible file name");
            if (ordinal < 100) {
                check(resourceName.equals(String.format(
                                Locale.ROOT,
                                "central_brain_smoking_positive_%03d", ordinal + 1)),
                        "positive resource mapping changed");
            } else {
                check(resourceName.equals(String.format(
                                Locale.ROOT,
                                "central_brain_smoking_negative_%03d", ordinal - 99)),
                        "negative resource mapping changed");
            }
        }

        Random seeded = new Random(20260824L);
        Set<Integer> selected = new HashSet<>();
        for (int attempt = 0; attempt < 10_000; attempt++) {
            int ordinal = CockpitMultimodalInput.selectSmokingOrdinal(seeded);
            check(ordinal >= 0 && ordinal < 200, "random selection escaped catalog");
            selected.add(ordinal);
        }
        check(selected.size() == 200, "seeded random sampling did not cover catalog");
        expectRejected(() -> CockpitMultimodalInput.smokingResourceNameForOrdinal(-1));
        expectRejected(() -> CockpitMultimodalInput.smokingResourceNameForOrdinal(200));

        System.out.println("smoking_dataset_positive_frames=100");
        System.out.println("smoking_dataset_negative_frames=100");
        System.out.println("smoking_dataset_random_catalog_size=200");
        System.out.println("smoking_dataset_model_filename_label_leak=false");
    }

    private static void expectRejected(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected contract rejection");
        } catch (IllegalArgumentException expected) {
            // Expected fail-closed result.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
