package com.centralbrain.runtime.orchestration;

import android.content.Context;

/** Debug variant enables only the explicit simulation profile. */
public final class OrchestrationBackendFactory {
    private OrchestrationBackendFactory() {}

    public static OrchestrationBackend create(Context context) {
        return new DebugSimulatedOrchestrationBackend(context);
    }
}
