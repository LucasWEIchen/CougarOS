package com.centralbrain.sdk;

import android.os.RemoteException;

import com.centralbrain.sdk.event.EventPage;
import com.centralbrain.sdk.event.EventAckRequest;
import com.centralbrain.sdk.event.EventAckResult;
import com.centralbrain.sdk.event.EventPageV2;
import com.centralbrain.sdk.event.EventSubscriptionHandle;
import com.centralbrain.sdk.event.EventSubscriptionRequest;
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

        default void onEventV2(RuntimeEvent event, String resumeCursor) {
            onEvent(event);
        }

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

    default boolean supportsEventV2() {
        return false;
    }

    default int getEventV2ProtocolVersion() throws RemoteException {
        throw new RemoteException("Event V2 is unavailable");
    }

    default String getEventV2ProtocolHash() throws RemoteException {
        throw new RemoteException("Event V2 is unavailable");
    }

    SessionHandle openSession(SessionRequest request) throws RemoteException;

    SessionSnapshot getSession(SessionHandle handle) throws RemoteException;

    SessionPage listSessions(SessionQuery query) throws RemoteException;

    boolean cancelSession(SessionHandle handle, int reasonCode) throws RemoteException;

    EventPage getEvents(String sessionId, String cursor, int limit) throws RemoteException;

    boolean registerSessionCallback(String sessionId, String cursor, EventSink sink)
            throws RemoteException;

    boolean unregisterSessionCallback(String sessionId, EventSink sink) throws RemoteException;

    default EventPageV2 getEventsV2(String sessionId, String cursor, int limit)
            throws RemoteException {
        throw new RemoteException("Event V2 is unavailable");
    }

    default EventSubscriptionHandle registerSessionCallbackV2(
            EventSubscriptionRequest request,
            EventSink sink) throws RemoteException {
        throw new RemoteException("Event V2 is unavailable");
    }

    default EventAckResult acknowledgeV2(EventAckRequest request) throws RemoteException {
        throw new RemoteException("Event V2 is unavailable");
    }

    default boolean unregisterSessionCallbackV2(
            EventSubscriptionHandle handle,
            EventSink sink) throws RemoteException {
        return true;
    }

    default boolean cancelSubscriptionV2(EventSubscriptionHandle handle) throws RemoteException {
        return true;
    }

    @Override
    void close();
}
