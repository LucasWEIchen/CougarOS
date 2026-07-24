package com.centralbrain.runtime.scenario;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Map;

public final class SimulatedShoppingPlanningServiceTest {
    private static final String INPUT = "1".repeat(64);
    private static final String APPROVAL = "2".repeat(64);

    @Test
    public void searchAndPreviewRemainSyntheticAndSideEffectFree() {
        SimulatedShoppingPlanningService service =
                new SimulatedShoppingPlanningService();

        SimulatedShoppingPlanningService.Result products =
                service.execute("search_product_catalog", INPUT, Map.of());
        SimulatedShoppingPlanningService.Result route =
                service.execute("preview_purchase_route", INPUT, Map.of());

        assertEquals("SHOPPING_PRODUCTS_READY", products.getStatusCode());
        assertEquals("ROUTE_PREVIEW_READY", route.getStatusCode());
        assertTrue(products.isSynthetic());
        assertFalse(products.isExternalDispatchPerformed());
        assertFalse(products.isPaymentMaterialAccessed());
        assertFalse(route.isVehicleHardwareAccessed());
        assertTrue(products.getResultDigest().matches("[0-9a-f]{64}"));
    }

    @Test
    public void commitAndNavigationRequireTheirOwnConfirmationDigest() {
        SimulatedShoppingPlanningService service =
                new SimulatedShoppingPlanningService();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.execute("commit_order", INPUT, Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.execute(
                        "start_purchase_navigation",
                        INPUT,
                        Map.of("request_purchase_confirmation", APPROVAL)));

        SimulatedShoppingPlanningService.Result order = service.execute(
                "commit_order",
                INPUT,
                Map.of("request_purchase_confirmation", APPROVAL));
        SimulatedShoppingPlanningService.Result navigation = service.execute(
                "start_purchase_navigation",
                INPUT,
                Map.of("request_navigation_confirmation", APPROVAL));

        assertEquals("ORDER_NOT_DISPATCHED", order.getStatusCode());
        assertEquals("NAVIGATION_SIMULATED", navigation.getStatusCode());
        assertFalse(order.isExternalDispatchPerformed());
        assertFalse(navigation.isExternalDispatchPerformed());
    }
}
