package com.centralbrain.runtime.effects;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.centralbrain.runtime.persistence.DurableEffectRepository;

import org.junit.Test;

public final class EffectDeliveryActivationGateTest {
    private final EffectDeliveryActivationGate gate = new EffectDeliveryActivationGate();

    @Test
    public void currentEmptyConfigurationIsExplicitlyBlocked() {
        EffectDeliveryActivationGate.Result result = gate.evaluate(
                null,
                new EmptyEffectMaterialSource(),
                DurableEffectRepository.DESTINATION_UIB_ACTION);
        assertFalse(result.isAllowed());
        assertTrue(result.hasBlocker(EffectDeliveryActivationGate.Blocker.ADAPTER_MISSING));
        assertTrue(result.hasBlocker(
                EffectDeliveryActivationGate.Blocker.MATERIAL_SOURCE_EMPTY));
        assertTrue(result.hasBlocker(
                EffectDeliveryActivationGate.Blocker.MATERIAL_SOURCE_NOT_PRODUCTION));
        assertTrue(result.hasBlocker(
                EffectDeliveryActivationGate.Blocker.MATERIAL_NOT_DURABLE));
        assertTrue(result.hasBlocker(
                EffectDeliveryActivationGate.Blocker.MATERIAL_NOT_ENCRYPTED));
        assertTrue(result.hasBlocker(
                EffectDeliveryActivationGate.Blocker.MATERIAL_INTEGRITY_UNBOUND));
        assertTrue(result.hasBlocker(
                EffectDeliveryActivationGate.Blocker.MATERIAL_DELETE_UNSUPPORTED));
        assertTrue(result.hasBlocker(
                EffectDeliveryActivationGate.Blocker.MATERIAL_RETENTION_INVALID));
    }

    @Test
    public void productionConformantDescriptorsPassNecessaryConditionGate() {
        EffectDeliveryActivationGate.Result result = gate.evaluate(
                safeAdapter(),
                source(EffectMaterialSource.Assurance.PRODUCTION, true),
                DurableEffectRepository.DESTINATION_UIB_ACTION);
        assertTrue(result.isAllowed());
    }

    @Test
    public void testOnlyOrUnboundedMaterialCannotActivate() {
        EffectDeliveryActivationGate.Result testOnly = gate.evaluate(
                safeAdapter(),
                source(EffectMaterialSource.Assurance.TEST_ONLY, true),
                DurableEffectRepository.DESTINATION_UIB_ACTION);
        assertFalse(testOnly.isAllowed());
        assertTrue(testOnly.hasBlocker(
                EffectDeliveryActivationGate.Blocker.MATERIAL_SOURCE_NOT_PRODUCTION));

        EffectDeliveryActivationGate.Result unbounded = gate.evaluate(
                safeAdapter(),
                source(EffectMaterialSource.Assurance.PRODUCTION, false),
                DurableEffectRepository.DESTINATION_UIB_ACTION);
        assertFalse(unbounded.isAllowed());
        assertTrue(unbounded.hasBlocker(
                EffectDeliveryActivationGate.Blocker.MATERIAL_RETENTION_INVALID));
    }

    private static EffectAdapter safeAdapter() {
        return new EffectAdapter() {
            @Override
            public Descriptor descriptor() {
                return new Descriptor(
                        "test.safe.adapter",
                        DurableEffectRepository.DESTINATION_UIB_ACTION,
                        IdempotencyMode.TOKEN_DEDUPLICATED,
                        true,
                        true,
                        StatusConsistency.LINEARIZABLE,
                        1_000);
            }

            @Override
            public ApplyResult apply(Invocation invocation) {
                throw new AssertionError("gate evaluation must not invoke apply");
            }

            @Override
            public StatusResult queryStatus(String idempotencyToken) {
                throw new AssertionError("gate evaluation must not query status");
            }
        };
    }

    private static EffectMaterialSource source(
            EffectMaterialSource.Assurance assurance,
            boolean boundedRetention) {
        return new EffectMaterialSource() {
            @Override
            public Descriptor descriptor() {
                return new Descriptor(
                        "test.material",
                        Availability.AVAILABLE,
                        assurance,
                        true,
                        true,
                        true,
                        true,
                        boundedRetention ? 86_400_000L : Long.MAX_VALUE);
            }

            @Override
            public Material resolve(DurableEffectRepository.Snapshot claim) {
                throw new AssertionError("gate evaluation must not resolve material");
            }
        };
    }
}
