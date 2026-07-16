package com.centralbrain.sdk.session;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.centralbrain.sdk.event.ActionEvent;
import com.centralbrain.sdk.event.EventContract;
import com.centralbrain.sdk.event.EventPage;
import com.centralbrain.sdk.event.MessageEvent;
import com.centralbrain.sdk.event.ObservationEvent;
import com.centralbrain.sdk.event.RuntimeEvent;
import com.centralbrain.sdk.plan.NodeDependency;
import com.centralbrain.sdk.plan.NodePolicy;
import com.centralbrain.sdk.plan.PlanContract;
import com.centralbrain.sdk.plan.PlanNode;
import com.centralbrain.sdk.plan.ScenarioPlan;

/** Cumulative physical/API 33 parcel evidence for Stage 2 P1 contracts. */
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
            verifyPlanRoundTrips();
            verifyPlanRejections();
            verifyEventRoundTrips();
            verifyEventRejections();
            result.putString(
                    "stream",
                    "\nsession_contract_version=1"
                            + "\nsession_parcel_round_trip_verified=true"
                            + "\nsession_oversize_rejected=true"
                            + "\nsession_unknown_version_rejected=true"
                            + "\nsession_runtime_service_published=false"
                            + "\nplan_contract_version=1"
                            + "\nplan_parcel_round_trip_verified=true"
                            + "\nplan_cycle_rejected=true"
                            + "\nplan_unknown_node_type_rejected=true"
                            + "\nplan_runtime_published=false"
                            + "\nevent_contract_version=1"
                            + "\nevent_parcel_round_trip_verified=true"
                            + "\nevent_ordering_rejected=true"
                            + "\nevent_parent_rejected=true"
                            + "\nevent_redaction_rejected=true"
                            + "\nevent_cursor_replay_verified=true"
                            + "\nevent_runtime_service_published=false"
                            + "\nevent_callback_service_published=false"
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

    private static void verifyPlanRoundTrips() {
        ScenarioPlan plan = validPlan();
        ScenarioPlan copy = roundTrip(plan, ScenarioPlan.CREATOR);
        assertEquals(plan.planId, copy.planId, "planId");
        assertEquals(plan.nodes.length, copy.nodes.length, "plan node count");
        assertEquals(plan.dependencies.length, copy.dependencies.length, "plan dependency count");
        PlanContract.validatePlan(copy);
    }

    private static void verifyPlanRejections() {
        ScenarioPlan cycle = validPlan();
        cycle.dependencies = new NodeDependency[] {
                dependency("capture-context", "apply-hvac"),
                dependency("apply-hvac", "verify-hvac"),
                dependency("verify-hvac", "capture-context")
        };
        expectPlanViolation(() -> PlanContract.validatePlan(cycle));

        ScenarioPlan unknown = validPlan();
        unknown.nodes[1].nodeType = "shell.execute";
        expectPlanViolation(() -> PlanContract.validatePlan(unknown));
    }

    private static ScenarioPlan validPlan() {
        String digest =
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        PlanNode capture = node("capture-context", "context.capture", digest, false);
        PlanNode apply = node("apply-hvac", "effect.execute", digest, true);
        apply.capabilityId = "vehicle.hvac.temperature";
        apply.resourceKey = "vehicle:hvac:row1-driver";
        apply.idempotencyKey = "device-plan:apply-hvac";
        apply.compensationNodeId = "compensate-hvac";
        apply.policy.verificationRequired = true;
        apply.policy.failureMode = PlanContract.FAILURE_COMPENSATE;
        PlanNode verify = node("verify-hvac", "effect.verify", digest, false);
        PlanNode compensate = node("compensate-hvac", "compensate", digest, false);
        compensate.idempotencyKey = "device-plan:compensate-hvac";

        ScenarioPlan plan = new ScenarioPlan();
        plan.planId = "c9c1ec9f-4d04-4c49-9156-d8e1be2e60a0";
        plan.sessionId = SESSION_ID;
        plan.scenarioId = "scene.cold.assist.v1";
        plan.revision = 1;
        plan.contextDigest = digest;
        plan.planDigest = digest;
        plan.compiledAtEpochMs = NOW;
        plan.deadlineEpochMs = NOW + 120_000;
        plan.nodes = new PlanNode[] {capture, apply, verify, compensate};
        plan.dependencies = new NodeDependency[] {
                dependency("capture-context", "apply-hvac"),
                dependency("apply-hvac", "verify-hvac")
        };
        return plan;
    }

    private static PlanNode node(
            String nodeId, String nodeType, String inputDigest, boolean required) {
        NodePolicy policy = new NodePolicy();
        policy.policyId = "policy.cabin.default";
        policy.policyVersion = 1;
        policy.riskClass = PlanContract.RISK_LOW;
        policy.failureMode = PlanContract.FAILURE_FAIL_PLAN;

        PlanNode node = new PlanNode();
        node.nodeId = nodeId;
        node.nodeType = nodeType;
        node.inputDigest = inputDigest;
        node.timeoutMs = 30_000;
        node.maxAttempts = 1;
        node.required = required;
        node.policy = policy;
        return node;
    }

    private static NodeDependency dependency(String prerequisite, String dependent) {
        NodeDependency dependency = new NodeDependency();
        dependency.prerequisiteNodeId = prerequisite;
        dependency.dependentNodeId = dependent;
        dependency.condition = PlanContract.DEPENDENCY_ON_SUCCESS;
        return dependency;
    }

    private static void verifyEventRoundTrips() {
        ActionEvent action = validAction();
        ActionEvent actionCopy = roundTrip(action, ActionEvent.CREATOR);
        assertEquals(action.actionId, actionCopy.actionId, "event actionId");
        EventContract.validateAction(actionCopy);

        ObservationEvent observation = validObservation();
        ObservationEvent observationCopy = roundTrip(
                observation,
                ObservationEvent.CREATOR);
        assertEquals(
                observation.observationId,
                observationCopy.observationId,
                "event observationId");
        EventContract.validateObservation(observationCopy);

        MessageEvent message = validUserMessage();
        MessageEvent messageCopy = roundTrip(message, MessageEvent.CREATOR);
        assertEquals(message.displayText, messageCopy.displayText, "event message text");
        EventContract.validateMessage(messageCopy);

        RuntimeEvent runtimeEvent = actionRuntimeEvent();
        RuntimeEvent runtimeEventCopy = roundTrip(runtimeEvent, RuntimeEvent.CREATOR);
        assertEquals(runtimeEvent.eventId, runtimeEventCopy.eventId, "eventId");
        assertEquals(runtimeEvent.sequence, runtimeEventCopy.sequence, "event sequence");
        EventContract.validateEvent(runtimeEventCopy);

        EventPage first = initialEventPage();
        EventPage firstCopy = roundTrip(first, EventPage.CREATOR);
        assertEquals(first.events.length, firstCopy.events.length, "event page count");
        EventContract.validatePage(firstCopy);

        EventPage next = nextEventPage();
        EventPage nextCopy = roundTrip(next, EventPage.CREATOR);
        EventContract.validateCursorReplay(firstCopy, nextCopy);
    }

    private static void verifyEventRejections() {
        EventPage ordering = initialEventPage();
        ordering.events[1].sequence = 3;
        expectEventViolation(() -> EventContract.validatePage(ordering));

        EventPage parent = initialEventPage();
        parent.events[1].parentSequence = 3;
        parent.events[1].parentEventId = parent.events[2].eventId;
        expectEventViolation(() -> EventContract.validatePage(parent));

        MessageEvent leaked = redactedAssistantMessage();
        leaked.displayText = "private model response";
        expectEventViolation(() -> EventContract.validateMessage(leaked));

        RuntimeEvent mismatchedSubject = observationRuntimeEvent();
        mismatchedSubject.observation.subjectType = EventContract.SUBJECT_MODEL;
        mismatchedSubject.observation.subjectId = "provider.local.test";
        expectEventViolation(() -> EventContract.validateEvent(mismatchedSubject));
    }

    private static EventPage initialEventPage() {
        EventPage page = new EventPage();
        page.sessionId = SESSION_ID;
        page.events = new RuntimeEvent[] {
                userRuntimeEvent(), actionRuntimeEvent(), observationRuntimeEvent()
        };
        page.nextCursor = "event-cursor-3";
        page.nextSequence = 3;
        page.hasMore = true;
        page.generatedAtEpochMs = NOW + 3;
        return page;
    }

    private static EventPage nextEventPage() {
        EventPage page = new EventPage();
        page.sessionId = SESSION_ID;
        page.requestCursor = "event-cursor-3";
        page.afterSequence = 3;
        page.events = new RuntimeEvent[] {assistantRuntimeEvent()};
        page.nextSequence = 4;
        page.redactionApplied = true;
        page.generatedAtEpochMs = NOW + 4;
        return page;
    }

    private static RuntimeEvent userRuntimeEvent() {
        RuntimeEvent event = baseEvent(
                "ee141cb0-6927-4453-ac1b-53d57921eb0c",
                1,
                "",
                0,
                "UserMessageReceived");
        event.source = EventContract.SOURCE_USER;
        event.privacyClass = EventContract.PRIVACY_SENSITIVE;
        event.payloadKind = EventContract.PAYLOAD_MESSAGE;
        event.message = validUserMessage();
        return event;
    }

    private static RuntimeEvent actionRuntimeEvent() {
        RuntimeEvent event = baseEvent(
                "cc8a7193-3d10-47da-bcea-0583b369c407",
                2,
                "ee141cb0-6927-4453-ac1b-53d57921eb0c",
                1,
                "ActionProposed");
        event.source = EventContract.SOURCE_GRAPH;
        event.payloadKind = EventContract.PAYLOAD_ACTION;
        event.action = validAction();
        return event;
    }

    private static RuntimeEvent observationRuntimeEvent() {
        RuntimeEvent event = baseEvent(
                "32e55938-ec71-4fd4-9530-5bdb9d333818",
                3,
                "cc8a7193-3d10-47da-bcea-0583b369c407",
                2,
                "EffectObserved");
        event.source = EventContract.SOURCE_ADAPTER;
        event.payloadKind = EventContract.PAYLOAD_OBSERVATION;
        event.observation = validObservation();
        return event;
    }

    private static RuntimeEvent assistantRuntimeEvent() {
        RuntimeEvent event = baseEvent(
                "ebef8dbe-4ea4-4ba0-984c-f3a39ec473eb",
                4,
                "32e55938-ec71-4fd4-9530-5bdb9d333818",
                3,
                "AssistantSummaryCreated");
        event.source = EventContract.SOURCE_RUNTIME;
        event.privacyClass = EventContract.PRIVACY_RESTRICTED;
        event.payloadKind = EventContract.PAYLOAD_MESSAGE;
        event.message = redactedAssistantMessage();
        return event;
    }

    private static RuntimeEvent baseEvent(
            String eventId,
            long sequence,
            String parentEventId,
            long parentSequence,
            String type) {
        RuntimeEvent event = new RuntimeEvent();
        event.eventId = eventId;
        event.sequence = sequence;
        event.sessionId = SESSION_ID;
        event.parentEventId = parentEventId;
        event.parentSequence = parentSequence;
        event.type = type;
        event.source = EventContract.SOURCE_RUNTIME;
        event.occurredAtEpochMs = NOW + sequence;
        event.privacyClass = EventContract.PRIVACY_INTERNAL;
        event.payloadDigest = eventDigest();
        event.eventDigest = eventDigest();
        return event;
    }

    private static ActionEvent validAction() {
        ActionEvent action = new ActionEvent();
        action.actionId = "b246f4de-bec4-4b22-9019-1b94dbaf08de";
        action.nodeId = "apply-hvac";
        action.capabilityId = "vehicle.hvac.temperature";
        action.state = EventContract.ACTION_PROPOSED;
        action.actionDigest = eventDigest();
        action.required = true;
        return action;
    }

    private static ObservationEvent validObservation() {
        ObservationEvent observation = new ObservationEvent();
        observation.observationId = "1fdcae20-fe42-4aca-bf1c-79f1a25a7f51";
        observation.subjectType = EventContract.SUBJECT_EFFECT;
        observation.subjectId = "08fc7600-d9b7-4c50-8b46-eabe4a8055ad";
        observation.outcome = EventContract.OUTCOME_OBSERVED;
        observation.quality = EventContract.QUALITY_FRESH;
        observation.evidenceDigest = eventDigest();
        return observation;
    }

    private static MessageEvent validUserMessage() {
        MessageEvent message = new MessageEvent();
        message.messageId = "76015ba7-a1c7-4f95-bec9-eab3eb90105c";
        message.role = EventContract.MESSAGE_USER;
        message.locale = "en-US";
        message.displayText = "I feel tired";
        message.contentDigest = eventDigest();
        return message;
    }

    private static MessageEvent redactedAssistantMessage() {
        MessageEvent message = new MessageEvent();
        message.messageId = "02f6ff8d-650d-4317-8d95-803411b82894";
        message.role = EventContract.MESSAGE_ASSISTANT;
        message.locale = "en-US";
        message.displayText = EventContract.REDACTED_DISPLAY_TEXT;
        message.contentDigest = eventDigest();
        message.redacted = true;
        message.redactionReason = EventContract.REDACTION_PRIVACY;
        return message;
    }

    private static String eventDigest() {
        return "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
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

    private static void expectPlanViolation(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected a Plan contract violation");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().startsWith("CB_PLAN_CONTRACT:")) {
                throw expected;
            }
        }
    }

    private static void expectEventViolation(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected an Event contract violation");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().startsWith("CB_EVENT_CONTRACT:")) {
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

    private static void assertEquals(long expected, long actual, String field) {
        if (expected != actual) {
            throw new AssertionError(field + ": expected=" + expected + " actual=" + actual);
        }
    }
}
