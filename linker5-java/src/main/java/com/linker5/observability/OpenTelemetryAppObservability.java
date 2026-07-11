package com.linker5.observability;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;

public class OpenTelemetryAppObservability implements AppObservability {

    private final Observability observability;

    public OpenTelemetryAppObservability(Observability observability) {
        this.observability = observability;
    }

    @Override
    public Span startServerSpan(String method, String path) {
        return observability.startServerSpan(method, path);
    }

    @Override
    public Span startChildSpan(String name) {
        return observability.startChildSpan(name);
    }

    @Override
    public void recordRequestStarted(String method, String route) {
        observability.recordRequestStarted(method, route);
    }

    @Override
    public void recordRequestCompleted(String method, String route, int statusCode, long durationMillis) {
        observability.recordRequestCompleted(method, route, statusCode, durationMillis);
    }

    @Override
    public void recordShortLinkCreated() {
        observability.recordShortLinkCreated();
    }

    @Override
    public void recordDatabaseOperation(String operation, long durationMillis) {
        observability.recordDatabaseOperation(operation, durationMillis);
    }

    @Override
    public void recordRequestPayloadSize(int sizeInBytes) {
        observability.recordRequestPayloadSize(sizeInBytes);
    }

    @Override
    public void recordResponseSize(int sizeInBytes) {
        observability.recordResponseSize(sizeInBytes);
    }

    @Override
    public void setDatabaseConnected(boolean connected) {
        observability.setDatabaseConnected(connected);
    }

    @Override
    public void emitLog(Severity severity, String message) {
        observability.emitLog(severity, message);
    }

    @Override
    public void markError(Span span, Exception exception) {
        observability.markError(span, exception);
    }

    @Override
    public void close() {
        observability.close();
    }
}
