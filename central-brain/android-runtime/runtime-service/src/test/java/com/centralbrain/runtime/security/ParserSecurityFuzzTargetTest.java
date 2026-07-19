package com.centralbrain.runtime.security;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

import java.nio.charset.StandardCharsets;

public final class ParserSecurityFuzzTargetTest {
    @Test
    public void allRawAndStructuredSurfaceSelectorsFailClosed() {
        invoke("C{");
        invoke("cseed-checkpoint");
        invoke("S{");
        invoke("sseed-scenario");
        invoke("Tseed-tool-structured");
        invoke("tseed-tool-arbitrary");
    }

    @Test
    public void targetDoesNotMutateCallerInput() {
        byte[] input = "cimmutable".getBytes(StandardCharsets.UTF_8);
        byte[] copy = input.clone();
        ParserSecurityFuzzTarget.fuzzerTestOneInput(input);
        assertArrayEquals(copy, input);
    }

    private static void invoke(String value) {
        ParserSecurityFuzzTarget.fuzzerTestOneInput(
                value.getBytes(StandardCharsets.UTF_8));
    }
}
