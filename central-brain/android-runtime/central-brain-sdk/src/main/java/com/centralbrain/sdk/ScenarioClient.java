package com.centralbrain.sdk;

import com.centralbrain.sdk.session.SessionHandle;
import com.centralbrain.sdk.session.SessionPage;
import com.centralbrain.sdk.session.SessionQuery;
import com.centralbrain.sdk.session.SessionRequest;
import com.centralbrain.sdk.session.SessionSnapshot;

/** Stage 2 scenario/session facade. Public callers never operate Binder primitives. */
public interface ScenarioClient extends AutoCloseable {
    String ERROR_NOT_CONNECTED = RuntimeContractV2.ERROR_NOT_CONNECTED;
    String ERROR_PROTOCOL_MISMATCH = RuntimeContractV2.ERROR_PROTOCOL_MISMATCH;
    String ERROR_TRANSPORT = RuntimeContractV2.ERROR_TRANSPORT;
    String ERROR_SUBSCRIPTION = RuntimeContractV2.ERROR_SUBSCRIPTION;
    String ERROR_CLOSED = RuntimeContractV2.ERROR_CLOSED;

    interface ConnectionListener {
        void onConnected(ScenarioClient client, boolean reconnected);

        void onDisconnected();

        void onConnectionFailed(String code, String message);
    }

    boolean connect();

    boolean reconnect();

    boolean isConnected();

    SessionHandle openSession(SessionRequest request, RuntimeEventListener listener);

    SessionSnapshot getSession(SessionHandle handle);

    SessionPage listSessions(SessionQuery query);

    boolean cancelSession(SessionHandle handle, int reasonCode);

    void observeSession(
            SessionHandle handle,
            String resumeCursor,
            RuntimeEventListener listener);

    void stopObserving(SessionHandle handle);

    @Override
    void close();

    /** Stable unchecked SDK failure. Binder/RemoteException details stay internal. */
    final class Failure extends RuntimeException {
        private final String code;

        public Failure(String code, String message) {
            this(code, message, null);
        }

        public Failure(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }
}
