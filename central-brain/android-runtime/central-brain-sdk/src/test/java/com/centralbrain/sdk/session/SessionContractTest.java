package com.centralbrain.sdk.session;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public final class SessionContractTest {
    private static final long NOW = 1_750_000_000_000L;
    private static final String REQUEST_ID = "8d595630-2255-4f4d-ac0f-26a20ee96f29";
    private static final String SESSION_ID = "9bffbb6a-5a0b-41b5-a924-bcfb45be4f26";

    @Test
    public void acceptsBoundedSessionContract() {
        SessionContract.validateRequest(validRequest(), NOW);
        SessionContract.validateHandle(validHandle());
        SessionContract.validateSnapshot(validSnapshot());
        SessionContract.validateQuery(validQuery());

        SessionPage page = new SessionPage();
        page.sessions = new SessionSnapshot[] {validSnapshot()};
        page.generatedAtEpochMs = NOW;
        SessionContract.validatePage(page);

        assertTrue(SessionContract.isTerminalState(
                ICentralBrainSessionRuntime.SESSION_STATE_COMPLETED));
        assertFalse(SessionContract.isTerminalState(
                ICentralBrainSessionRuntime.SESSION_STATE_EXECUTING));
    }

    @Test
    public void rejectsOversizeAndUnknownRequestFields() {
        SessionRequest oversizeRequest = validRequest();
        oversizeRequest.utterance = "x".repeat(SessionContract.MAX_UTTERANCE_CHARS + 1);
        expectViolation(() -> SessionContract.validateRequest(oversizeRequest, NOW));

        SessionRequest unknownSourceRequest = validRequest();
        unknownSourceRequest.source = 99;
        expectViolation(() -> SessionContract.validateRequest(unknownSourceRequest, NOW));

        SessionRequest unknownVersionRequest = validRequest();
        unknownVersionRequest.schemaVersion = 2;
        expectViolation(() -> SessionContract.validateRequest(unknownVersionRequest, NOW));
    }

    @Test
    public void rejectsUnboundedQueryAndUnknownSnapshotState() {
        SessionQuery query = validQuery();
        query.pageSize = SessionContract.MAX_PAGE_SIZE + 1;
        expectViolation(() -> SessionContract.validateQuery(query));

        SessionSnapshot snapshot = validSnapshot();
        snapshot.state = 99;
        expectViolation(() -> SessionContract.validateSnapshot(snapshot));

        SessionPage page = new SessionPage();
        page.sessions = new SessionSnapshot[SessionContract.MAX_PAGE_SIZE + 1];
        page.generatedAtEpochMs = NOW;
        expectViolation(() -> SessionContract.validatePage(page));
    }

    @Test
    public void rejectsExpiredOrUnboundedDeadline() {
        SessionRequest expiredRequest = validRequest();
        expiredRequest.deadlineEpochMs = NOW;
        expectViolation(() -> SessionContract.validateRequest(expiredRequest, NOW));

        SessionRequest unboundedRequest = validRequest();
        unboundedRequest.deadlineEpochMs = NOW + SessionContract.MAX_DEADLINE_FUTURE_MS + 1;
        expectViolation(() -> SessionContract.validateRequest(unboundedRequest, NOW));
    }

    private static SessionRequest validRequest() {
        SessionRequest request = new SessionRequest();
        request.requestId = REQUEST_ID;
        request.scenarioId = "scene.fatigue.assist.v1";
        request.utterance = "I feel tired";
        request.source = ICentralBrainSessionRuntime.SOURCE_HMI_BUTTON;
        request.seatZone = ICentralBrainSessionRuntime.SEAT_ZONE_DRIVER;
        request.locale = "en-US";
        request.deadlineEpochMs = NOW + 60_000;
        request.clientContextVersion = 4;
        return request;
    }

    private static SessionHandle validHandle() {
        SessionHandle handle = new SessionHandle();
        handle.sessionId = SESSION_ID;
        handle.acceptedAtEpochMs = NOW;
        handle.expiresAtEpochMs = NOW + 60_000;
        return handle;
    }

    private static SessionSnapshot validSnapshot() {
        SessionSnapshot snapshot = new SessionSnapshot();
        snapshot.sessionId = SESSION_ID;
        snapshot.requestId = REQUEST_ID;
        snapshot.scenarioId = "scene.fatigue.assist.v1";
        snapshot.state = ICentralBrainSessionRuntime.SESSION_STATE_EXECUTING;
        snapshot.activePlanRevision = 1;
        snapshot.lastEventSequence = 3;
        snapshot.createdAtEpochMs = NOW;
        snapshot.updatedAtEpochMs = NOW + 100;
        snapshot.deadlineEpochMs = NOW + 60_000;
        snapshot.summary = "Applying the governed comfort plan";
        return snapshot;
    }

    private static SessionQuery validQuery() {
        SessionQuery query = new SessionQuery();
        query.stateFilter = ICentralBrainSessionRuntime.SESSION_STATE_ANY;
        query.includeTerminal = true;
        query.pageSize = SessionContract.DEFAULT_PAGE_SIZE;
        return query;
    }

    private static void expectViolation(Runnable operation) {
        try {
            operation.run();
            fail("expected a Session contract violation");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().startsWith("CB_SESSION_CONTRACT:"));
        }
    }
}
