package com.linker5.app;

public record CreateShortLinkRequest(String url, String alias, String publicBaseUrl) {
}
