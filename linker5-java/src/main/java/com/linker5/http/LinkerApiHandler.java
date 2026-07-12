package com.linker5.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.linker5.app.CreateLinkResult;
import com.linker5.app.CreateShortLinkRequest;
import com.linker5.app.LinkerUseCases;
import com.linker5.observability.AppObservability;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

final class LinkerApiHandler {

    private static final String APPLICATION_JSON = "application/json";
    private static final String HTTP_DELETE = "DELETE";
    private static final String LINKER_SHORT_ID = "linker.short_id";
    private static final String DB_OPERATION_SELECT = "SELECT";
    private static final String SHORT_URL_NOT_FOUND = "Short URL not found";
    private static final String ALIAS_FIELD = "alias";
    private static final Gson ERROR_JSON_GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final LinkerUseCases linker;
    private final Connection db;
    private final AppObservability observability;
    private final Gson gson;
    private final Logger logger;

    LinkerApiHandler(LinkerUseCases linker, Connection db, AppObservability observability, Gson gson, Logger logger) {
        this.linker = linker;
        this.db = db;
        this.observability = observability;
        this.gson = gson;
        this.logger = logger;
    }

    LinkerResponse handle(LinkerRequest request) {
        long start = System.nanoTime();
        String path = request.path();
        String method = request.method();
        String route = resolveRoute(path, method);
        int status = 500;
        Span requestSpan = observability.startServerSpan(method, path);

        try (Scope ignored = requestSpan.makeCurrent()) {
            observability.recordRequestStarted(method, route);
            logInfo("Incoming request: " + method + " " + path);
            logDebug("Route dispatch selected: " + route);

            LinkerResponse response = dispatch(request);
            status = response.status();
            return response;
        } catch (IllegalArgumentException exception) {
            observability.markError(requestSpan, exception);
            logWarn("Invalid short link creation request: " + exception.getMessage());
            status = 400;
            JsonObject errorBody = new JsonObject();
            errorBody.addProperty("error", exception.getMessage());
            return LinkerResponse.json(400, ERROR_JSON_GSON.toJson(errorBody));
        } catch (JsonParseException exception) {
            observability.markError(requestSpan, exception);
            logWarn("Invalid JSON in short link creation request");
            status = 400;
            return LinkerResponse.json(400, "{\"error\":\"Invalid JSON\"}");
        } catch (Exception exception) {
            observability.markError(requestSpan, exception);
            logError("Unhandled error for " + method + " " + path, exception);
            status = 500;
            return LinkerResponse.json(500, "{\"error\":\"Server error\"}");
        } finally {
            requestSpan.setAttribute("http.response.status_code", status);
            requestSpan.end();
            observability.recordRequestCompleted(method, route, status, elapsedMillis(start));
        }
    }

    private LinkerResponse dispatch(LinkerRequest request) throws SQLException {
        String path = request.path();
        String method = request.method();

        if (path.equals("/healthz") && method.equalsIgnoreCase("GET")) {
            return healthz();
        }
        if (path.equals("/link") && method.equalsIgnoreCase("POST")) {
            return create(request);
        }
        if (path.equals("/") || path.isBlank()) {
            return LinkerResponse.plain(404, "Not found");
        }

        String id = path.startsWith("/") ? path.substring(1) : path;
        if (id.isBlank()) {
            return LinkerResponse.plain(404, "Not found");
        }
        if (method.equalsIgnoreCase("HEAD")) {
            return metadata(id);
        }
        if (method.equalsIgnoreCase(HTTP_DELETE)) {
            return delete(id);
        }
        if (method.equalsIgnoreCase("GET")) {
            return redirect(id);
        }
        return LinkerResponse.plain(404, "Not found");
    }

    private LinkerResponse create(LinkerRequest request) throws SQLException {
        Span span = observability.startChildSpan("http.create_short_link");
        try (Scope ignored = span.makeCurrent()) {
            observability.recordRequestPayloadSize(request.body().getBytes(StandardCharsets.UTF_8).length);
            logDebug("Create short URL request body received");

            CreateLinkResult result = createShortLink(request);

            observability.recordShortLinkCreated();
            span.setAttribute(LINKER_SHORT_ID, result.id());
            logInfo("Short URL created: id=" + result.id());
            return new LinkerResponse(201, gson.toJson(result).getBytes(StandardCharsets.UTF_8), APPLICATION_JSON, java.util.Map.of(), false);
        } catch (SQLException | RuntimeException exception) {
            observability.markError(span, exception);
            logError("Short link creation failed", exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    private LinkerResponse healthz() {
        Span span = observability.startChildSpan("healthcheck");
        try (Scope ignored = span.makeCurrent()) {
            long dbStart = System.nanoTime();
            boolean ok = linker.isHealthy(db);
            observability.recordDatabaseOperation(DB_OPERATION_SELECT, elapsedMillis(dbStart));
            span.setAttribute("linker.healthcheck.result", ok ? "ok" : "fail");

            if (ok) {
                return LinkerResponse.json(200, "{\"status\":\"ok\"}");
            }
            return LinkerResponse.json(500, "{\"status\":\"fail\"}");
        } catch (Exception exception) {
            observability.markError(span, exception);
            logError("Healthcheck failed", exception);
            return LinkerResponse.json(500, "{\"status\":\"fail\"}");
        } finally {
            span.end();
        }
    }

    private LinkerResponse redirect(String id) throws SQLException {
        Span span = observability.startChildSpan("http.redirect_short_link");
        try (Scope ignored = span.makeCurrent()) {
            logDebug("Redirect lookup requested for id=" + id);
            Optional<String> redirectUrl = resolveRedirectWithObservability(id);

            if (redirectUrl.isPresent()) {
                String target = redirectUrl.get();
                span.setAttribute(LINKER_SHORT_ID, id);
                logInfo("Redirect success: id=" + id + " -> " + target);
                return LinkerResponse.redirect(target);
            }

            logWarn("Redirect not found for id=" + id);
            return LinkerResponse.plain(404, SHORT_URL_NOT_FOUND);
        } catch (SQLException | RuntimeException exception) {
            observability.markError(span, exception);
            logError("Redirect lookup failed for id=" + id, exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    private LinkerResponse metadata(String id) throws SQLException {
        Span span = observability.startChildSpan("http.short_link_metadata");
        try (Scope ignored = span.makeCurrent()) {
            logDebug("Metadata lookup requested for id=" + id);
            long dbStart = System.nanoTime();
            Optional<String> targetUrl = linker.resolveMetadata(id, db);
            observability.recordDatabaseOperation(DB_OPERATION_SELECT, elapsedMillis(dbStart));

            if (targetUrl.isPresent()) {
                span.setAttribute(LINKER_SHORT_ID, id);
                logInfo("Metadata found for id=" + id);
                return LinkerResponse.plain(200, targetUrl.get());
            }

            logWarn("Metadata not found for id=" + id);
            return LinkerResponse.plain(404, SHORT_URL_NOT_FOUND);
        } catch (SQLException | RuntimeException exception) {
            observability.markError(span, exception);
            logError("Metadata lookup failed for id=" + id, exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    private LinkerResponse delete(String id) throws SQLException {
        Span span = observability.startChildSpan("http.delete_short_link");
        try (Scope ignored = span.makeCurrent()) {
            logDebug("Delete requested for id=" + id);
            long dbStart = System.nanoTime();
            boolean deleted = linker.deleteShortLink(id, db);
            observability.recordDatabaseOperation(HTTP_DELETE, elapsedMillis(dbStart));

            if (deleted) {
                span.setAttribute(LINKER_SHORT_ID, id);
                logInfo("Short URL deleted: id=" + id);
                return LinkerResponse.empty(204);
            }

            logWarn("Delete requested for unknown id=" + id);
            return LinkerResponse.plain(404, SHORT_URL_NOT_FOUND);
        } catch (SQLException | RuntimeException exception) {
            observability.markError(span, exception);
            logError("Delete failed for id=" + id, exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    private CreateLinkResult createShortLink(LinkerRequest request) throws SQLException {
        Span persistSpan = observability.startChildSpan("db.insert_short_url");
        try (Scope persistScope = persistSpan.makeCurrent()) {
            long dbStart = System.nanoTime();
            CreateLinkResult result = linker.createShortLink(parseCreateShortLinkRequest(request), db);
            observability.recordDatabaseOperation("INSERT", elapsedMillis(dbStart));
            return result;
        } catch (IllegalArgumentException | JsonParseException | SQLException exception) {
            observability.markError(persistSpan, exception);
            throw exception;
        } finally {
            persistSpan.end();
        }
    }

    private CreateShortLinkRequest parseCreateShortLinkRequest(LinkerRequest request) {
        JsonObject payload = gson.fromJson(request.body(), JsonObject.class);
        if (payload == null || !payload.has("url") || payload.get("url").isJsonNull()) {
            throw new IllegalArgumentException("Missing 'url'");
        }

        String alias = null;
        if (payload.has(ALIAS_FIELD) && !payload.get(ALIAS_FIELD).isJsonNull()) {
            alias = payload.get(ALIAS_FIELD).getAsString();
        }

        return new CreateShortLinkRequest(
                payload.get("url").getAsString(),
                alias,
                derivePublicBaseUrl(request.host(), request.scheme())
        );
    }

    static String derivePublicBaseUrl(String host, String scheme) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Invalid public base URL");
        }
        String resolvedScheme = (scheme == null || scheme.isBlank()) ? "http" : scheme;
        return resolvedScheme + "://" + host;
    }

    private Optional<String> resolveRedirect(String id, Span lookupSpan) throws SQLException {
        try (Scope lookupScope = lookupSpan.makeCurrent()) {
            long dbStart = System.nanoTime();
            Optional<String> redirectUrl = linker.resolveRedirect(id, db);
            observability.recordDatabaseOperation(DB_OPERATION_SELECT, elapsedMillis(dbStart));
            return redirectUrl;
        } finally {
            lookupSpan.end();
        }
    }

    private Optional<String> resolveRedirectWithObservability(String id) throws SQLException {
        Span lookupSpan = observability.startChildSpan("db.select_short_url");
        try {
            return resolveRedirect(id, lookupSpan);
        } catch (SQLException | RuntimeException exception) {
            observability.markError(lookupSpan, exception);
            throw exception;
        }
    }

    static String resolveRoute(String path, String method) {
        if (path.equals("/healthz")) {
            return "healthcheck";
        }
        if (path.equals("/link") && method.equalsIgnoreCase("POST")) {
            return "create-short-link";
        }
        if (method.equalsIgnoreCase("HEAD")) {
            return "short-link-metadata";
        }
        if (method.equalsIgnoreCase(HTTP_DELETE)) {
            return "delete-short-link";
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
