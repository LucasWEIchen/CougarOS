package com.centralbrain.sdk.session;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

/** Physical/API 33 parcel evidence for Stage 2 P1-W01. */
public final class SessionParcelInstrumentation extends Instrumentation {
    private static final String TAG = "CbSessionParcelTest";
    private static final long NOW = 1_750_000_000_000L;
    private static final String REQUEST_ID = "8d595630-2255-4f4d-ac0f-26a20ee96f29";
    private static final String SESSION_ID = "9bffbb6a-5a0b-41b5-a924-bcfb45be4f26";

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            verifyRoundTrips();
            verifyOversizeRejection();
            result.putString(
                    "stream",
                    "\nsession_contract_version=1"
                            + "\nsession_parcel_round_trip_verified=true"
                            + "\nsession_oversize_rejected=true"
                            + "\nsession_unknown_version_rejected=true"
                            + "\nsession_runtime_service_published=false"
                            + "\nhardware_accessed=false\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            Log.e(TAG, "Session parcel instrumentation failed", failure);
            result.putString("stream", "\nSession parcel instrumentation failed:\n"
                    + Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private static void verifyRoundTrips() {
        SessionRequest request = new SessionRequest();
        request.requestId = REQUEST_ID;
        request.scenarioId = "scene.fatigue.assist.v1";
        request.utterance = "I feel tired";
        request.source = ICentralBrainSessionRuntime.SOURCE_HMI_BUTTON;
        request.seatZone = ICentralBrainSessionRuntime.SEAT_ZONE_DRIVER;
        request.locale = "en-US";
        request.deadlineEpochMs = NOW + 60_000;
        request.clientContextVersion = 4;
        SessionRequest requestCopy = roundTrip(request, SessionRequest.CREATOR);
        assertEquals(REQUEST_ID, requestCopy.requestId, "requestId");
        assertEquals(request.scenarioId, requestCopy.scenarioId, "scenarioId");
        SessionContract.validateRequest(requestCopy, NOW);

        SessionHandle handle = new SessionHandle();
        handle.sessionId = SESSION_ID;
        handle.acceptedAtEpochMs = NOW;
        handle.expiresAtEpochMs = NOW + 60_000;
        SessionHandle handleCopy = roundTrip(handle, SessionHandle.CREATOR);
        assertEquals(SESSION_ID, handleCopy.sessionId, "sessionId");
        SessionContract.validateHandle(handleCopy);

        SessionSnapshot snapshot = new SessionSnapshot();
        snapshot.sessionId = SESSION_ID;
        snapshot.requestId = REQUEST_ID;
        snapshot.scenarioId = request.scenarioId;
        snapshot.state = ICentralBrainSessionRuntime.SESSION_STATE_EXECUTING;
        snapshot.activePlanRevision = 1;
        snapshot.lastEventSequence = 3;
        snapshot.createdAtEpochMs = NOW;
        snapshot.updatedAtEpochMs = NOW + 100;
        snapshot.deadlineEpochMs = NOW + 60_000;
        snapshot.summary = "Applying the governed comfort plan";
        SessionSnapshot snapshotCopy = roundTrip(snapshot, SessionSnapshot.CREATOR);
        assertEquals(snapshot.state, snapshotCopy.state, "snapshot state");
        SessionContract.validateSnapshot(snapshotCopy);

        SessionQuery query = new SessionQuery();
        query.stateFilter = ICentralBrainSessionRuntime.SESSION_STATE_EXECUTING;
        query.includeTerminal = false;
        query.cursor = "cursor-1";
        query.pageSize = 10;
        SessionQuery queryCopy = roundTrip(query, SessionQuery.CREATOR);
        assertEquals(query.pageSize, queryCopy.pageSize, "query page size");
        SessionContract.validateQuery(queryCopy);

        SessionPage page = new SessionPage();
        page.sessions = new SessionSnapshot[] {snapshot};
        page.nextCursor = "cursor-2";
        page.hasMore = true;
        page.generatedAtEpochMs = NOW + 200;
        SessionPage pageCopy = roundTrip(page, SessionPage.CREATOR);
        assertEquals(1, pageCopy.sessions.length, "page session count");
        assertEquals("cursor-2", pageCopy.nextCursor, "page cursor");
        SessionContract.validatePage(pageCopy);
    }

    private static void verifyOversizeRejection() {
        SessionRequest request = new SessionRequest();
        request.requestId = REQUEST_ID;
        request.utterance = "x".repeat(SessionContract.MAX_UTTERANCE_CHARS + 1);
        request.source = ICentralBrainSessionRuntime.SOURCE_VOICE;
        request.seatZone = ICentralBrainSessionRuntime.SEAT_ZONE_DRIVER;
        request.locale = "en-US";
        request.deadlineEpochMs = NOW + 60_000;
        expectViolation(() -> SessionContract.validateRequest(request, NOW));

        request.utterance = "I feel tired";
        request.schemaVersion = 2;
        expectViolation(() -> SessionContract.validateRequest(request, NOW));
    }

    private static <T extends Parcelable> T roundTrip(T value, Parcelable.Creator<T> creator) {
        Parcel parcel = Parcel.obtain();
        try {
            value.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return creator.createFromParcel(parcel);
        } finally {
            parcel.recycle();
        }
    }

    private static void expectViolation(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected a Session contract violation");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().startsWith("CB_SESSION_CONTRACT:")) {
                throw expected;
            }
        }
    }

    private static void assertEquals(String expected, String actual, String field) {
        if (!expected.equals(actual)) {
            throw new AssertionError(field + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertEquals(int expected, int actual, String field) {
        if (expected != actual) {
            throw new AssertionError(field + ": expected=" + expected + " actual=" + actual);
        }
    }
}
