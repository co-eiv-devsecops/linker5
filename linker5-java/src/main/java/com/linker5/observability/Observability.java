package com.linker5.observability;

import com.linker5.config.RuntimeConfig;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.exporter.logging.LoggingMetricExporter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.logging.SystemOutLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.OpenTelemetrySdkBuilder;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.SdkLoggerProviderBuilder;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;

import java.time.Duration;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class Observability implements AutoCloseable {

    private static final String NOOP_INSTRUMENTATION_NAME = "com.linker5.noop";
    private static final String OBSERVABILITY_INSTRUMENTATION_NAME = "com.linker5.observability";
    private static final Observability NOOP = new Observability();
    private static final AtomicReference<Observability> GLOBAL = new AtomicReference<>(NOOP);

    private static final AttributeKey<String> ROUTE = AttributeKey.stringKey("linker.route");
    private static final AttributeKey<String> METHOD = AttributeKey.stringKey("http.request.method");
    private static final AttributeKey<Long> STATUS = AttributeKey.longKey("http.response.status_code");
    private static final AttributeKey<String> OPERATION = AttributeKey.stringKey("db.operation");
    private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

    private final OpenTelemetrySdk sdk;
    private final Tracer tracer;
    private final Meter meter;
    private final io.opentelemetry.api.logs.Logger otelLogger;
    private final AtomicInteger inFlightRequests;
    private final AtomicInteger activeDbConnections;
    private final LongCounter requestCounter;
    private final LongCounter errorCounter;
    private final LongCounter shortLinkCounter;
    private final LongCounter databaseOperationCounter;
    private final DoubleHistogram requestDuration;
    private final DoubleHistogram databaseOperationDuration;
    private final DoubleHistogram requestPayloadSize;
    private final DoubleHistogram responseSize;

    private Observability() {
        this.sdk = null;
        OpenTelemetry noop = OpenTelemetry.noop();
        this.tracer = noop.getTracer(NOOP_INSTRUMENTATION_NAME);
        this.meter = noop.getMeter(NOOP_INSTRUMENTATION_NAME);
        this.otelLogger = noop.getLogsBridge().get(NOOP_INSTRUMENTATION_NAME);
        this.inFlightRequests = new AtomicInteger();
        this.activeDbConnections = new AtomicInteger();
        this.requestCounter = null;
        this.errorCounter = null;
        this.shortLinkCounter = null;
        this.databaseOperationCounter = null;
        this.requestDuration = null;
        this.databaseOperationDuration = null;
        this.requestPayloadSize = null;
        this.responseSize = null;
    }

    private Observability(OpenTelemetrySdk sdk) {
        this.sdk = sdk;
        this.tracer = sdk.getTracer(OBSERVABILITY_INSTRUMENTATION_NAME);
        this.meter = sdk.getMeter(OBSERVABILITY_INSTRUMENTATION_NAME);
        this.otelLogger = GlobalOpenTelemetry.get().getLogsBridge().get(OBSERVABILITY_INSTRUMENTATION_NAME);
        this.inFlightRequests = new AtomicInteger();
        this.activeDbConnections = new AtomicInteger();
        this.requestCounter = meter.counterBuilder("linker.http.requests.total")
                .setDescription("Total HTTP requests processed by Linker")
                .build();
        this.errorCounter = meter.counterBuilder("linker.http.errors.total")
                .setDescription("Total HTTP error responses emitted by Linker")
                .build();
        this.shortLinkCounter = meter.counterBuilder("linker.short_links.created.total")
                .setDescription("Total short links created")
                .build();
        this.databaseOperationCounter = meter.counterBuilder("linker.db.operations.total")
                .setDescription("Total database operations executed")
                .build();
        this.requestDuration = meter.histogramBuilder("linker.http.request.duration.ms")
                .setDescription("HTTP request duration in milliseconds")
                .setUnit("ms")
                .build();
        this.databaseOperationDuration = meter.histogramBuilder("linker.db.operation.duration.ms")
                .setDescription("Database operation duration in milliseconds")
                .setUnit("ms")
                .build();
        this.requestPayloadSize = meter.histogramBuilder("linker.http.request.payload.size.bytes")
                .setDescription("Request payload size in bytes")
                .setUnit("By")
                .build();
        this.responseSize = meter.histogramBuilder("linker.http.response.size.bytes")
                .setDescription("Response size in bytes")
                .setUnit("By")
                .build();

        meter.gaugeBuilder("linker.http.requests.in_flight")
                .ofLongs()
                .setDescription("Current in-flight HTTP requests")
                .buildWithCallback(measurement -> measurement.record(inFlightRequests.get()));
        meter.gaugeBuilder("linker.db.connection.state")
                .ofLongs()
                .setDescription("Database connection state: 1 connected, 0 disconnected")
                .buildWithCallback(measurement -> measurement.record(activeDbConnections.get()));
    }

    public static void initialize(RuntimeConfig config) {
        boolean useOtlp = config.otlpEndpoint().isPresent()
                || config.otlpTracesEndpoint().isPresent()
                || config.otlpMetricsEndpoint().isPresent()
                || config.otlpLogsEndpoint().isPresent();

        Resource resource = Resource.getDefault().toBuilder().put(SERVICE_NAME, config.serviceName()).build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(useOtlp
                        ? BatchSpanProcessor.builder(buildSpanExporter(config)).build()
                        : SimpleSpanProcessor.create(LoggingSpanExporter.create()))
                .build();

        SdkMeterProvider meterProvider = SdkMeterProvider.builder()
                .setResource(resource)
                .registerMetricReader(PeriodicMetricReader.builder(useOtlp
                                ? buildMetricExporter(config)
                                : LoggingMetricExporter.create())
                        .setInterval(Duration.ofSeconds(30))
                        .build())
                .build();

        OpenTelemetrySdkBuilder builder = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .setMeterProvider(meterProvider);

        if (config.otelLogExportEnabled()) {
            SdkLoggerProviderBuilder loggerProvider = SdkLoggerProvider.builder()
                    .setResource(resource);

            if (useOtlp) {
                loggerProvider.addLogRecordProcessor(BatchLogRecordProcessor.builder(buildLogExporter(config)).build());
            } else {
                loggerProvider.addLogRecordProcessor(SimpleLogRecordProcessor.create(SystemOutLogRecordExporter.create()));
            }

            builder.setLoggerProvider(loggerProvider.build());
        }

        GLOBAL.set(new Observability(builder.buildAndRegisterGlobal()));
    }

    private static OtlpHttpSpanExporter buildSpanExporter(RuntimeConfig config) {
        var builder = OtlpHttpSpanExporter.builder();
        resolveEndpoint(config.otlpTracesEndpoint(), config.otlpEndpoint()).ifPresent(builder::setEndpoint);
        applyHeaders(builder, config.otlpHeaders());
        return builder.build();
    }

    private static OtlpHttpMetricExporter buildMetricExporter(RuntimeConfig config) {
        var builder = OtlpHttpMetricExporter.builder();
        resolveEndpoint(config.otlpMetricsEndpoint(), config.otlpEndpoint()).ifPresent(builder::setEndpoint);
        applyHeaders(builder, config.otlpHeaders());
        return builder.build();
    }

    private static OtlpHttpLogRecordExporter buildLogExporter(RuntimeConfig config) {
        var builder = OtlpHttpLogRecordExporter.builder();
        resolveEndpoint(config.otlpLogsEndpoint(), config.otlpEndpoint()).ifPresent(builder::setEndpoint);
        applyHeaders(builder, config.otlpHeaders());
        return builder.build();
    }

    private static Optional<String> resolveEndpoint(Optional<String> signalSpecific, Optional<String> common) {
        return signalSpecific.isPresent() ? signalSpecific : common;
    }

    static Map<String, String> parseOtlpHeaders(Optional<String> rawHeaders) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (rawHeaders.isEmpty()) {
            return headers;
        }

        for (String entry : rawHeaders.get().split(",")) {
            String trimmedEntry = entry.trim();
            int separatorIndex = trimmedEntry.indexOf('=');
            boolean hasKeyValuePair = !trimmedEntry.isEmpty()
                    && separatorIndex > 0
                    && separatorIndex < trimmedEntry.length() - 1;

            if (hasKeyValuePair) {
                String key = trimmedEntry.substring(0, separatorIndex).trim();
                String value = trimmedEntry.substring(separatorIndex + 1).trim();
                if (key.isEmpty() || value.isEmpty()) {
                    continue;
                }
                headers.put(key, URLDecoder.decode(value, StandardCharsets.UTF_8));
            }
        }

        return headers;
    }

    private static void applyHeaders(OtlpHeaderBuilderAdapter builder, Optional<String> rawHeaders) {
        parseOtlpHeaders(rawHeaders).forEach(builder::setHeader);
    }

    private static void applyHeaders(io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporterBuilder builder,
                                     Optional<String> rawHeaders) {
        applyHeaders(builder::addHeader, rawHeaders);
    }

    private static void applyHeaders(io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporterBuilder builder,
                                     Optional<String> rawHeaders) {
        applyHeaders(builder::addHeader, rawHeaders);
    }

    private static void applyHeaders(io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporterBuilder builder,
                                     Optional<String> rawHeaders) {
        applyHeaders(builder::addHeader, rawHeaders);
    }

    @FunctionalInterface
    private interface OtlpHeaderBuilderAdapter {
        void setHeader(String key, String value);
    }

    public static Observability get() {
        return GLOBAL.get();
    }

    public Span startServerSpan(String method, String path) {
        return tracer.spanBuilder("http.request")
                .setSpanKind(SpanKind.SERVER)
                .setAttribute("http.request.method", method)
                .setAttribute("url.path", path)
                .startSpan();
    }

    public Span startChildSpan(String name) {
        return tracer.spanBuilder(name)
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
    }

    public void recordRequestStarted(String method, String route) {
        inFlightRequests.incrementAndGet();
        if (requestCounter != null) {
            requestCounter.add(1, Attributes.of(METHOD, method, ROUTE, route));
        }
    }

    public void recordRequestCompleted(String method, String route, int statusCode, long durationMillis) {
        inFlightRequests.decrementAndGet();
        if (requestDuration != null) {
            requestDuration.record(durationMillis, Attributes.of(METHOD, method, ROUTE, route, STATUS, (long) statusCode));
        }
        if (statusCode >= 400 && errorCounter != null) {
            errorCounter.add(1, Attributes.of(METHOD, method, ROUTE, route, STATUS, (long) statusCode));
        }
    }

    public void recordShortLinkCreated() {
        if (shortLinkCounter != null) {
            shortLinkCounter.add(1);
        }
    }

    public void recordDatabaseOperation(String operation, long durationMillis) {
        if (databaseOperationCounter != null) {
            databaseOperationCounter.add(1, Attributes.of(OPERATION, operation));
        }
        if (databaseOperationDuration != null) {
            databaseOperationDuration.record(durationMillis, Attributes.of(OPERATION, operation));
        }
    }

    public void recordRequestPayloadSize(int sizeInBytes) {
        if (requestPayloadSize != null) {
            requestPayloadSize.record(sizeInBytes);
        }
    }

    public void recordResponseSize(int sizeInBytes) {
        if (responseSize != null) {
            responseSize.record(sizeInBytes);
        }
    }

    public void setDatabaseConnected(boolean connected) {
        activeDbConnections.set(connected ? 1 : 0);
    }

    public void emitLog(Severity severity, String message) {
        otelLogger.logRecordBuilder()
                .setSeverity(severity)
                .setBody(message)
                .emit();
    }

    public void markError(Span span, Exception exception) {
        span.recordException(exception);
        span.setStatus(StatusCode.ERROR);
    }

    @Override
    public void close() {
        if (sdk != null) {
            sdk.close();
        }
    }
}
