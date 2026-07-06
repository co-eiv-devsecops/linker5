package com.linker5;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @AfterEach
    void clearDatabaseField() throws Exception {
        setMainDatabase(null);
    }

    @Test
    void shouldCreateShortLinkFromPostRequest() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            new LinkRepository().initializeSchema(connection);
            setMainDatabase(connection);

            FakeHttpExchange exchange = new FakeHttpExchange(
                    "POST",
                    "/link",
                    "{\"url\":\"https://example.com\"}",
                    "localhost:8080"
            );

            invokeHandle(exchange);

            assertEquals(201, exchange.statusCode);
            String body = exchange.responseAsText();
            assertTrue(body.contains("\"shortUrl\":\"http://localhost:8080/"));
            assertTrue(body.contains("\"id\":\""));
            assertEquals("application/json", exchange.getResponseHeaders().getFirst("Content-Type"));

            ResultSet resultSet = connection.createStatement().executeQuery("SELECT COUNT(*) FROM shorturl");
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
    }

    @Test
    void shouldRejectInvalidUrlInPostRequest() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            new LinkRepository().initializeSchema(connection);
            setMainDatabase(connection);

            FakeHttpExchange exchange = new FakeHttpExchange(
                    "POST",
                    "/link",
                    "{\"url\":\"/relative\"}",
                    "localhost:8080"
            );

            invokeHandle(exchange);

            assertEquals(400, exchange.statusCode);
            assertEquals("{\"error\":\"Invalid URL\"}", exchange.responseAsText());
            assertEquals("application/json", exchange.getResponseHeaders().getFirst("Content-Type"));
        }
    }

    @Test
    void shouldServeHtmlAndAssets() throws Exception {
        setMainDatabase(DriverManager.getConnection("jdbc:sqlite::memory:"));

        FakeHttpExchange root = new FakeHttpExchange("GET", "/", "", "localhost:8080");
        invokeHandle(root);
        assertEquals(200, root.statusCode);
        assertEquals("text/html", root.getResponseHeaders().getFirst("Content-Type"));
        assertTrue(root.responseAsText().contains("<!doctype html>") || root.responseAsText().contains("<html"));

        FakeHttpExchange css = new FakeHttpExchange("GET", "/css/style.css", "", "localhost:8080");
        invokeHandle(css);
        assertEquals(200, css.statusCode);
        assertEquals("text/css", css.getResponseHeaders().getFirst("Content-Type"));

        FakeHttpExchange js = new FakeHttpExchange("GET", "/js/app.js", "", "localhost:8080");
        invokeHandle(js);
        assertEquals(200, js.statusCode);
        assertEquals("application/javascript", js.getResponseHeaders().getFirst("Content-Type"));
    }

    @Test
    void shouldReturnNotFoundWhenStaticFileDoesNotExist() throws Exception {
        setMainDatabase(DriverManager.getConnection("jdbc:sqlite::memory:"));

        FakeHttpExchange exchange = new FakeHttpExchange("GET", "/css/missing.css", "", "localhost:8080");

        invokeHandle(exchange);

        assertEquals(404, exchange.statusCode);
        assertEquals("Not found", exchange.responseAsText());
        assertEquals("text/plain", exchange.getResponseHeaders().getFirst("Content-Type"));
    }

    @Test
    void shouldRedirectWhenShortLinkExists() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            LinkRepository repository = new LinkRepository();
            repository.initializeSchema(connection);
            repository.save(connection, "abc12345", "https://example.com/target");
            setMainDatabase(connection);

            FakeHttpExchange exchange = new FakeHttpExchange("GET", "/abc12345", "", "localhost:8080");

            invokeHandle(exchange);

            assertEquals(302, exchange.statusCode);
            assertEquals("https://example.com/target", exchange.getResponseHeaders().getFirst("Location"));
        }
    }

    @Test
    void shouldReturnNotFoundWhenShortLinkDoesNotExist() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            new LinkRepository().initializeSchema(connection);
            setMainDatabase(connection);

            FakeHttpExchange exchange = new FakeHttpExchange("GET", "/missing-id", "", "localhost:8080");

            invokeHandle(exchange);

            assertEquals(404, exchange.statusCode);
            assertEquals("Short URL not found", exchange.responseAsText());
            assertEquals("text/plain", exchange.getResponseHeaders().getFirst("Content-Type"));
        }
    }

    @Test
    void shouldReturnServerErrorWhenUnhandledExceptionOccurs() throws Exception {
        setMainDatabase(null);

        FakeHttpExchange exchange = new FakeHttpExchange(
                "POST",
                "/link",
                "{\"url\":\"https://example.com\"}",
                "localhost:8080"
        );

        invokeHandle(exchange);

        assertEquals(500, exchange.statusCode);
        assertEquals("{\"error\":\"Server error\"}", exchange.responseAsText());
        assertEquals("application/json", exchange.getResponseHeaders().getFirst("Content-Type"));
    }

    @Test
    void shouldOpenDatabaseAndInitializeSchemaUsingPrivateMethods() throws Exception {
        Connection connection = (Connection) invokePrivateStatic("openDatabase");
        assertNotNull(connection);

        try (connection) {
            invokePrivateStatic("initializeSchema", Connection.class, connection);
            String id = UUID.randomUUID().toString().substring(0, 8);
            connection.createStatement().execute("INSERT INTO shorturl(id, url) VALUES('" + id + "', 'https://example.com')");

            ResultSet resultSet = connection.createStatement().executeQuery("SELECT url FROM shorturl WHERE id='" + id + "'");
            assertTrue(resultSet.next());
            assertEquals("https://example.com", resultSet.getString("url"));
        }
    }

    private static void invokeHandle(FakeHttpExchange exchange) throws Exception {
        invokePrivateStatic("handle", HttpExchange.class, exchange);
    }

    private static Object invokePrivateStatic(String methodName) throws Exception {
        Method method = Main.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        return method.invoke(null);
    }

    private static Object invokePrivateStatic(String methodName, Class<?> parameterType, Object argument) throws Exception {
        Method method = Main.class.getDeclaredMethod(methodName, parameterType);
        method.setAccessible(true);
        return method.invoke(null, argument);
    }

    private static void setMainDatabase(Connection connection) throws Exception {
        Field dbField = Main.class.getDeclaredField("db");
        dbField.setAccessible(true);
        dbField.set(null, connection);
    }

    private static final class FakeHttpExchange extends HttpExchange {

        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final URI requestUri;
        private final String requestMethod;
        private final InputStream requestBody;
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();

        private int statusCode;
        private long responseLength;

        private FakeHttpExchange(String requestMethod, String path, String body, String host) {
            this.requestMethod = requestMethod;
            this.requestUri = URI.create(path);
            this.requestBody = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
            this.requestHeaders.add("Host", host);
        }

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI() {
            return requestUri;
        }

        @Override
        public String getRequestMethod() {
            return requestMethod;
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
            try {
                requestBody.close();
                responseBody.close();
            } catch (IOException ignored) {
                // No-op for test double cleanup.
            }
        }

        @Override
        public InputStream getRequestBody() {
            return requestBody;
        }

        @Override
        public OutputStream getResponseBody() {
            return responseBody;
        }

        @Override
        public void sendResponseHeaders(int statusCode, long responseLength) {
            this.statusCode = statusCode;
            this.responseLength = responseLength;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public int getResponseCode() {
            return statusCode;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value) {
            // Not required for these tests.
        }

        @Override
        public void setStreams(InputStream i, OutputStream o) {
            // Not required for these tests.
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }

        private String responseAsText() {
            return responseBody.toString(StandardCharsets.UTF_8);
        }
    }
}
