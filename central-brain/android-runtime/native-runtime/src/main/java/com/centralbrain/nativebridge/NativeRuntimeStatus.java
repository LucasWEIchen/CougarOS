package com.centralbrain.nativebridge;

public final class NativeRuntimeStatus {
    public static final int OK = 0;
    public static final int INVALID_ARGUMENT = 1;
    public static final int ABI_MISMATCH = 2;
    public static final int OUT_OF_MEMORY = 3;
    public static final int CAPACITY_EXHAUSTED = 4;
    public static final int NOT_FOUND = 5;
    public static final int BUSY = 6;
    public static final int CLOSED = 7;
    public static final int INTERNAL_ERROR = 8;

    private NativeRuntimeStatus() {}

    public static String nameOf(int status) {
        switch (status) {
            case OK:
                return "OK";
            case INVALID_ARGUMENT:
                return "INVALID_ARGUMENT";
            case ABI_MISMATCH:
                return "ABI_MISMATCH";
            case OUT_OF_MEMORY:
                return "OUT_OF_MEMORY";
            case CAPACITY_EXHAUSTED:
                return "CAPACITY_EXHAUSTED";
            case NOT_FOUND:
                return "NOT_FOUND";
            case BUSY:
                return "BUSY";
            case CLOSED:
                return "CLOSED";
            case INTERNAL_ERROR:
                return "INTERNAL_ERROR";
            default:
                return "UNKNOWN";
        }
    }
}
