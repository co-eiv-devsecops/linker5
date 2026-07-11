package com.linker5.http;

import com.google.gson.Gson;
import com.linker5.app.LinkService;
import com.linker5.app.Linker;
import com.linker5.config.RuntimeConfig;
import com.linker5.flags.EnvFeatureFlagProvider;
import com.linker5.flags.FeatureFlagProvider;
import com.linker5.flags.LaunchDarklyFeatureFlagProvider;
import com.linker5.ids.UuidShortIdGenerator;
import com.linker5.observability.AppObservability;
import com.linker5.observability.Observability;
import com.linker5.observability.OpenTelemetryAppObservability;
import com.linker5.persistence.DatabaseConfig;
import com.linker5.persistence.LinkRepository;
import com.linker5.redirect.RedirectHandler;
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

    private static final Gson GSON = new Gson();
    private static final DatabaseConfig DATABASE_CONFIG = new DatabaseConfig();
    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) throws Exception {
        RuntimeConfig config = RuntimeConfig.load();
        configureLogging(config.logLevel());
        Observability.initialize(config);
        AppObservability observability = new OpenTelemetryAppObservability(Observability.get());
        Runtime.getRuntime().addShutdownHook(new Thread(observability::close));

        try {
            Connection db = openDatabase(observability);
            FeatureFlagProvider featureFlagProvider = createFeatureFlagProvider(System.getenv("LAUNCHDARKLY_SDK_KEY"));
            LinkRepository repository = new LinkRepository();
            Linker linker = new Linker(
                    new LinkService(GSON, repository, new UuidShortIdGenerator()),
                    new RedirectHandler(repository, featureFlagProvider),
                    repository
            );
            initializeSchema(db, linker, observability);

            HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
            server.createContext("/", new LinkerHttpHandler(linker, db, observability, GSON, LOG));
            server.start();

            emitInfo(observability, "Linker escuchando en el puerto " + config.port());
            emitInfo(observability, "OpenTelemetry inicializado para el servicio " + config.serviceName());
        } catch (Exception exception) {
            emitFatal(observability, "No se pudo iniciar Linker", exception);
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
            String url = DATABASE_CONFIG.getConnectionString();
            String user = DATABASE_CONFIG.getDatabaseUser();
            String password = DATABASE_CONFIG.getDatabasePassword();

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

    static void initializeSchema(Connection database, Linker linker, AppObservability observability) throws Exception {
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
