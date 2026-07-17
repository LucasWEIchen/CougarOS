package com.centralbrain.sdk;

import android.os.RemoteException;

import com.centralbrain.sdk.event.EventPage;
import com.centralbrain.sdk.event.RuntimeEvent;
import com.centralbrain.sdk.session.SessionHandle;
import com.centralbrain.sdk.session.SessionPage;
import com.centralbrain.sdk.session.SessionQuery;
import com.centralbrain.sdk.session.SessionRequest;
import com.centralbrain.sdk.session.SessionSnapshot;

/** Package-private transport seam used by the public facade and deterministic JVM tests. */
interface ScenarioTransport extends AutoCloseable {
    interface Listener {
        void onConnected();

        void onDisconnected();

        void onConnectionFailed(String message);
    }

    interface EventSink {
        void onEvent(RuntimeEvent event);

        void onOverflow(String resumeCursor);

        void onClosed(int reasonCode, String resumeCursor);
    }

    void setListener(Listener listener);

    boolean connect();

    boolean reconnect();

    boolean isConnected();

    int getSessionProtocolVersion() throws RemoteException;

    String getSessionProtocolHash() throws RemoteException;

    int getEventProtocolVersion() throws RemoteException;

    String getEventProtocolHash() throws RemoteException;

    SessionHandle openSession(SessionRequest request) throws RemoteException;

    SessionSnapshot getSession(SessionHandle handle) throws RemoteException;

    SessionPage listSessions(SessionQuery query) throws RemoteException;

    boolean cancelSession(SessionHandle handle, int reasonCode) throws RemoteException;

    EventPage getEvents(String sessionId, String cursor, int limit) throws RemoteException;

    boolean registerSessionCallback(String sessionId, String cursor, EventSink sink)
            throws RemoteException;

    boolean unregisterSessionCallback(String sessionId, EventSink sink) throws RemoteException;

    @Override
    void close();
}
