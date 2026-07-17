package com.centralbrain.runtime.graph;

/** Exact-class typed executor contract. Implementations receive no serialized or reflective payload. */
public interface TypedNodeExecutor<
        I extends NodeExecutionInput,
        O extends NodeExecutionOutput> {
    String nodeType();

    Class<I> inputType();

    Class<O> outputType();

    NodeExecutionResult<O> execute(I input);

    default boolean isProductionAuthorized() {
        return false;
    }

    default boolean mayDispatchEffect() {
        return false;
    }

    default boolean mayInvokeModel() {
        return false;
    }

    default boolean mayAccessNetwork() {
        return false;
    }

    default boolean mayAccessHardware() {
        return false;
    }

    default boolean mayPersistRawData() {
        return false;
    }
}
