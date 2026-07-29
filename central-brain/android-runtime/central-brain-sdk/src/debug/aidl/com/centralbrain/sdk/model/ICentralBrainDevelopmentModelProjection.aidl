package com.centralbrain.sdk.model;

import com.centralbrain.sdk.model.DevelopmentModelProjection;
import com.centralbrain.sdk.model.DevelopmentModelInput;
import com.centralbrain.sdk.model.DevelopmentModelInputReceipt;

// Debug-only side channel for bounded model UX evidence. Release does not publish this Service.
interface ICentralBrainDevelopmentModelProjection {
    const int INTERFACE_VERSION = 3;
    const String INTERFACE_HASH = "94f13bf8ccf71a13ef568355e604336ed506bbf7f5b46230013cc257e199d5a0";

    int getProtocolVersion();
    String getProtocolHash();
    DevelopmentModelInputReceipt stageOwnModelInput(in DevelopmentModelInput input);
    DevelopmentModelProjection getOwnProjection(String sessionId);
}
