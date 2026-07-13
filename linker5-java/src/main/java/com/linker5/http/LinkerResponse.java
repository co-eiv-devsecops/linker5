package com.linker5.http;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public record LinkerResponse(
        int status,
        byte[] body,
        String contentType,
        Map<String, String> headers,
        boolean omitBody
) {

    public static LinkerResponse json(int status, String body) {
        return new LinkerResponse(status, body.getBytes(StandardCharsets.UTF_8), "application/json", Map.of(), false);
    }

    public static LinkerResponse plain(int status, String body) {
        return new LinkerResponse(status, body.getBytes(StandardCharsets.UTF_8), "text/plain", Map.of(), false);
    }

    public static LinkerResponse redirect(String location) {
        return new LinkerResponse(302, new byte[0], null, Map.of("Location", location), true);
    }

    public static LinkerResponse empty(int status) {
        return new LinkerResponse(status, new byte[0], null, Map.of(), true);
    }
}
