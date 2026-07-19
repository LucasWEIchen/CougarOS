package com.centralbrain.sdk.model;

import com.centralbrain.sdk.model.DevelopmentModelProjection;

// Debug-only side channel for bounded model UX evidence. Release does not publish this Service.
interface ICentralBrainDevelopmentModelProjection {
    const int INTERFACE_VERSION = 1;
    const String INTERFACE_HASH = "6932c481e3f6556a6ffe336459615687df26419a82f47cb30c074e3ec4ffd015";

    int getProtocolVersion();
    String getProtocolHash();
    DevelopmentModelProjection getOwnProjection(String sessionId);
}
