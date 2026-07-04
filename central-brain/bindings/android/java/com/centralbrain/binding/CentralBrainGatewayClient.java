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
 * Req IDs: XSC-002, XSC-003, XSC-004, XSC-005, XSC-006, NV-P-002, DEL-001.
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

    public String invokeServiceJson(String traceId, String requestJson) throws RemoteException {
        return requireGateway().invokeServiceJson(traceId, requestJson);
    }

    public String getBindingDetailJson(String traceId) throws RemoteException {
        return requireGateway().getBindingDetailJson(traceId);
    }

    public String getNativeAdaptersDetailJson(String traceId) throws RemoteException {
        return requireGateway().getNativeAdaptersDetailJson(traceId);
    }

    private ICentralBrainGateway requireGateway() {
        if (gateway == null) {
            throw new IllegalStateException("Central Brain gateway service is not bound");
        }
        return gateway;
    }
}
