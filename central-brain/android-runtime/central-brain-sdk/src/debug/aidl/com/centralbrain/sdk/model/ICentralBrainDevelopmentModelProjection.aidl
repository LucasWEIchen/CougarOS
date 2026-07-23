package com.centralbrain.sdk.model;

import com.centralbrain.sdk.model.DevelopmentModelProjection;
import com.centralbrain.sdk.model.DevelopmentModelInput;
import com.centralbrain.sdk.model.DevelopmentModelInputReceipt;

// Debug-only side channel for bounded model UX evidence. Release does not publish this Service.
interface ICentralBrainDevelopmentModelProjection {
    const int INTERFACE_VERSION = 2;
    const String INTERFACE_HASH = "c966fbbac6fe3eb48b72d27c09cbc305dece035a55efa5ff0c9e3c6ea0524486";

    int getProtocolVersion();
    String getProtocolHash();
    DevelopmentModelInputReceipt stageOwnMultimodalInput(in DevelopmentModelInput input);
    DevelopmentModelProjection getOwnProjection(String sessionId);
}
