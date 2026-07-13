package com.linker5.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

final class StaticUiHandler {

    private static final Set<String> STATIC_ASSET_DIRECTORIES = Set.of("css", "js");
    private static final Pattern STATIC_ASSET_FILE_NAME = Pattern.compile("[A-Za-z0-9_.-]+");

    private StaticUiHandler() {
    }

    static boolean isStaticUiPath(String path) {
        return path.equals("/") || path.startsWith("/css/") || path.startsWith("/js/");
    }

    static Optional<LinkerResponse> tryServe(String path) throws IOException {
        if (path.equals("/")) {
            return Optional.of(serveResource("index.html"));
        }
        if (!path.startsWith("/css/") && !path.startsWith("/js/")) {
            return Optional.empty();
        }

        String assetPath = path.substring(1);
        if (!isSafeStaticAssetPath(assetPath)) {
            return Optional.of(LinkerResponse.plain(404, "Not found"));
        }
        return Optional.of(serveResource(assetPath));
    }

    static boolean isSafeStaticAssetPath(String file) {
        int separatorIndex = file.indexOf('/');
        if (separatorIndex < 0) {
            return false;
        }
        String directory = file.substring(0, separatorIndex);
        String name = file.substring(separatorIndex + 1);
        return STATIC_ASSET_DIRECTORIES.contains(directory)
                && !name.isEmpty()
                && STATIC_ASSET_FILE_NAME.matcher(name).matches();
    }

    private static LinkerResponse serveResource(String file) throws IOException {
        try (InputStream in = StaticUiHandler.class.getResourceAsStream("/wwwroot/" + file)) {
            if (in == null) {
                return LinkerResponse.plain(404, "Not found");
            }
            return new LinkerResponse(200, in.readAllBytes(), contentType(file), java.util.Map.of(), false);
        }
    }

    private static String contentType(String file) {
        if (file.endsWith(".css")) {
            return "text/css";
        }
        if (file.endsWith(".js")) {
            return "application/javascript";
        }
        return "text/html";
    }
}
