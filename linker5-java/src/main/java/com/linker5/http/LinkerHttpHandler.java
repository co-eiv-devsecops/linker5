package com.linker5.http;

import com.google.gson.Gson;
import com.linker5.app.LinkerUseCases;
import com.linker5.observability.AppObservability;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import io.opentelemetry.api.logs.Severity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LinkerHttpHandler implements HttpHandler {

    private final AppObservability observability;
    private final Logger logger;
    private final LinkerApiHandler apiHandler;

    public LinkerHttpHandler(LinkerUseCases linker, Connection db, AppObservability observability, Gson gson, Logger logger) {
        this.observability = observability;
        this.logger = logger;
        this.apiHandler = new LinkerApiHandler(linker, db, observability, gson, logger);
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        if (StaticUiHandler.isStaticUiPath(path)) {
            serveStaticResponse(ex, StaticUiHandler.tryServe(path).orElseThrow());
            return;
        }

        String body = method.equalsIgnoreCase("POST")
                ? new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)
                : "";
        LinkerRequest request = new LinkerRequest(method, path, ex.getRequestHeaders().getFirst("Host"), "http", body);
        writeResponse(ex, apiHandler.handle(request));
    }

    static String derivePublicBaseUrl(HttpExchange ex) {
        return LinkerApiHandler.derivePublicBaseUrl(ex.getRequestHeaders().getFirst("Host"), "http");
    }

    private void serveStaticResponse(HttpExchange ex, LinkerResponse response) throws IOException {
        writeResponse(ex, response);
    }

    static boolean isSafeStaticAssetPath(String file) {
        return StaticUiHandler.isSafeStaticAssetPath(file);
    }

    static String resolveRoute(String path, String method) {
        if (StaticUiHandler.isStaticUiPath(path)) {
            if (path.equals("/")) {
                return "root-ui";
            }
            return "static-asset";
        }
        return LinkerApiHandler.resolveRoute(path, method);
    }

    static long elapsedMillis(long start) {
        return LinkerApiHandler.elapsedMillis(start);
    }

    private void writeResponse(HttpExchange ex, LinkerResponse response) throws IOException {
        for (var header : response.headers().entrySet()) {
            ex.getResponseHeaders().set(header.getKey(), header.getValue());
        }
        if (response.contentType() != null) {
            ex.getResponseHeaders().set("Content-Type", response.contentType());
        }
        if (response.omitBody()) {
            ex.sendResponseHeaders(response.status(), -1);
            ex.close();
            return;
        }
        ex.sendResponseHeaders(response.status(), response.body().length);
        ex.getResponseBody().write(response.body());
        observability.recordResponseSize(response.body().length);
        ex.close();
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
