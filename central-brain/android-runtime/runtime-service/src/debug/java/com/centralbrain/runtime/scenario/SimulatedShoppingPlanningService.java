package com.centralbrain.runtime.scenario;

import com.centralbrain.runtime.persistence.DurableDigest;

import java.util.Map;
import java.util.Objects;

/**
 * Debug-only deterministic shopping and route-planning Tool service.
 *
 * <p>The service produces bounded synthetic projections. It never contacts a merchant, payment
 * provider, map service, vehicle bus, or Driver/HAL surface.
 */
public final class SimulatedShoppingPlanningService {
    public static final String PROFILE_ID =
            "android13-cabin-shopping-route-planning-simulation-v1";

    private static final Map<String, Definition> DEFINITIONS = Map.of(
            "search_product_catalog",
            new Definition("SHOPPING_PRODUCTS_READY",
                    "饮用水候选 3 项 · SYNTHETIC", false, ""),
            "search_purchase_poi",
            new Definition("SHOPPING_MERCHANTS_READY",
                    "便利店/服务区候选 3 项 · SYNTHETIC", false, ""),
            "prepare_order_preview",
            new Definition("SHOPPING_ORDER_PREPARED",
                    "饮用水 500ml ×1 · 价格待确认 · SYNTHETIC", false, ""),
            "preview_purchase_route",
            new Definition("ROUTE_PREVIEW_READY",
                    "最近候选约 2.4 km / 4 min · SYNTHETIC", false, ""),
            "commit_order",
            new Definition("ORDER_NOT_DISPATCHED",
                    "订单已确认 · 外部购物接口未接入 · NOT_DISPATCHED",
                    true, "request_purchase_confirmation"),
            "start_purchase_navigation",
            new Definition("NAVIGATION_SIMULATED",
                    "购物路线已进入 UI 导航仿真 · SYNTHETIC", true,
                    "request_navigation_confirmation"));

    public static final class Result {
        private final String nodeId;
        private final String statusCode;
        private final String displaySummary;
        private final String resultDigest;

        private Result(
                String nodeId,
                String statusCode,
                String displaySummary,
                String resultDigest) {
            this.nodeId = nodeId;
            this.statusCode = statusCode;
            this.displaySummary = displaySummary;
            this.resultDigest = resultDigest;
        }

        public String getNodeId() {
            return nodeId;
        }

        public String getStatusCode() {
            return statusCode;
        }

        public String getDisplaySummary() {
            return displaySummary;
        }

        public String getResultDigest() {
            return resultDigest;
        }

        public boolean isSynthetic() {
            return true;
        }

        public boolean isExternalDispatchPerformed() {
            return false;
        }

        public boolean isPaymentMaterialAccessed() {
            return false;
        }

        public boolean isVehicleHardwareAccessed() {
            return false;
        }
    }

    private static final class Definition {
        private final String statusCode;
        private final String summary;
        private final boolean approvalRequired;
        private final String approvalNodeId;

        private Definition(
                String statusCode,
                String summary,
                boolean approvalRequired,
                String approvalNodeId) {
            this.statusCode = statusCode;
            this.summary = summary;
            this.approvalRequired = approvalRequired;
            this.approvalNodeId = approvalNodeId;
        }
    }

    public Result execute(
            String nodeId,
            String inputDigest,
            Map<String, String> approvalDigests) {
        Definition definition = DEFINITIONS.get(requireNodeId(nodeId));
        if (definition == null) {
            throw violation("Tool node is not allowlisted");
        }
        String canonicalInputDigest = requireDigest(inputDigest, "inputDigest");
        Map<String, String> approvals =
                Objects.requireNonNull(approvalDigests, "approvalDigests");
        String approvalDigest = "";
        if (definition.approvalRequired) {
            approvalDigest = requireDigest(
                    approvals.get(definition.approvalNodeId),
                    "approvalDigest");
        }
        String resultDigest = DurableDigest.sha256(
                "central-brain-shopping-tool-result-v1",
                PROFILE_ID,
                nodeId,
                canonicalInputDigest,
                definition.statusCode,
                approvalDigest);
        return new Result(
                nodeId,
                definition.statusCode,
                definition.summary,
                resultDigest);
    }

    public boolean supports(String nodeId) {
        return nodeId != null && DEFINITIONS.containsKey(nodeId);
    }

    private static String requireNodeId(String value) {
        if (value == null || !value.matches("[a-z][a-z0-9_]{2,63}")) {
            throw violation("nodeId is invalid");
        }
        return value;
    }

    private static String requireDigest(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw violation(field + " is invalid");
        }
        return value;
    }

    private static IllegalArgumentException violation(String message) {
        return new IllegalArgumentException("CB_SIM_SHOPPING_ROUTE: " + message);
    }
}
