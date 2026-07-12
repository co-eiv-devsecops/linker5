package com.linker5.http;

public record LinkerRequest(
        String method,
        String path,
        String host,
        String scheme,
        String body
) {
}
