package com.linker5.observability;

import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;

public interface AppObservability extends AutoCloseable {

    Span startServerSpan(String method, String path);

    Span startChildSpan(String name);

    void recordRequestStarted(String method, String route);

    void recordRequestCompleted(String method, String route, int statusCode, long durationMillis);

    void recordShortLinkCreated();

    void recordDatabaseOperation(String operation, long durationMillis);

    void recordRequestPayloadSize(int sizeInBytes);

    void recordResponseSize(int sizeInBytes);

    void setDatabaseConnected(boolean connected);

    void emitLog(Severity severity, String message);

    void markError(Span span, Exception exception);

    @Override
    default void close() {
        // no-op by default
    }
}
