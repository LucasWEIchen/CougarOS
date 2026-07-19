package com.centralbrain.runtime.orchestration;

import android.content.Context;

/** Release variant has no trusted target adapter and therefore remains fail closed. */
public final class OrchestrationBackendFactory {
    private OrchestrationBackendFactory() {}

    public static OrchestrationBackend create(Context context) {
        return new FailClosedOrchestrationBackend();
    }
}
