package com.linker5.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.linker5.app.CreateLinkResult;
import com.linker5.app.Linker;
import com.linker5.observability.AppObservability;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class LinkerHttpHandler implements HttpHandler {

    private static final String APPLICATION_JSON = "application/json";
    private static final Set<String> STATIC_ASSET_DIRECTORIES = Set.of("css", "js");
    private static final Pattern STATIC_ASSET_FILE_NAME = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Gson ERROR_JSON_GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final Linker linker;
    private final Connection db;
    private final AppObservability observability;
    private final Gson gson;
    private final Logger logger;

    public LinkerHttpHandler(Linker linker, Connection db, AppObservability observability, Gson gson, Logger logger) {
        this.linker = linker;
        this.db = db;
        this.observability = observability;
        this.gson = gson;
        this.logger = logger;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        long start = System.nanoTime();
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        String route = resolveRoute(path, method);
        int status = 500;
        Span requestSpan = observability.startServerSpan(method, path);

        try (Scope ignored = requestSpan.makeCurrent()) {
            observability.recordRequestStarted(method, route);
            logInfo("Incoming request: " + method + " " + path);
            logDebug("Route dispatch selected: " + route);

            if (path.equals("/link") && method.equalsIgnoreCase("POST")) {
                status = create(ex);
                return;
            }
            if (path.equals("/")) {
                status = serveStatic(ex, "index.html");
                return;
            }
            if (path.startsWith("/css/") || path.startsWith("/js/")) {
                String staticAssetPath = path.substring(1);
                if (!isSafeStaticAssetPath(staticAssetPath)) {
                    logWarn("Rejected static asset request outside the allowed directory: " + staticAssetPath);
                    send(ex, 404, "Not found", "text/plain");
                    status = 404;
                    return;
                }
                status = serveStatic(ex, staticAssetPath);
                return;
            }
            if (path.equals("/healthz")) {
                status = healthz(ex);
                return;
            }
            if (method.equalsIgnoreCase("HEAD")) {
                status = metadata(ex, path.substring(1));
                return;
            }
            status = redirect(ex, path.substring(1));
        } catch (Exception exception) {
            observability.markError(requestSpan, exception);
            logError("Unhandled error for " + method + " " + path, exception);
            status = 500;
            send(ex, 500, "{\"error\":\"Server error\"}", APPLICATION_JSON);
        } finally {
            requestSpan.setAttribute("http.response.status_code", status);
            requestSpan.end();
            observability.recordRequestCompleted(method, route, status, elapsedMillis(start));
        }
    }

    private int create(HttpExchange ex) throws Exception {
        Span span = observability.startChildSpan("http.create_short_link");
        try (Scope ignored = span.makeCurrent()) {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            observability.recordRequestPayloadSize(body.getBytes(StandardCharsets.UTF_8).length);
            logDebug("Create short URL request body received");

            CreateLinkResult result = createShortLinkOrSendBadRequest(ex, body);
            if (result == null) {
                return 400;
            }

            observability.recordShortLinkCreated();
            span.setAttribute("linker.short_id", result.id());
            logInfo("Short URL created: id=" + result.id());
            send(ex, 201, gson.toJson(result), APPLICATION_JSON);
            return 201;
        } catch (Exception exception) {
            observability.markError(span, exception);
            logError("Short link creation failed", exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    private int healthz(HttpExchange ex) throws IOException {
        Span span = observability.startChildSpan("healthcheck");
        try (Scope ignored = span.makeCurrent()) {
            long dbStart = System.nanoTime();
            boolean ok = linker.isHealthy(db);
            observability.recordDatabaseOperation("SELECT", elapsedMillis(dbStart));
            span.setAttribute("linker.healthcheck.result", ok ? "ok" : "fail");

            if (ok) {
                send(ex, 200, "{\"status\":\"ok\"}", APPLICATION_JSON);
                return 200;
            }
            send(ex, 500, "{\"status\":\"fail\"}", APPLICATION_JSON);
            return 500;
        } catch (Exception exception) {
            observability.markError(span, exception);
            logError("Healthcheck failed", exception);
            send(ex, 500, "{\"status\":\"fail\"}", APPLICATION_JSON);
            return 500;
        } finally {
            span.end();
        }
    }

    private int redirect(HttpExchange ex, String id) throws Exception {
        Span span = observability.startChildSpan("http.redirect_short_link");
        try (Scope ignored = span.makeCurrent()) {
            logDebug("Redirect lookup requested for id=" + id);
            Optional<String> redirectUrl = resolveRedirectWithObservability(id);

            if (redirectUrl.isPresent()) {
                String target = redirectUrl.get();
                span.setAttribute("linker.short_id", id);
                logInfo("Redirect success: id=" + id + " -> " + target);
                ex.getResponseHeaders().add("Location", target);
                ex.sendResponseHeaders(302, -1);
                ex.close();
                return 302;
            }

            logWarn("Redirect not found for id=" + id);
            send(ex, 404, "Short URL not found", "text/plain");
            return 404;
        } catch (Exception exception) {
            observability.markError(span, exception);
            logError("Redirect lookup failed for id=" + id, exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    private int metadata(HttpExchange ex, String id) throws Exception {
        Span span = observability.startChildSpan("http.short_link_metadata");
        try (Scope ignored = span.makeCurrent()) {
            logDebug("Metadata lookup requested for id=" + id);
            long dbStart = System.nanoTime();
            Optional<String> targetUrl = linker.resolveMetadata(id, db);
            observability.recordDatabaseOperation("SELECT", elapsedMillis(dbStart));

            if (targetUrl.isPresent()) {
                span.setAttribute("linker.short_id", id);
                logInfo("Metadata found for id=" + id);
                send(ex, 200, targetUrl.get(), "text/plain");
                return 200;
            }

            logWarn("Metadata not found for id=" + id);
            send(ex, 404, "Short URL not found", "text/plain");
            return 404;
        } catch (Exception exception) {
            observability.markError(span, exception);
            logError("Metadata lookup failed for id=" + id, exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    private CreateLinkResult createShortLinkOrSendBadRequest(HttpExchange ex, String body) throws Exception {
        Span persistSpan = observability.startChildSpan("db.insert_short_url");
        try {
            return createShortLink(body, ex.getRequestHeaders().getFirst("Host"), persistSpan);
        } catch (IllegalArgumentException exception) {
            observability.markError(persistSpan, exception);
            logWarn("Invalid short link creation request: " + exception.getMessage());
            JsonObject errorBody = new JsonObject();
            errorBody.addProperty("error", exception.getMessage());
            send(ex, 400, ERROR_JSON_GSON.toJson(errorBody), APPLICATION_JSON);
            return null;
        } catch (Exception exception) {
            observability.markError(persistSpan, exception);
            throw exception;
        }
    }

    private int serveStatic(HttpExchange ex, String file) throws IOException {
        logDebug("Serving static file: " + file);
        try (InputStream in = LinkerHttpHandler.class.getResourceAsStream("/wwwroot/" + file)) {
            if (in == null) {
                logWarn("Static file not found: " + file);
                send(ex, 404, "Not found", "text/plain");
                return 404;
            }
            byte[] bytes = in.readAllBytes();
            String type = "text/html";
            if (file.endsWith(".css")) {
                type = "text/css";
            } else if (file.endsWith(".js")) {
                type = "application/javascript";
            }
            logDebug("Static file served with content type " + type + ": " + file);
            send(ex, 200, bytes, type);
            return 200;
        }
    }

    private CreateLinkResult createShortLink(String body, String host, Span persistSpan) throws Exception {
        try (Scope persistScope = persistSpan.makeCurrent()) {
            long dbStart = System.nanoTime();
            CreateLinkResult result = linker.createShortLink(body, host, db);
            observability.recordDatabaseOperation("INSERT", elapsedMillis(dbStart));
            return result;
        } finally {
            persistSpan.end();
        }
    }

    private Optional<String> resolveRedirect(String id, Span lookupSpan) throws Exception {
        try (Scope lookupScope = lookupSpan.makeCurrent()) {
            long dbStart = System.nanoTime();
            Optional<String> redirectUrl = linker.resolveRedirect(id, db);
            observability.recordDatabaseOperation("SELECT", elapsedMillis(dbStart));
            return redirectUrl;
        } finally {
            lookupSpan.end();
        }
    }

    private Optional<String> resolveRedirectWithObservability(String id) throws Exception {
        Span lookupSpan = observability.startChildSpan("db.select_short_url");
        try {
            return resolveRedirect(id, lookupSpan);
        } catch (Exception exception) {
            observability.markError(lookupSpan, exception);
            throw exception;
        }
    }

    private void send(HttpExchange ex, int status, String body, String type) throws IOException {
        send(ex, status, body.getBytes(StandardCharsets.UTF_8), type);
    }

    private void send(HttpExchange ex, int status, byte[] bytes, String type) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        observability.recordResponseSize(bytes.length);
        ex.close();
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

    static String resolveRoute(String path, String method) {
        if (path.equals("/healthz")) {
            return "healthcheck";
        }
        if (path.equals("/link") && method.equalsIgnoreCase("POST")) {
            return "create-short-link";
        }
        if (path.equals("/")) {
            return "root-ui";
        }
        if (path.startsWith("/css/") || path.startsWith("/js/")) {
            return "static-asset";
        }
        if (method.equalsIgnoreCase("HEAD")) {
            return "short-link-metadata";
        }
        return "redirect-short-link";
    }

    static long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private void logInfo(String message) {
        String formatted = "[Info] " + message;
        logger.info(formatted);
        observability.emitLog(Severity.INFO, formatted);
    }

    private void logWarn(String message) {
        String formatted = "[Warn] " + message;
        logger.warning(formatted);
        observability.emitLog(Severity.WARN, formatted);
    }

    private void logDebug(String message) {
        String formatted = "[Debug] " + message;
        logger.fine(formatted);
        observability.emitLog(Severity.DEBUG, formatted);
    }

    private void logError(String message, Exception exception) {
        String formatted = "[Error] " + message;
        logger.log(Level.SEVERE, formatted, exception);
        observability.emitLog(Severity.ERROR, formatted + ": " + exception.getMessage());
    }
}
