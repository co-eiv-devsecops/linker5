package com.linker5;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObservabilityTest {

    @Test
    void shouldParseOtlpHeaders() {
        Map<String, String> headers = Observability.parseOtlpHeaders(Optional.of(
                "Authorization=Basic%20token123, X-Scope-OrgID = tenant-a "
        ));

        assertEquals(Map.of(
                "Authorization", "Basic token123",
                "X-Scope-OrgID", "tenant-a"
        ), headers);
    }

    @Test
    void shouldIgnoreMalformedOtlpHeaders() {
        Map<String, String> headers = Observability.parseOtlpHeaders(Optional.of(
                "Authorization=Basic token123, invalid, =missing-key, missing-value="
        ));

        assertEquals(Map.of("Authorization", "Basic token123"), headers);
    }
}
