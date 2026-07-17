package com.centralbrain.runtime.vehicle.capability;

/** Immutable availability flags; semantic support is separate from production activation. */
public final class CapabilityAvailability {
    private final boolean readable;
    private final boolean writable;
    private final boolean simulatable;
    private final boolean productionAvailable;
    private final boolean productionAuthorized;

    public CapabilityAvailability(
            boolean readable,
            boolean writable,
            boolean simulatable,
            boolean productionAvailable,
            boolean productionAuthorized) {
        if (!readable && !writable) {
            throw new IllegalArgumentException(
                    "CB_VEHICLE_CAPABILITY: capability must be readable or writable");
        }
        if (productionAuthorized && (!productionAvailable || !writable)) {
            throw new IllegalArgumentException(
                    "CB_VEHICLE_CAPABILITY: production authorization requires an available writable capability");
        }
        this.readable = readable;
        this.writable = writable;
        this.simulatable = simulatable;
        this.productionAvailable = productionAvailable;
        this.productionAuthorized = productionAuthorized;
    }

    public static CapabilityAvailability softwareContract(
            boolean readable,
            boolean writable,
            boolean simulatable) {
        return new CapabilityAvailability(readable, writable, simulatable, false, false);
    }

    public boolean isReadable() {
        return readable;
    }

    public boolean isWritable() {
        return writable;
    }

    public boolean isSimulatable() {
        return simulatable;
    }

    public boolean isProductionAvailable() {
        return productionAvailable;
    }

    public boolean isProductionAuthorized() {
        return productionAuthorized;
    }

    public boolean canUseProduction() {
        return writable && productionAvailable && productionAuthorized;
    }
}
