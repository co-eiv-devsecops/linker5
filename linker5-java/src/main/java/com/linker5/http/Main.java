package com.linker5.http;

import com.linker5.app.LinkerUseCases;
import com.linker5.config.RuntimeConfig;
import com.linker5.flags.EnvFeatureFlagProvider;
import com.linker5.flags.FeatureFlagProvider;
import com.linker5.flags.LaunchDarklyFeatureFlagProvider;
import com.linker5.observability.AppObservability;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;

import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws Exception {
        LinkerApplicationRuntime runtime = LinkerApplicationRuntime.initialize();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                runtime.close();
            } catch (Exception ignored) {
                // Best-effort shutdown cleanup.
            }
        }));

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(runtime.config().port()), 0);
            server.createContext("/", new LinkerHttpHandler(runtime.linker(), runtime.db(), runtime.observability(), runtime.gson(), LOG));
            server.start();

            emitInfo(runtime.observability(), "Linker escuchando en el puerto " + runtime.config().port());
            emitInfo(runtime.observability(), "OpenTelemetry inicializado para el servicio " + runtime.config().serviceName());
        } catch (Exception exception) {
            emitFatal(runtime.observability(), "No se pudo iniciar Linker", exception);
            throw exception;
        }
    }

    static FeatureFlagProvider createFeatureFlagProvider(String launchDarklySdkKey) {
        if (launchDarklySdkKey != null && !launchDarklySdkKey.isBlank()) {
            LOG.info("[Info] Using LaunchDarkly feature flag provider");
            return LaunchDarklyFeatureFlagProvider.forSdkKey(launchDarklySdkKey);
        }
        LOG.warning("[Warn] LAUNCHDARKLY_SDK_KEY not set; using environment feature flag provider");
        return new EnvFeatureFlagProvider();
    }

    static Connection openDatabase(AppObservability observability) throws Exception {
        emitInfo(observability, "Opening database connection (MySQL strictly required)");
        try {
            com.linker5.persistence.DatabaseConfig databaseConfig = new com.linker5.persistence.DatabaseConfig();
            String url = databaseConfig.getConnectionString();
            String user = databaseConfig.getDatabaseUser();
            String password = databaseConfig.getDatabasePassword();

            if (url == null || !url.startsWith("jdbc:mysql:")) {
                throw new IllegalArgumentException("Invalid database URL. Strictly expecting a 'jdbc:mysql:' connection string.");
            }

            Class.forName("com.mysql.cj.jdbc.Driver");

            Connection connection = (user != null && !user.isBlank())
                    ? DriverManager.getConnection(url, user, password)
                    : DriverManager.getConnection(url);

            observability.setDatabaseConnected(true);
            return connection;
        } catch (Exception exception) {
            emitFatal(observability, "MySQL database connection could not be opened", exception);
            throw exception;
        }
    }

    static void initializeSchema(Connection database, LinkerUseCases linker, AppObservability observability) throws Exception {
        emitInfo(observability, "Initializing database schema");
        Span span = observability.startChildSpan("db.initialize_schema");
        long dbStart = System.nanoTime();
        try (Scope ignored = span.makeCurrent()) {
            linker.initializeSchema(database);
            observability.recordDatabaseOperation("CREATE_TABLE", elapsedMillis(dbStart));
        } catch (Exception exception) {
            observability.markError(span, exception);
            emitFatal(observability, "Database schema initialization failed", exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    static void configureLogging(Level level) {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(level);
        for (Handler handler : rootLogger.getHandlers()) {
            handler.setLevel(level);
        }
        LOG.setLevel(level);

        Logger.getLogger("sun.net.httpserver").setLevel(Level.INFO);
        Logger.getLogger("jdk.event.security").setLevel(Level.WARNING);
    }

    static long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static void emitInfo(AppObservability observability, String message) {
        String formatted = "[Info] " + message;
        LOG.info(formatted);
        observability.emitLog(Severity.INFO, formatted);
    }

    private static void emitFatal(AppObservability observability, String message, Exception exception) {
        String formatted = "[Fatal] " + message;
        LOG.log(Level.SEVERE, formatted, exception);
        observability.emitLog(Severity.FATAL, formatted + ": " + exception.getMessage());
    }
}
