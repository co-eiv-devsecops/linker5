package com.linker5.http;

import com.google.gson.Gson;
import com.linker5.app.LinkService;
import com.linker5.app.Linker;
import com.linker5.flags.EnvFeatureFlagProvider;
import com.linker5.observability.NoopAppObservability;
import com.linker5.persistence.LinkRepository;
import com.linker5.redirect.RedirectHandler;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkerHttpHandlerTest {

    private static final String IN_MEMORY_SQLITE_URL = "jdbc:sqlite::memory:";
    private static final String CREATE_LINK_PATH = "/link";
    private static final String LOCALHOST_HOST = "localhost:8080";
    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    @Test
    void shouldCreateShortLinkFromPostRequest() throws Exception {
        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            LinkRepository repository = new LinkRepository();
            repository.initializeSchema(connection);
            LinkerHttpHandler handler = newHandler(connection, defaultLinker(repository));

            FakeHttpExchange exchange = new FakeHttpExchange("POST", CREATE_LINK_PATH, "{\"url\":\"https://example.com\"}", LOCALHOST_HOST);
            handler.handle(exchange);

            assertEquals(201, exchange.statusCode);
            String body = exchange.responseAsText();
            assertTrue(body.contains("\"shortUrl\":\"http://" + LOCALHOST_HOST + "/"));
            assertTrue(body.contains("\"id\":"));
            assertEquals("application/json", exchange.getResponseHeaders().getFirst(CONTENT_TYPE_HEADER));

            ResultSet resultSet = connection.createStatement().executeQuery("SELECT COUNT(*) FROM shorturl");
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
    }

    @Test
    void shouldRejectInvalidUrlInPostRequest() throws Exception {
        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            LinkRepository repository = new LinkRepository();
            repository.initializeSchema(connection);
            LinkerHttpHandler handler = newHandler(connection, defaultLinker(repository));

            FakeHttpExchange exchange = new FakeHttpExchange("POST", CREATE_LINK_PATH, "{\"url\":\"/relative\"}", LOCALHOST_HOST);
            handler.handle(exchange);

            assertEquals(400, exchange.statusCode);
            assertEquals("{\"error\":\"Invalid URL\"}", exchange.responseAsText());
        }
    }

    @Test
    void shouldRejectPostRequestMissingUrlField() throws Exception {
        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            LinkRepository repository = new LinkRepository();
            repository.initializeSchema(connection);
            LinkerHttpHandler handler = newHandler(connection, defaultLinker(repository));

            FakeHttpExchange exchange = new FakeHttpExchange("POST", CREATE_LINK_PATH, "{}", LOCALHOST_HOST);
            handler.handle(exchange);

            assertEquals(400, exchange.statusCode);
            assertEquals("{\"error\":\"Missing 'url'\"}", exchange.responseAsText());
            assertEquals("application/json", exchange.getResponseHeaders().getFirst(CONTENT_TYPE_HEADER));
        }
    }

    @Test
    void shouldServeHtmlAndAssets() throws Exception {
        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            LinkerHttpHandler handler = newHandler(connection, defaultLinker(new LinkRepository()));

            FakeHttpExchange root = new FakeHttpExchange("GET", "/", "", LOCALHOST_HOST);
            handler.handle(root);
            assertEquals(200, root.statusCode);
            assertEquals("text/html", root.getResponseHeaders().getFirst(CONTENT_TYPE_HEADER));

            FakeHttpExchange css = new FakeHttpExchange("GET", "/css/style.css", "", LOCALHOST_HOST);
            handler.handle(css);
            assertEquals(200, css.statusCode);
            assertEquals("text/css", css.getResponseHeaders().getFirst(CONTENT_TYPE_HEADER));

            FakeHttpExchange js = new FakeHttpExchange("GET", "/js/app.js", "", LOCALHOST_HOST);
            handler.handle(js);
            assertEquals(200, js.statusCode);
            assertEquals("application/javascript", js.getResponseHeaders().getFirst(CONTENT_TYPE_HEADER));
        }
    }

    @Test
    void shouldReturnNotFoundWhenStaticFileDoesNotExist() throws Exception {
        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            LinkerHttpHandler handler = newHandler(connection, defaultLinker(new LinkRepository()));
            FakeHttpExchange exchange = new FakeHttpExchange("GET", "/css/missing.css", "", LOCALHOST_HOST);

            handler.handle(exchange);

            assertEquals(404, exchange.statusCode);
            assertEquals("Not found", exchange.responseAsText());
        }
    }

    @Test
    void shouldRejectStaticFileRequestsAttemptingPathTraversal() throws Exception {
        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            LinkerHttpHandler handler = newHandler(connection, defaultLinker(new LinkRepository()));
            FakeHttpExchange exchange = new FakeHttpExchange("GET", "/css/../../pom.xml", "", LOCALHOST_HOST);

            handler.handle(exchange);

            assertEquals(404, exchange.statusCode);
            assertEquals("Not found", exchange.responseAsText());
        }
    }

    @Test
    void shouldRejectStaticFileRequestsWithNestedPaths() {
        assertTrue(LinkerHttpHandler.isSafeStaticAssetPath("css/style.css"));
        assertTrue(LinkerHttpHandler.isSafeStaticAssetPath("js/app.js"));
        assertEquals(false, LinkerHttpHandler.isSafeStaticAssetPath("css/../../pom.xml"));
        assertEquals(false, LinkerHttpHandler.isSafeStaticAssetPath("css/sub/style.css"));
        assertEquals(false, LinkerHttpHandler.isSafeStaticAssetPath("img/logo.png"));
        assertEquals(false, LinkerHttpHandler.isSafeStaticAssetPath("css/"));
        assertEquals(false, LinkerHttpHandler.isSafeStaticAssetPath("css"));
    }

    @Test
    void shouldRedirectWhenShortLinkExists() throws Exception {
        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            LinkRepository repository = new LinkRepository();
            repository.initializeSchema(connection);
            repository.save(connection, "abc12345", "https://example.com/target");
            Linker linker = new Linker(new LinkService(), new RedirectHandler(repository, flagName -> true), repository);
            LinkerHttpHandler handler = newHandler(connection, linker);

            FakeHttpExchange exchange = new FakeHttpExchange("GET", "/abc12345", "", LOCALHOST_HOST);
            handler.handle(exchange);

            assertEquals(302, exchange.statusCode);
            assertEquals("https://example.com/target", exchange.getResponseHeaders().getFirst("Location"));
        }
    }

    @Test
    void shouldReturnNotFoundWhenShortLinkDoesNotExist() throws Exception {
        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            LinkRepository repository = new LinkRepository();
            repository.initializeSchema(connection);
            LinkerHttpHandler handler = newHandler(connection, defaultLinker(repository));

            FakeHttpExchange exchange = new FakeHttpExchange("GET", "/missing-id", "", LOCALHOST_HOST);
            handler.handle(exchange);

            assertEquals(404, exchange.statusCode);
            assertEquals("Short URL not found", exchange.responseAsText());
        }
    }

    @Test
    void shouldReturnMetadataAsBodyWhenShortLinkExistsOnHeadRequest() throws Exception {
        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            LinkRepository repository = new LinkRepository();
            repository.initializeSchema(connection);
            repository.save(connection, "abc12345", "https://example.com/target");
            LinkerHttpHandler handler = newHandler(connection, defaultLinker(repository));

            FakeHttpExchange exchange = new FakeHttpExchange("HEAD", "/abc12345", "", LOCALHOST_HOST);
            handler.handle(exchange);

            assertEquals(200, exchange.statusCode);
            assertEquals("https://example.com/target", exchange.responseAsText());
            assertEquals("text/plain", exchange.getResponseHeaders().getFirst(CONTENT_TYPE_HEADER));
        }
    }

    @Test
    void shouldReturnNotFoundOnHeadRequestWhenShortLinkDoesNotExist() throws Exception {
        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            LinkRepository repository = new LinkRepository();
            repository.initializeSchema(connection);
            LinkerHttpHandler handler = newHandler(connection, defaultLinker(repository));

            FakeHttpExchange exchange = new FakeHttpExchange("HEAD", "/missing-id", "", LOCALHOST_HOST);
            handler.handle(exchange);

            assertEquals(404, exchange.statusCode);
            assertEquals("Short URL not found", exchange.responseAsText());
        }
    }

    @Test
    void shouldDeleteShortLinkAndReturnNoContent() throws Exception {
        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            LinkRepository repository = new LinkRepository();
            repository.initializeSchema(connection);
            repository.save(connection, "abc12345", "https://example.com/target");
            LinkerHttpHandler handler = newHandler(connection, defaultLinker(repository));

            FakeHttpExchange exchange = new FakeHttpExchange("DELETE", "/abc12345", "", LOCALHOST_HOST);
            handler.handle(exchange);

            assertEquals(204, exchange.statusCode);
            assertEquals("", exchange.responseAsText());
            assertTrue(repository.findUrlById(connection, "abc12345").isEmpty());
        }
    }

    @Test
    void shouldReturnNotFoundWhenDeletingUnknownShortLink() throws Exception {
        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            LinkRepository repository = new LinkRepository();
            repository.initializeSchema(connection);
            LinkerHttpHandler handler = newHandler(connection, defaultLinker(repository));

            FakeHttpExchange exchange = new FakeHttpExchange("DELETE", "/missing-id", "", LOCALHOST_HOST);
            handler.handle(exchange);

            assertEquals(404, exchange.statusCode);
            assertEquals("Short URL not found", exchange.responseAsText());
        }
    }

    @Test
    void shouldReturnServerErrorWhenUnhandledExceptionOccurs() throws Exception {
        LinkerHttpHandler handler = newHandler(null, defaultLinker(new LinkRepository()));
        FakeHttpExchange exchange = new FakeHttpExchange("POST", CREATE_LINK_PATH, "{\"url\":\"https://example.com\"}", LOCALHOST_HOST);

        handler.handle(exchange);

        assertEquals(500, exchange.statusCode);
        assertEquals("{\"error\":\"Server error\"}", exchange.responseAsText());
    }

    @Test
    void shouldResolveRoutesAndExposeElapsedMillisHelper() {
        assertEquals("create-short-link", LinkerHttpHandler.resolveRoute(CREATE_LINK_PATH, "POST"));
        assertEquals("root-ui", LinkerHttpHandler.resolveRoute("/", "GET"));
        assertEquals("static-asset", LinkerHttpHandler.resolveRoute("/css/style.css", "GET"));
        assertEquals("redirect-short-link", LinkerHttpHandler.resolveRoute("/abc12345", "GET"));
        assertEquals("short-link-metadata", LinkerHttpHandler.resolveRoute("/abc12345", "HEAD"));
        assertEquals("delete-short-link", LinkerHttpHandler.resolveRoute("/abc12345", "DELETE"));
        assertTrue(LinkerHttpHandler.elapsedMillis(System.nanoTime() - 2_000_000) >= 0);
    }

    private static LinkerHttpHandler newHandler(Connection connection, Linker linker) {
        return new LinkerHttpHandler(linker, connection, new NoopAppObservability(), new Gson(), Logger.getLogger("LinkerHttpHandlerTest"));
    }

    private static Linker defaultLinker(LinkRepository repository) {
        return new Linker(new LinkService(), new RedirectHandler(repository, new EnvFeatureFlagProvider()), repository);
    }

    private static final class FakeHttpExchange extends HttpExchange {
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final URI requestUri;
        private final String requestMethod;
        private final InputStream requestBody;
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private int statusCode;

        private FakeHttpExchange(String requestMethod, String path, String body, String host) {
            this.requestMethod = requestMethod;
            this.requestUri = URI.create(path);
            this.requestBody = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
            this.requestHeaders.add("Host", host);
        }

        @Override public Headers getRequestHeaders() { return requestHeaders; }
        @Override public Headers getResponseHeaders() { return responseHeaders; }
        @Override public URI getRequestURI() { return requestUri; }
        @Override public String getRequestMethod() { return requestMethod; }
        @Override public HttpContext getHttpContext() { return null; }
        @Override public void close() {
            try {
                requestBody.close();
                responseBody.close();
            } catch (IOException ignored) {
                // No-op for test double cleanup.
            }
        }
        @Override public InputStream getRequestBody() { return requestBody; }
        @Override public OutputStream getResponseBody() { return responseBody; }
        @Override public void sendResponseHeaders(int statusCode, long responseLength) { this.statusCode = statusCode; }
        @Override public InetSocketAddress getRemoteAddress() { return null; }
        @Override public int getResponseCode() { return statusCode; }
        @Override public InetSocketAddress getLocalAddress() { return null; }
        @Override public String getProtocol() { return "HTTP/1.1"; }
        @Override public Object getAttribute(String name) { return null; }
        @Override public void setAttribute(String name, Object value) {
            // Not required for these tests.
        }

        @Override public void setStreams(InputStream i, OutputStream o) {
            // Not required for these tests.
        }
        @Override public HttpPrincipal getPrincipal() { return null; }

        String responseAsText() {
            return responseBody.toString(StandardCharsets.UTF_8);
        }
    }
}
