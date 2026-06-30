package com.linker5;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.InetSocketAddress;
import java.net.URI;

import java.nio.charset.StandardCharsets;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.UUID;

public class Main {

    private static final String DB_URL = "jdbc:sqlite:linker.db";

    public static void main(String[] args) throws Exception {

        initializeDatabase();

        int port = Integer.parseInt(
                System.getenv().getOrDefault("PORT", "8080")
        );

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", Main::handleRequest);

        server.setExecutor(null);

        server.start();

        System.out.println("-----------------------------------");
        System.out.println("Linker iniciado");
        System.out.println("Puerto: " + port);
        System.out.println("-----------------------------------");
    }

    /**
     * Router principal
     */
    private static void handleRequest(HttpExchange exchange) throws IOException {

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        System.out.println(method + " " + path);

        if (path.equals("/")) {
            serveStatic(exchange, "index.html");
            return;
        }

        if (path.equals("/css/style.css")) {
            serveStatic(exchange, "css/style.css");
            return;
        }

        if (path.equals("/js/app.js")) {
            serveStatic(exchange, "js/app.js");
            return;
        }

        if (path.equals("/link") && method.equalsIgnoreCase("POST")) {
            handleCreateLink(exchange);
            return;
        }

        String id = path.substring(1);

        if (!id.isBlank()) {
            handleRedirect(exchange, id);
            return;
        }

        sendResponse(exchange,404,"Not Found","text/plain");
    }

    private static void handleCreateLink(HttpExchange exchange) throws IOException {

        String body = new String(
        exchange.getRequestBody().readAllBytes(),
        StandardCharsets.UTF_8
);

        Gson gson = new Gson();
        JsonObject json = gson.fromJson(body, JsonObject.class);

        String url = json.get("url").getAsString();

    if (!isValidUrl(url)) {
        sendResponse(exchange, 400,
                "{\"error\":\"Invalid URL\"}",
                "application/json");
        return;
    }

    String id = generateId();

    try (Connection connection = DriverManager.getConnection(DB_URL)) {

        PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO shorturl(id,url) VALUES(?,?)"
        );

        statement.setString(1, id);
        statement.setString(2, url);

        statement.executeUpdate();

    } catch (SQLException e) {

        e.printStackTrace();

        sendResponse(exchange,
                500,
                "{\"error\":\"Database Error\"}",
                "application/json");

        return;

    }

    String host = exchange.getRequestHeaders().getFirst("Host");

    String response = String.format("""
    {
        "id":"%s",
        "shortUrl":"http://%s/%s"
    }
    """, id, host, id);
        sendResponse(exchange, 201, response, "application/json");
    }

private static void handleRedirect(HttpExchange exchange, String id) throws IOException {

    try (Connection connection = DriverManager.getConnection(DB_URL)) {

        PreparedStatement statement = connection.prepareStatement(
                "SELECT url FROM shorturl WHERE id=?"
        );

        statement.setString(1, id);

        ResultSet result = statement.executeQuery();

        if (result.next()) {

            exchange.getResponseHeaders()
                    .add("Location", result.getString("url"));

            exchange.sendResponseHeaders(302, -1);

        } else {

            sendResponse(exchange,
                    404,
                    "Short URL not found",
                    "text/plain");

        }

    } catch (SQLException e) {

        e.printStackTrace();

        sendResponse(exchange,
                500,
                "Database Error",
                "text/plain");

    }
}

private static void serveStatic(HttpExchange exchange, String file) throws IOException {

    InputStream input = Main.class
            .getResourceAsStream("/wwwroot/" + file);

    if (input == null) {

        sendResponse(exchange,
                404,
                "File Not Found",
                "text/plain");

        return;
    }

    byte[] bytes = input.readAllBytes();

    String contentType = "text/plain";

    if (file.endsWith(".html"))
        contentType = "text/html";

    if (file.endsWith(".css"))
        contentType = "text/css";

    if (file.endsWith(".js"))
        contentType = "application/javascript";

    exchange.getResponseHeaders().set("Content-Type", contentType);

    exchange.sendResponseHeaders(200, bytes.length);

    OutputStream output = exchange.getResponseBody();

    output.write(bytes);

    output.close();

}

private static void sendResponse(HttpExchange exchange,
                                 int status,
                                 String body,
                                 String contentType) throws IOException {

    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

    exchange.getResponseHeaders()
            .set("Content-Type", contentType);

    exchange.sendResponseHeaders(status, bytes.length);

    OutputStream output = exchange.getResponseBody();

    output.write(bytes);

    output.close();

}

private static void initializeDatabase() {

    try (Connection connection = DriverManager.getConnection(DB_URL)) {

        Statement statement = connection.createStatement();

        statement.execute("""
                CREATE TABLE IF NOT EXISTS shorturl(
                    id TEXT PRIMARY KEY,
                    url TEXT NOT NULL
                )
                """);

    }

    catch (SQLException e) {

        e.printStackTrace();

    }

}

private static String generateId() {

    return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0,8);

}

private static boolean isValidUrl(String url) {

    try {

        URI uri = new URI(url);

        return uri.isAbsolute();

    }

    catch (Exception e) {

        return false;

    }

}}