package com.linker5;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Scope;
import java.util.Optional;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Handler;
import java.util.logging.Logger;

public class Main {

    private static final Gson GSON = new Gson();
    private static final Linker LINKER = new Linker();
    private static final Logger LOG = Logger.getLogger(Main.class.getName());
    private static Connection db;
    private static RedirectHandler redirectHandler;

    public static void main(String[] args) throws Exception {
        RuntimeConfig config = RuntimeConfig.load();
        configureLogging(config.logLevel());
        Observability.initialize(config);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> Observability.get().close()));

        try {
            db = openDatabase();
            initializeSchema(db);

            FeatureFlagProvider featureFlagProvider = createFeatureFlagProvider(System.getenv("LAUNCHDARKLY_SDK_KEY"));
            redirectHandler = new RedirectHandler(new LinkRepository(), featureFlagProvider);

            HttpServer server = HttpServer.create(new InetSocketAddress(config.port()), 0);
            server.createContext("/", Main::handle);
            server.start();
            logInfo("Linker escuchando en el puerto " + config.port());
            logInfo("OpenTelemetry inicializado para el servicio " + config.serviceName());
        } catch (Exception exception) {
            logFatal("No se pudo iniciar Linker", exception);
            throw exception;
        }
    }

    // Choose the feature flag backend based on configuration: LaunchDarkly when an
    // SDK key is present, otherwise the environment-variable provider. This keeps the
    // app deployable without LaunchDarkly configured instead of crashing at startup.
    static FeatureFlagProvider createFeatureFlagProvider(String launchDarklySdkKey) {
        if (launchDarklySdkKey != null && !launchDarklySdkKey.isBlank()) {
            LOG.info("[Info] Using LaunchDarkly feature flag provider");
            return new LaunchDarklyFeatureFlagProvider();
        }
        LOG.warning("[Warn] LAUNCHDARKLY_SDK_KEY not set; using environment feature flag provider");
        return new EnvFeatureFlagProvider();
    }

    private static void handle(HttpExchange ex) throws IOException {
        long start = System.nanoTime();
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        String route = resolveRoute(path, method);
        int status = 500;
        Span requestSpan = Observability.get().startServerSpan(method, path);

        try (Scope ignored = requestSpan.makeCurrent()) {
            Observability.get().recordRequestStarted(method, route);
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
                status = serveStatic(ex, path.substring(1));
                return;
            }
            if (path.equals("/healthz")) {
                status = healthz(ex);
                return;
            }
            status = redirect(ex, path.substring(1));
        } catch (Exception exception) {
            Observability.get().markError(requestSpan, exception);
            logError("Unhandled error for " + method + " " + path, exception);
            status = 500;
            send(ex, 500, "{\"error\":\"Server error\"}", "application/json");
        } finally {
            requestSpan.setAttribute("http.response.status_code", status);
            requestSpan.end();
            Observability.get().recordRequestCompleted(method, route, status, elapsedMillis(start));
        }
    }

    private static int create(HttpExchange ex) throws Exception {
        Span span = Observability.get().startChildSpan("http.create_short_link");
        try (Scope ignored = span.makeCurrent()) {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Observability.get().recordRequestPayloadSize(body.getBytes(StandardCharsets.UTF_8).length);
            logDebug("Create short URL request body received");

            String url = GSON.fromJson(body, JsonObject.class).get("url").getAsString();
            if (!URI.create(url).isAbsolute()) {
                logWarn("Invalid URL received: " + url);
                send(ex, 400, "{\"error\":\"Invalid URL\"}", "application/json");
                return 400;
            }

            Span persistSpan = Observability.get().startChildSpan("db.insert_short_url");
            String id;
            try (Scope persistScope = persistSpan.makeCurrent()) {
                id = UUID.randomUUID().toString().substring(0, 8);
                long dbStart = System.nanoTime();
                PreparedStatement st = db.prepareStatement("INSERT INTO shorturl(id,url) VALUES(?,?)");
                st.setString(1, id);
                st.setString(2, url);
                st.executeUpdate();
                Observability.get().recordDatabaseOperation("INSERT", elapsedMillis(dbStart));
            } catch (Exception exception) {
                Observability.get().markError(persistSpan, exception);
                throw exception;
            } finally {
                persistSpan.end();
            }

            Observability.get().recordShortLinkCreated();
            span.setAttribute("linker.short_id", id);
            logInfo("Short URL created: id=" + id + " target=" + url);
            String host = ex.getRequestHeaders().getFirst("Host");
            send(ex, 201, String.format("{\"id\":\"%s\",\"shortUrl\":\"http://%s/%s\"}", id, host, id), "application/json");
            return 201;
        } catch (Exception exception) {
            Observability.get().markError(span, exception);
            logError("Short link creation failed", exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    private static int healthz(HttpExchange ex) throws IOException {
        Span span = Observability.get().startChildSpan("healthcheck");
        try (Scope ignored = span.makeCurrent()) {
            long dbStart = System.nanoTime();
            PreparedStatement st = db.prepareStatement("SELECT 1");
            ResultSet rs = st.executeQuery();
            boolean ok = rs.next();
            Observability.get().recordDatabaseOperation("SELECT", elapsedMillis(dbStart));
            span.setAttribute("linker.healthcheck.result", ok ? "ok" : "fail");

            if (ok) {
                send(ex, 200, "{\"status\":\"ok\"}", "application/json");
                return 200;
            }
            send(ex, 500, "{\"status\":\"fail\"}", "application/json");
            return 500;
        } catch (Exception exception) {
            Observability.get().markError(span, exception);
            logError("Healthcheck failed", exception);
            send(ex, 500, "{\"status\":\"fail\"}", "application/json");
            return 500;
        } finally {
            span.end();
        }
    }

    private static int redirect(HttpExchange ex, String id) throws Exception {
        Span span = Observability.get().startChildSpan("http.redirect_short_link");
        try (Scope ignored = span.makeCurrent()) {
            logDebug("Redirect lookup requested for id=" + id);
            Span lookupSpan = Observability.get().startChildSpan("db.select_short_url");
            Optional<String> redirectUrl;
            try (Scope lookupScope = lookupSpan.makeCurrent()) {
                long dbStart = System.nanoTime();
                redirectUrl = redirectHandler.resolveRedirect(id, db);
                Observability.get().recordDatabaseOperation("SELECT", elapsedMillis(dbStart));
            } catch (Exception exception) {
                Observability.get().markError(lookupSpan, exception);
                throw exception;
            } finally {
                lookupSpan.end();
            }

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
            Observability.get().markError(span, exception);
            logError("Redirect lookup failed for id=" + id, exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    private static int serveStatic(HttpExchange ex, String file) throws IOException {
        logDebug("Serving static file: " + file);
        try (InputStream in = Main.class.getResourceAsStream("/wwwroot/" + file)) {
            if (in == null) {
                logWarn("Static file not found: " + file);
                send(ex, 404, "Not found", "text/plain");
                return 404;
            }
            byte[] bytes = in.readAllBytes();
            String type = file.endsWith(".css") ? "text/css" : file.endsWith(".js") ? "application/javascript" : "text/html";
            logDebug("Static file served with content type " + type + ": " + file);
            send(ex, 200, bytes, type);
            return 200;
        }
    }

    private static void send(HttpExchange ex, int status, String body, String type) throws IOException {
        send(ex, status, body.getBytes(StandardCharsets.UTF_8), type);
    }

    private static void send(HttpExchange ex, int status, byte[] bytes, String type) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        Observability.get().recordResponseSize(bytes.length);
        ex.close();
    }

    private static Connection openDatabase() throws Exception {
        logInfo("Opening database connection (MySQL strictly required)");
        try {
            String url = LINKER.getDatabaseConnectionString();
            String user = LINKER.getDatabaseUser();
            String password = LINKER.getDatabasePassword();

            if (url == null || !url.startsWith("jdbc:mysql:")) {
                throw new IllegalArgumentException("Invalid database URL. Strictly expecting a 'jdbc:mysql:' connection string.");
            }

            Class.forName("com.mysql.cj.jdbc.Driver");

            Connection connection = (user != null && !user.isBlank())
                    ? DriverManager.getConnection(url, user, password)
                    : DriverManager.getConnection(url);

            Observability.get().setDatabaseConnected(true);
            return connection;
        } catch (Exception exception) {
            logFatal("MySQL database connection could not be opened", exception);
            throw exception;
        }
    }

    private static void initializeSchema(Connection database) throws Exception {
        logInfo("Initializing database schema");
        Span span = Observability.get().startChildSpan("db.initialize_schema");
        long dbStart = System.nanoTime();
        try (Scope ignored = span.makeCurrent()) {
            database.createStatement().execute("CREATE TABLE IF NOT EXISTS shorturl(id VARCHAR(64) PRIMARY KEY, url TEXT NOT NULL)");
            Observability.get().recordDatabaseOperation("CREATE_TABLE", elapsedMillis(dbStart));
        } catch (Exception exception) {
            Observability.get().markError(span, exception);
            logFatal("Database schema initialization failed", exception);
            throw exception;
        } finally {
            span.end();
        }
    }

    private static void configureLogging(Level level) {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(level);
        for (Handler handler : rootLogger.getHandlers()) {
            handler.setLevel(level);
        }
        LOG.setLevel(level);

        Logger.getLogger("sun.net.httpserver").setLevel(Level.INFO);
        Logger.getLogger("jdk.event.security").setLevel(Level.WARNING);
    }

    private static String resolveRoute(String path, String method) {
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
        return "redirect-short-link";
    }

    private static long elapsedMillis(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static void logInfo(String message) {
        String formatted = "[Info] " + message;
        LOG.info(formatted);
        Observability.get().emitLog(Severity.INFO, formatted);
    }

    private static void logWarn(String message) {
        String formatted = "[Warn] " + message;
        LOG.warning(formatted);
        Observability.get().emitLog(Severity.WARN, formatted);
    }

    private static void logDebug(String message) {
        String formatted = "[Debug] " + message;
        LOG.fine(formatted);
        Observability.get().emitLog(Severity.DEBUG, formatted);
    }

    private static void logError(String message, Exception exception) {
        String formatted = "[Error] " + message;
        LOG.log(Level.SEVERE, formatted, exception);
        Observability.get().emitLog(Severity.ERROR, formatted + ": " + exception.getMessage());
    }

    private static void logFatal(String message, Exception exception) {
        String formatted = "[Fatal] " + message;
        LOG.log(Level.SEVERE, formatted, exception);
        Observability.get().emitLog(Severity.FATAL, formatted + ": " + exception.getMessage());
    }
}
