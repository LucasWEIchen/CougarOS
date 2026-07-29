package com.centralbrain.sdk.model;

import com.centralbrain.sdk.model.DevelopmentModelProjection;
import com.centralbrain.sdk.model.DevelopmentModelInput;
import com.centralbrain.sdk.model.DevelopmentModelInputReceipt;

// Debug-only side channel for bounded model UX evidence. Release does not publish this Service.
interface ICentralBrainDevelopmentModelProjection {
    const int INTERFACE_VERSION = 3;
    const String INTERFACE_HASH = "734be8320485292ef97a649a329b0253005563a6d4e7e6ed4638bc04cb23ac1d";

    int getProtocolVersion();
    String getProtocolHash();
    DevelopmentModelInputReceipt stageOwnModelInput(in DevelopmentModelInput input);
    DevelopmentModelProjection getOwnProjection(String sessionId);
}
