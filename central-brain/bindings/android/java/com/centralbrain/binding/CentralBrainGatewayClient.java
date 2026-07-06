package com.centralbrain.binding;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

/**
 * Minimal Android client helper for the Central Brain Binder sample.
 *
 * Req IDs: XSC-001, XSC-002, XSC-003, XSC-004, XSC-005, XSC-006, APP-004,
 * FW-U-003, FW-U-004, FW-U-006, FW-U-008, NV-P-002, NV-P-006, KH-003, KH-006,
 * DEL-001, DEL-002, DEL-003, DEL-004, DEL-005.
 */
public final class CentralBrainGatewayClient {
    public interface Callback {
        void onConnected(CentralBrainGatewayClient client);
        void onDisconnected();
    }

    private final Context context;
    private final Callback callback;
    private ICentralBrainGateway gateway;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            gateway = ICentralBrainGateway.Stub.asInterface(service);
            callback.onConnected(CentralBrainGatewayClient.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            gateway = null;
            callback.onDisconnected();
        }
    };

    public CentralBrainGatewayClient(Context context, Callback callback) {
        this.context = context.getApplicationContext();
        this.callback = callback;
    }

    public boolean bind(String baseUrl) {
        Intent intent = new Intent(CentralBrainGatewayBinderService.ACTION_BIND);
        intent.setPackage(context.getPackageName());
        if (baseUrl != null && !baseUrl.isEmpty()) {
            intent.putExtra(CentralBrainGatewayBinderService.EXTRA_BASE_URL, baseUrl);
        }
        return context.bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    public void unbind() {
        context.unbindService(connection);
        gateway = null;
    }

    public String getStateJson(String traceId) throws RemoteException {
        return requireGateway().getStateJson(traceId);
    }

    public String listEventTopicsJson(String traceId) throws RemoteException {
        return requireGateway().listEventTopicsJson(traceId);
    }

    public String publishEventJson(String traceId, String requestJson) throws RemoteException {
        return requireGateway().publishEventJson(traceId, requestJson);
    }

    public String getRecentEventsJson(String traceId, int limit) throws RemoteException {
        return requireGateway().getRecentEventsJson(traceId, limit);
    }

    public String getUibExtensionsJson(String traceId) throws RemoteException {
        return requireGateway().getUibExtensionsJson(traceId);
    }

    public String getAiSdkCapabilitiesJson(String traceId) throws RemoteException {
        return requireGateway().getAiSdkCapabilitiesJson(traceId);
    }

    public String planAgentTaskJson(String traceId, String requestJson) throws RemoteException {
        return requireGateway().planAgentTaskJson(traceId, requestJson);
    }

    public String executeAgentTaskJson(String traceId, String requestJson) throws RemoteException {
        return requireGateway().executeAgentTaskJson(traceId, requestJson);
    }

    public String listSkillsJson(String traceId) throws RemoteException {
        return requireGateway().listSkillsJson(traceId);
    }

    public String invokeSkillJson(String traceId, String skillId, String requestJson) throws RemoteException {
        return requireGateway().invokeSkillJson(traceId, skillId, requestJson);
    }

    public String queryMemoryJson(String traceId, String requestJson) throws RemoteException {
        return requireGateway().queryMemoryJson(traceId, requestJson);
    }

    public String requestActionJson(String traceId, String requestJson) throws RemoteException {
        return requireGateway().requestActionJson(traceId, requestJson);
    }

    public String invokeServiceJson(String traceId, String requestJson) throws RemoteException {
        return requireGateway().invokeServiceJson(traceId, requestJson);
    }

    public String getServiceContractsJson(String traceId) throws RemoteException {
        return requireGateway().getServiceContractsJson(traceId);
    }

    public String precheckGovernanceJson(String traceId, String requestJson) throws RemoteException {
        return requireGateway().precheckGovernanceJson(traceId, requestJson);
    }

    public String getGovernanceBackendContractJson(String traceId) throws RemoteException {
        return requireGateway().getGovernanceBackendContractJson(traceId);
    }

    public String getGovernanceMigrationCheckJson(String traceId) throws RemoteException {
        return requireGateway().getGovernanceMigrationCheckJson(traceId);
    }

    public String getGovernanceDeploymentPlanJson(String traceId) throws RemoteException {
        return requireGateway().getGovernanceDeploymentPlanJson(traceId);
    }

    public String getBindingDetailJson(String traceId) throws RemoteException {
        return requireGateway().getBindingDetailJson(traceId);
    }

    public String getBindingReadinessJson(String traceId) throws RemoteException {
        return requireGateway().getBindingReadinessJson(traceId);
    }

    public String getDeliveryReadinessJson(String traceId) throws RemoteException {
        return requireGateway().getDeliveryReadinessJson(traceId);
    }

    public String getNativeAdaptersDetailJson(String traceId) throws RemoteException {
        return requireGateway().getNativeAdaptersDetailJson(traceId);
    }

    public String getDriverHalGapsJson(String traceId) throws RemoteException {
        return requireGateway().getDriverHalGapsJson(traceId);
    }

    private ICentralBrainGateway requireGateway() {
        if (gateway == null) {
            throw new IllegalStateException("Central Brain gateway service is not bound");
        }
        return gateway;
    }
}
