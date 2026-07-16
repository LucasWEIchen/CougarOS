package com.centralbrain.sdk.session;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import com.centralbrain.sdk.plan.NodeDependency;
import com.centralbrain.sdk.plan.NodePolicy;
import com.centralbrain.sdk.plan.PlanContract;
import com.centralbrain.sdk.plan.PlanNode;
import com.centralbrain.sdk.plan.ScenarioPlan;

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
            verifyPlanRoundTrips();
            verifyPlanRejections();
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
