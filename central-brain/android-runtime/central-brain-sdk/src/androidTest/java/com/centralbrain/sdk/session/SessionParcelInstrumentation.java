package com.centralbrain.sdk.session;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.centralbrain.sdk.RuntimeEventListener;
import com.centralbrain.sdk.ScenarioClient;
import com.centralbrain.sdk.SessionClient;
import com.centralbrain.sdk.effect.ApprovalPrompt;
import com.centralbrain.sdk.effect.EffectContract;
import com.centralbrain.sdk.effect.EffectIntent;
import com.centralbrain.sdk.effect.UndoHandle;
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

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Cumulative physical/API 33 parcel evidence for Stage 2 P1 contracts. */
public final class SessionParcelInstrumentation extends Instrumentation {
    private static final String TAG = "CbSessionParcelTest";
    private static final long NOW = 1_750_000_000_000L;
    private static final String REQUEST_ID = "8d595630-2255-4f4d-ac0f-26a20ee96f29";
    private static final String SESSION_ID = "9bffbb6a-5a0b-41b5-a924-bcfb45be4f26";
    private boolean liveFacade;

    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        liveFacade = arguments != null && "true".equals(arguments.getString("liveFacade"));
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            if (liveFacade) {
                verifyLiveFacade(result);
                finish(Activity.RESULT_OK, result);
                return;
            }
            verifyRoundTrips();
            verifyOversizeRejection();
            verifyPlanRoundTrips();
            verifyPlanRejections();
            verifyEventRoundTrips();
            verifyEventRejections();
            verifyEffectRoundTrips();
            verifyEffectStateTransitions();
            verifyEffectRejections();
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
                            + "\neffect_contract_version=1"
                            + "\neffect_parcel_round_trip_verified=true"
                            + "\neffect_state_transitions_verified=true"
                            + "\neffect_illegal_terminal_transition_rejected=true"
                            + "\nstale_approval_rejected=true"
                            + "\nexpired_undo_rejected=true"
                            + "\neffect_runtime_service_published=false"
                            + "\napproval_response_service_published=false"
                            + "\nundo_service_published=false"
                            + "\nhardware_accessed=false\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            Log.e(TAG, "Session parcel instrumentation failed", failure);
            result.putString("stream", "\nSession parcel instrumentation failed:\n"
                    + Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void verifyLiveFacade(Bundle result) throws Exception {
        ExecutorService callbacks = Executors.newSingleThreadExecutor();
        CountDownLatch connected = new CountDownLatch(1);
        CountDownLatch reconnected = new CountDownLatch(1);
        CountDownLatch snapshotDelivered = new CountDownLatch(1);
        CountDownLatch initialReplay = new CountDownLatch(1);
        CountDownLatch reconnectReplay = new CountDownLatch(1);
        CountDownLatch twoEvents = new CountDownLatch(2);
        AtomicInteger connectionCount = new AtomicInteger();
        AtomicInteger eventCount = new AtomicInteger();
        AtomicInteger replayCount = new AtomicInteger();
        AtomicReference<String> asyncFailure = new AtomicReference<>("");
        SessionClient client = new SessionClient(
                getContext(),
                callbacks,
                new ScenarioClient.ConnectionListener() {
                    @Override
                    public void onConnected(ScenarioClient ignored, boolean wasReconnected) {
                        connectionCount.incrementAndGet();
                        if (wasReconnected) {
                            reconnected.countDown();
                        } else {
                            connected.countDown();
                        }
                    }

                    @Override
                    public void onDisconnected() {}

                    @Override
                    public void onConnectionFailed(String code, String message) {
                        asyncFailure.compareAndSet("", code + ": " + message);
                    }
                });
        try {
            if (!client.connect()) {
                throw new AssertionError("session facade bindService returned false");
            }
            await(connected, "initial facade connection");

            long now = System.currentTimeMillis();
            SessionRequest request = new SessionRequest();
            request.requestId = UUID.randomUUID().toString();
            request.scenarioId = "scene.fatigue.assist.v1";
            request.utterance = "I feel tired";
            request.source = ICentralBrainSessionRuntime.SOURCE_HMI_BUTTON;
            request.seatZone = ICentralBrainSessionRuntime.SEAT_ZONE_DRIVER;
            request.locale = "en-US";
            request.deadlineEpochMs = now + 60_000;
            RuntimeEventListener events = new RuntimeEventListener() {
                @Override
                public void onSnapshot(SessionSnapshot snapshot) {
                    SessionContract.validateSnapshot(snapshot);
                    snapshotDelivered.countDown();
                }

                @Override
                public void onEvent(RuntimeEvent event) {
                    EventContract.validateEvent(event);
                    eventCount.incrementAndGet();
                    twoEvents.countDown();
                }

                @Override
                public void onReplayComplete(long lastSequence) {
                    int count = replayCount.incrementAndGet();
                    if (count == 1) {
                        initialReplay.countDown();
                    } else if (count == 2) {
                        reconnectReplay.countDown();
                    }
                }

                @Override
                public void onError(String code, String message) {
                    asyncFailure.compareAndSet("", code + ": " + message);
                }
            };

            SessionHandle handle = client.openSession(request, events);
            SessionContract.validateHandle(handle);
            await(snapshotDelivered, "session snapshot");
            await(initialReplay, "initial event replay");

            if (!client.reconnect()) {
                throw new AssertionError("facade reconnect returned false");
            }
            await(reconnected, "facade reconnect");
            await(reconnectReplay, "reconnect event replay");
            if (!client.cancelSession(
                    handle,
                    ICentralBrainSessionRuntime.CANCEL_REASON_USER)) {
                throw new AssertionError("session cancellation was not accepted");
            }
            await(twoEvents, "second runtime event");
            if (!asyncFailure.get().isEmpty()) {
                throw new AssertionError(asyncFailure.get());
            }
            if (eventCount.get() != 2) {
                throw new AssertionError("event replay duplicated delivery: " + eventCount.get());
            }
            client.close();
            client.close();
            try {
                client.reconnect();
                throw new AssertionError("closed facade accepted reconnect");
            } catch (ScenarioClient.Failure expected) {
                if (!ScenarioClient.ERROR_CLOSED.equals(expected.getCode())) {
                    throw expected;
                }
            }
            result.putString(
                    "stream",
                    "\nsdk_facade_v2_available=true"
                            + "\nsession_runtime_service_published=true"
                            + "\nevent_runtime_service_published=true"
                            + "\nevent_callback_service_published=true"
                            + "\nsession_runtime_transient_registry=true"
                            + "\nsession_runtime_persistence_wired=false"
                            + "\nsession_runtime_process_death_rehydration=false"
                            + "\nactive_session_reconnect_resubscribe_verified=true"
                            + "\ncallback_replay_deduplicated=true"
                            + "\nclose_reconnect_idempotency_verified=true"
                            + "\nscenario_execution_enabled=false"
                            + "\nhardware_accessed=false\n");
        } finally {
            client.close();
            callbacks.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch, String operation) throws Exception {
        if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError(operation + " timed out");
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

    private static void verifyEffectRoundTrips() {
        EffectIntent intent = validEffectIntent();
        EffectIntent intentCopy = roundTrip(intent, EffectIntent.CREATOR);
        assertEquals(intent.effectId, intentCopy.effectId, "effect intent ID");
        assertEquals(intent.contextVersion, intentCopy.contextVersion, "effect Context version");
        EffectContract.validateIntent(intentCopy, NOW);

        com.centralbrain.sdk.effect.EffectObservation observation = effectObservation(
                EffectContract.STATE_VERIFIED,
                1,
                7);
        com.centralbrain.sdk.effect.EffectObservation observationCopy = roundTrip(
                observation,
                com.centralbrain.sdk.effect.EffectObservation.CREATOR);
        assertEquals(observation.state, observationCopy.state, "effect observation state");
        EffectContract.validateObservation(observationCopy);

        ApprovalPrompt prompt = validApprovalPrompt();
        ApprovalPrompt promptCopy = roundTrip(prompt, ApprovalPrompt.CREATOR);
        assertEquals(prompt.approvalId, promptCopy.approvalId, "approval ID");
        EffectContract.validateApprovalPrompt(promptCopy);
        EffectContract.validateApprovalResume(
                promptCopy,
                effectDigest('a'),
                effectDigest('b'),
                effectDigest('d'),
                7,
                NOW);

        UndoHandle handle = validUndoHandle();
        UndoHandle handleCopy = roundTrip(handle, UndoHandle.CREATOR);
        assertEquals(handle.undoId, handleCopy.undoId, "undo ID");
        EffectContract.validateUndoHandle(handleCopy);
        EffectContract.validateUndoRequest(
                handleCopy,
                effectDigest('a'),
                effectDigest('7'),
                8,
                NOW);
    }

    private static void verifyEffectStateTransitions() {
        com.centralbrain.sdk.effect.EffectObservation proposed = effectObservation(
                EffectContract.STATE_PROPOSED,
                0,
                1);
        com.centralbrain.sdk.effect.EffectObservation authorized = effectObservation(
                EffectContract.STATE_AUTHORIZED,
                0,
                2);
        com.centralbrain.sdk.effect.EffectObservation prepared = effectObservation(
                EffectContract.STATE_PREPARED,
                1,
                3);
        com.centralbrain.sdk.effect.EffectObservation dispatched = effectObservation(
                EffectContract.STATE_DISPATCHED,
                1,
                4);
        com.centralbrain.sdk.effect.EffectObservation delivered = effectObservation(
                EffectContract.STATE_DELIVERED,
                1,
                5);
        com.centralbrain.sdk.effect.EffectObservation applied = effectObservation(
                EffectContract.STATE_APPLIED,
                1,
                6);
        com.centralbrain.sdk.effect.EffectObservation verified = effectObservation(
                EffectContract.STATE_VERIFIED,
                1,
                7);
        EffectContract.validateTransition(proposed, authorized);
        EffectContract.validateTransition(authorized, prepared);
        EffectContract.validateTransition(prepared, dispatched);
        EffectContract.validateTransition(dispatched, delivered);
        EffectContract.validateTransition(delivered, applied);
        EffectContract.validateTransition(applied, verified);
    }

    private static void verifyEffectRejections() {
        com.centralbrain.sdk.effect.EffectObservation dispatched = effectObservation(
                EffectContract.STATE_DISPATCHED,
                1,
                1);
        com.centralbrain.sdk.effect.EffectObservation verified = effectObservation(
                EffectContract.STATE_VERIFIED,
                1,
                2);
        expectEffectViolation(() -> EffectContract.validateTransition(dispatched, verified));

        com.centralbrain.sdk.effect.EffectObservation terminal = effectObservation(
                EffectContract.STATE_VERIFIED,
                1,
                3);
        com.centralbrain.sdk.effect.EffectObservation later = effectObservation(
                EffectContract.STATE_COMPENSATING,
                1,
                4);
        expectEffectViolation(() -> EffectContract.validateTransition(terminal, later));

        ApprovalPrompt stale = validApprovalPrompt();
        expectEffectViolation(() -> EffectContract.validateApprovalResume(
                stale,
                effectDigest('a'),
                effectDigest('b'),
                effectDigest('d'),
                8,
                NOW));

        UndoHandle expired = validUndoHandle();
        expectEffectViolation(() -> EffectContract.validateUndoRequest(
                expired,
                effectDigest('a'),
                effectDigest('7'),
                8,
                expired.expiresAtEpochMs));

        com.centralbrain.sdk.effect.EffectObservation simulated = effectObservation(
                EffectContract.STATE_DISPATCHED,
                1,
                9);
        simulated.source = EffectContract.SOURCE_SIMULATION;
        simulated.sourceId = "simulated.vehicle.twin";
        expectEffectViolation(() -> EffectContract.validateObservation(simulated));
    }

    private static EffectIntent validEffectIntent() {
        EffectIntent intent = new EffectIntent();
        intent.effectId = "08fc7600-d9b7-4c50-8b46-eabe4a8055ad";
        intent.sessionId = SESSION_ID;
        intent.planId = "c9c1ec9f-4d04-4c49-9156-d8e1be2e60a0";
        intent.nodeId = "apply-hvac";
        intent.actionId = "b246f4de-bec4-4b22-9019-1b94dbaf08de";
        intent.capabilityId = "vehicle.hvac.temperature";
        intent.targetArea = "vehicle.cabin.row1.driver";
        intent.valueKind = EffectContract.VALUE_DECIMAL;
        intent.decimalValue = 21.5;
        intent.unit = "celsius";
        intent.targetValueDigest = effectDigest('c');
        intent.idempotencyKey = "device-plan:apply-hvac";
        intent.planDigest = effectDigest('a');
        intent.contextDigest = effectDigest('d');
        intent.contextVersion = 7;
        intent.riskClass = EffectContract.RISK_LOW;
        intent.required = true;
        intent.verificationPolicy = EffectContract.VERIFY_REPORTED_TOLERANCE;
        intent.verificationTolerance = 0.5;
        intent.reversible = true;
        intent.compensationDigest = effectDigest('e');
        intent.createdAtEpochMs = NOW - 1_000;
        intent.deadlineEpochMs = NOW + 60_000;
        return intent;
    }

    private static ApprovalPrompt validApprovalPrompt() {
        ApprovalPrompt prompt = new ApprovalPrompt();
        prompt.approvalId = "7bd72ec6-8a04-41d5-a8c4-fe450d80877f";
        prompt.sessionId = SESSION_ID;
        prompt.planId = "c9c1ec9f-4d04-4c49-9156-d8e1be2e60a0";
        prompt.nodeId = "recline-driver-seat";
        prompt.actionId = "b246f4de-bec4-4b22-9019-1b94dbaf08de";
        prompt.effectId = "08fc7600-d9b7-4c50-8b46-eabe4a8055ad";
        prompt.planDigest = effectDigest('a');
        prompt.actionDigest = effectDigest('b');
        prompt.targetValueDigest = effectDigest('c');
        prompt.contextDigest = effectDigest('d');
        prompt.contextVersion = 7;
        prompt.policyId = "policy.cabin.default";
        prompt.policyVersion = 3;
        prompt.riskClass = EffectContract.RISK_HIGH;
        prompt.reasonCode = "CB_APPROVAL_REQUIRED";
        prompt.promptCode = "approval.driver_seat_recline";
        prompt.approvalDigest = effectDigest('f');
        prompt.createdAtEpochMs = NOW - 1_000;
        prompt.expiresAtEpochMs = NOW + 60_000;
        return prompt;
    }

    private static UndoHandle validUndoHandle() {
        UndoHandle handle = new UndoHandle();
        handle.undoId = "de5875c9-468a-4c67-9d0f-0d30a82b9268";
        handle.sessionId = SESSION_ID;
        handle.effectId = "08fc7600-d9b7-4c50-8b46-eabe4a8055ad";
        handle.sourceObservationId = "00000000-0000-4000-8000-000000000007";
        handle.capabilityId = "vehicle.hvac.temperature";
        handle.planDigest = effectDigest('a');
        handle.verifiedObservationDigest = effectDigest('7');
        handle.compensationDigest = effectDigest('e');
        handle.handleDigest = effectDigest('9');
        handle.issuedContextVersion = 7;
        handle.state = EffectContract.UNDO_AVAILABLE;
        handle.createdAtEpochMs = NOW - 1_000;
        handle.expiresAtEpochMs = NOW + 5 * 60_000;
        return handle;
    }

    private static com.centralbrain.sdk.effect.EffectObservation effectObservation(
            int state,
            int attempt,
            int serial) {
        com.centralbrain.sdk.effect.EffectObservation observation =
                new com.centralbrain.sdk.effect.EffectObservation();
        observation.observationId = String.format(
                "00000000-0000-4000-8000-%012d",
                serial);
        observation.effectId = "08fc7600-d9b7-4c50-8b46-eabe4a8055ad";
        observation.sessionId = SESSION_ID;
        observation.actionId = "b246f4de-bec4-4b22-9019-1b94dbaf08de";
        observation.planDigest = effectDigest('a');
        observation.contextVersion = 7;
        observation.state = state;
        observation.source = EffectContract.SOURCE_RUNTIME;
        observation.sourceId = "runtime.effect.coordinator";
        observation.attempt = attempt;
        observation.targetValueDigest = effectDigest('c');
        if (state == EffectContract.STATE_APPLIED
                || state == EffectContract.STATE_VERIFIED
                || state == EffectContract.STATE_COMPENSATING
                || state == EffectContract.STATE_COMPENSATED) {
            observation.reportedValueDigest = effectDigest('7');
        }
        observation.evidenceDigest = effectDigest('6');
        observation.observationDigest = effectDigest(Character.forDigit(serial % 16, 16));
        if (state == EffectContract.STATE_REJECTED) {
            observation.failureCode = "CB_EFFECT_REJECTED";
        } else if (state == EffectContract.STATE_UNKNOWN) {
            observation.failureCode = "CB_EFFECT_OUTCOME_UNKNOWN";
        } else if (state == EffectContract.STATE_FAILED_RETRYABLE) {
            observation.failureCode = "CB_EFFECT_RETRY";
        } else if (state == EffectContract.STATE_FAILED_TERMINAL) {
            observation.failureCode = "CB_EFFECT_FAILED";
        } else if (state == EffectContract.STATE_CANCELLED) {
            observation.failureCode = "CB_EFFECT_CANCELLED";
        }
        observation.occurredAtEpochMs = NOW + serial;
        observation.terminal = state == EffectContract.STATE_REJECTED
                || state == EffectContract.STATE_VERIFIED
                || state == EffectContract.STATE_FAILED_TERMINAL
                || state == EffectContract.STATE_COMPENSATED
                || state == EffectContract.STATE_CANCELLED;
        observation.retryable = state == EffectContract.STATE_FAILED_RETRYABLE;
        return observation;
    }

    private static String effectDigest(char value) {
        return String.valueOf(value).repeat(64);
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

    private static void expectEffectViolation(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected an Effect contract violation");
        } catch (IllegalArgumentException expected) {
            if (!expected.getMessage().startsWith("CB_EFFECT_CONTRACT:")) {
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
