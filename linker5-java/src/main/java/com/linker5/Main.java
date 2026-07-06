package com.linker5;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

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
import java.util.logging.Logger;

public class Main {

    private static final Gson GSON = new Gson();
    private static final Linker LINKER = new Linker();
    private static final Logger LOG = Logger.getLogger(Main.class.getName());
    private static Connection db;

    public static void main(String[] args) throws Exception {
        db = openDatabase();
        initializeSchema(db);
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", Main::handle);
        server.start();
        LOG.info("Linker escuchando en el puerto " + port);
    }

    private static void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String method = ex.getRequestMethod();
        LOG.info("Incoming request: " + method + " " + path);
        try {
            if (path.equals("/link") && method.equalsIgnoreCase("POST")) { create(ex); return; }
            if (path.equals("/")) { serveStatic(ex, "index.html"); return; }
            if (path.startsWith("/css/") || path.startsWith("/js/")) { serveStatic(ex, path.substring(1)); return; }
            redirect(ex, path.substring(1));
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unhandled error for " + method + " " + path, e);
            send(ex, 500, "{\"error\":\"Server error\"}", "application/json");
        }
    }

    private static void create(HttpExchange ex) throws Exception {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String url = GSON.fromJson(body, JsonObject.class).get("url").getAsString();
        LOG.info("Create short URL request received");
        if (!URI.create(url).isAbsolute()) {
            LOG.warning("Invalid URL received: " + url);
            send(ex, 400, "{\"error\":\"Invalid URL\"}", "application/json");
            return;
        }

        String id = UUID.randomUUID().toString().substring(0, 8);
        PreparedStatement st = db.prepareStatement("INSERT INTO shorturl(id,url) VALUES(?,?)");
        st.setString(1, id);
        st.setString(2, url);
        st.executeUpdate();
        LOG.info("Short URL created: id=" + id + " target=" + url);

        String host = ex.getRequestHeaders().getFirst("Host");
        send(ex, 201, String.format("{\"id\":\"%s\",\"shortUrl\":\"http://%s/%s\"}", id, host, id), "application/json");
    }

    private static void redirect(HttpExchange ex, String id) throws Exception {
        LOG.info("Redirect lookup for id=" + id);
        PreparedStatement st = db.prepareStatement("SELECT url FROM shorturl WHERE id=?");
        st.setString(1, id);
        ResultSet rs = st.executeQuery();
        if (rs.next()) {
            String target = rs.getString("url");
            LOG.info("Redirect success: id=" + id + " -> " + target);
            ex.getResponseHeaders().add("Location", target);
            ex.sendResponseHeaders(302, -1);
            ex.close();
        } else {
            LOG.warning("Redirect not found for id=" + id);
            send(ex, 404, "Short URL not found", "text/plain");
        }
    }

    private static void serveStatic(HttpExchange ex, String file) throws IOException {
        LOG.fine("Serving static file: " + file);
        try (InputStream in = Main.class.getResourceAsStream("/wwwroot/" + file)) {
            if (in == null) {
                LOG.warning("Static file not found: " + file);
                send(ex, 404, "Not found", "text/plain");
                return;
            }
            byte[] bytes = in.readAllBytes();
            String type = file.endsWith(".css") ? "text/css" : file.endsWith(".js") ? "application/javascript" : "text/html";
            send(ex, 200, bytes, type);
        }
    }

    private static void send(HttpExchange ex, int status, String body, String type) throws IOException {
        send(ex, status, body.getBytes(StandardCharsets.UTF_8), type);
    }

    private static void send(HttpExchange ex, int status, byte[] bytes, String type) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    private static Connection openDatabase() throws Exception {
        LOG.info("Opening database connection");
        return DriverManager.getConnection(LINKER.getDatabaseConnectionString());
    }

    private static void initializeSchema(Connection database) throws Exception {
        LOG.info("Initializing database schema");
        database.createStatement().execute("CREATE TABLE IF NOT EXISTS shorturl(id TEXT PRIMARY KEY, url TEXT NOT NULL)");
    }
}
