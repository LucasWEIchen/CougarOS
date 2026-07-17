package com.centralbrain.runtime.session;

import com.centralbrain.sdk.event.EventPage;
import com.centralbrain.sdk.event.RuntimeEvent;
import com.centralbrain.sdk.session.SessionHandle;
import com.centralbrain.sdk.session.SessionPage;
import com.centralbrain.sdk.session.SessionQuery;
import com.centralbrain.sdk.session.SessionRequest;
import com.centralbrain.sdk.session.SessionSnapshot;

/** Owner-scoped persistence boundary used by the Session/Event Binder endpoint. */
public interface SessionRegistry {
    SessionHandle openOwned(String owner, SessionRequest request);

    SessionSnapshot findOwned(String owner, SessionHandle handle);

    SessionPage listOwned(String owner, SessionQuery query);

    CancelResult cancelOwned(String owner, SessionHandle handle, int reasonCode);

    EventPage eventsOwned(String owner, String sessionId, String cursor, int limit);

    int size();

    final class CancelResult {
        private final boolean changed;
        private final RuntimeEvent event;

        public CancelResult(boolean changed, RuntimeEvent event) {
            this.changed = changed;
            this.event = event;
        }

        public boolean isChanged() {
            return changed;
        }

        public RuntimeEvent getEvent() {
            return event;
        }
    }
}
