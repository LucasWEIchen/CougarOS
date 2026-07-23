package com.centralbrain.sdk.model;

import android.os.ParcelFileDescriptor;

// Debug-only, owner-scoped multimodal input. Image bytes travel through the FD, not Binder.
parcelable DevelopmentModelInput {
    int schemaVersion = 1;
    String sessionId = "";
    String scenarioId = "";
    String inputText = "";
    String imageMimeType = "";
    String imageFileName = "";
    long imageByteCount = 0;
    String imageSha256 = "";
    ParcelFileDescriptor imageFd;
}
