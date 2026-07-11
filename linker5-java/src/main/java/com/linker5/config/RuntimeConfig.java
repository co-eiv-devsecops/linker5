package com.linker5.config;

import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;

public record RuntimeConfig(
        int port,
        Level logLevel,
        String serviceName,
        boolean otelLogExportEnabled,
        Optional<String> otlpEndpoint,
        Optional<String> otlpHeaders,
        Optional<String> otlpTracesEndpoint,
        Optional<String> otlpMetricsEndpoint,
        Optional<String> otlpLogsEndpoint
) {

    public static RuntimeConfig load() {
        return new RuntimeConfig(
                resolveInt("linker.port", "PORT", 8080),
                resolveLogLevel(),
                resolveString("otel.service.name", "OTEL_SERVICE_NAME", "linker5-java"),
                resolveBoolean("linker.otel.logs.enabled", "LINKER_OTEL_LOG_EXPORT", false),
                resolveOptionalString("otel.exporter.otlp.endpoint", "OTEL_EXPORTER_OTLP_ENDPOINT"),
                resolveOptionalString("otel.exporter.otlp.headers", "OTEL_EXPORTER_OTLP_HEADERS"),
                resolveOptionalString("otel.exporter.otlp.traces.endpoint", "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT"),
                resolveOptionalString("otel.exporter.otlp.metrics.endpoint", "OTEL_EXPORTER_OTLP_METRICS_ENDPOINT"),
                resolveOptionalString("otel.exporter.otlp.logs.endpoint", "OTEL_EXPORTER_OTLP_LOGS_ENDPOINT")
        );
    }

    static Level resolveLogLevel() {
        return parseLogLevel(resolveString("linker.log.level", "LINKER_LOG_LEVEL", "INFO"));
    }

    static Level parseLogLevel(String rawLevel) {
        String normalized = rawLevel == null ? "INFO" : rawLevel.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "TRACE", "DEBUG", "FINE" -> Level.FINE;
            case "WARN", "WARNING" -> Level.WARNING;
            case "ERROR", "FATAL", "SEVERE" -> Level.SEVERE;
            case "INFO" -> Level.INFO;
            default -> Level.parse(normalized);
        };
    }

    private static int resolveInt(String propertyName, String envName, int defaultValue) {
        String configured = resolveString(propertyName, envName, Integer.toString(defaultValue));
        return Integer.parseInt(configured);
    }

    private static boolean resolveBoolean(String propertyName, String envName, boolean defaultValue) {
        String configured = resolveString(propertyName, envName, Boolean.toString(defaultValue));
        return Boolean.parseBoolean(configured);
    }

    private static Optional<String> resolveOptionalString(String propertyName, String envName) {
        String systemValue = System.getProperty(propertyName);
        if (systemValue != null && !systemValue.isBlank()) {
            return Optional.of(systemValue);
        }

        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return Optional.of(envValue);
        }

        return Optional.empty();
    }

    private static String resolveString(String propertyName, String envName, String defaultValue) {
        String systemValue = System.getProperty(propertyName);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }

        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        return defaultValue;
    }
}
