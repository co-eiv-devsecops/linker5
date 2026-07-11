package com.linker5.observability;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;

public class NoopAppObservability implements AppObservability {

    @Override
    public Span startServerSpan(String method, String path) {
        return Span.getInvalid();
    }

    @Override
    public Span startChildSpan(String name) {
        return Span.getInvalid();
    }

    @Override
    public void recordRequestStarted(String method, String route) {
        // Intentionally no-op: used when observability is disabled or not relevant for the caller.
    }

    @Override
    public void recordRequestCompleted(String method, String route, int statusCode, long durationMillis) {
        // Intentionally no-op: used when observability is disabled or not relevant for the caller.
    }

    @Override
    public void recordShortLinkCreated() {
        // Intentionally no-op: used when observability is disabled or not relevant for the caller.
    }

    @Override
    public void recordDatabaseOperation(String operation, long durationMillis) {
        // Intentionally no-op: used when observability is disabled or not relevant for the caller.
    }

    @Override
    public void recordRequestPayloadSize(int sizeInBytes) {
        // Intentionally no-op: used when observability is disabled or not relevant for the caller.
    }

    @Override
    public void recordResponseSize(int sizeInBytes) {
        // Intentionally no-op: used when observability is disabled or not relevant for the caller.
    }

    @Override
    public void setDatabaseConnected(boolean connected) {
        // Intentionally no-op: used when observability is disabled or not relevant for the caller.
    }

    @Override
    public void emitLog(Severity severity, String message) {
        // Intentionally no-op: used when observability is disabled or not relevant for the caller.
    }

    @Override
    public void markError(Span span, Exception exception) {
        // Intentionally no-op: used when observability is disabled or not relevant for the caller.
    }
}
