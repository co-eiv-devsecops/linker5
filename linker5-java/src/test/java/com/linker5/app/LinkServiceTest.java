package com.linker5.app;

import com.google.gson.Gson;
import com.linker5.persistence.LinkRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkServiceTest {

    private static final String GENERATED_SHORT_LINK_ID = "abcd1234";
    private static final String GENERATED_ALIAS_FALLBACK_ID = "generated-id";
    private static final String IN_MEMORY_SQLITE_URL = "jdbc:sqlite::memory:";
    private static final String LOCALHOST_HOST = "localhost:8080";

    private final LinkRepository repository = new LinkRepository();

    @Nested
    @DisplayName("Cuando el short link id está vacío o en blanco")
    class WhenShortLinkIdIsBlank {

        @ParameterizedTest(name = "When_ShortLinkIdIsBlank_Expect_IllegalArgumentException [{index}]")
        @ValueSource(strings = {"", " ", "   ", "\t"})
        void shouldRejectBlankShortLinkId(String shortLinkId) throws Exception {

            LinkService service = new LinkService(new Gson(), repository, () -> GENERATED_SHORT_LINK_ID);

            try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
                repository.initializeSchema(connection);

                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                        service.resolveShortLink(shortLinkId, connection));

                assertEquals("Invalid short link id", exception.getMessage());
            }
        }
    }

    @Nested
    @DisplayName("Cuando el id autogenerado ya existe")
    class WhenGeneratedShortLinkIdAlreadyExists {

        @ParameterizedTest(name = "When_GeneratedShortLinkIdAlreadyExists_Expect_ClearFailure [{index}]")
        @ValueSource(strings = {"https://example.com", "https://escuelaing.edu.co"})
        void shouldFailWhenGeneratedShortLinkIdAlreadyExists(String url) throws Exception {
            // Arrange
            LinkService service = new LinkService(new Gson(), repository, () -> GENERATED_SHORT_LINK_ID);

            try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
                repository.initializeSchema(connection);
                repository.save(connection, GENERATED_SHORT_LINK_ID, "https://existing-link.com");

                // Act
                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                        service.createShortLink("{\"url\":\"" + url + "\"}", LOCALHOST_HOST, connection));

                // Assert
                assertEquals("Short link id already exists", exception.getMessage());
            }
        }
    }

    @Test
    void shouldCreateShortLinkUsingTheProvidedDependencies() throws Exception {
        LinkService service = new LinkService(new Gson(), repository, () -> GENERATED_SHORT_LINK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            repository.initializeSchema(connection);

            CreateLinkResult result = service.createShortLink("{\"url\":\"https://example.com\"}", LOCALHOST_HOST, connection);

            assertEquals(GENERATED_SHORT_LINK_ID, result.id());
            assertEquals("http://" + LOCALHOST_HOST + "/" + GENERATED_SHORT_LINK_ID, result.shortUrl());
            assertEquals(Optional.of("https://example.com"), service.resolveShortLink(GENERATED_SHORT_LINK_ID, connection));
        }
    }

    @Test
    void shouldRejectRelativeUrls() throws Exception {
        LinkService service = new LinkService(new Gson(), repository, () -> GENERATED_SHORT_LINK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    service.createShortLink("{\"url\":\"/relative\"}", LOCALHOST_HOST, connection));

            assertEquals("Invalid URL", exception.getMessage());
        }
    }

    @ParameterizedTest(name = "When_UrlSchemeIsNotAllowed_Expect_IllegalArgumentException [{index}]")
    @ValueSource(strings = {
            "javascript:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "file:///etc/passwd",
            "ftp://example.com/resource"
    })
    void shouldRejectUrlsWithDisallowedSchemes(String url) throws Exception {
        LinkService service = new LinkService(new Gson(), repository, () -> GENERATED_SHORT_LINK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    service.createShortLink("{\"url\":\"" + url + "\"}", LOCALHOST_HOST, connection));

            assertEquals("Invalid URL", exception.getMessage());
        }
    }

    @Test
    void shouldRejectUrlsWithInvalidUriSyntax() throws Exception {
        LinkService service = new LinkService(new Gson(), repository, () -> GENERATED_SHORT_LINK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    service.createShortLink("{\"url\":\"http://example.com/a b\"}", LOCALHOST_HOST, connection));

            assertEquals("Invalid URL", exception.getMessage());
        }
    }

    @Test
    void shouldRejectRequestsMissingTheUrlField() throws Exception {
        LinkService service = new LinkService(new Gson(), repository, () -> GENERATED_SHORT_LINK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    service.createShortLink("{}", LOCALHOST_HOST, connection));

            assertEquals("Missing 'url'", exception.getMessage());
        }
    }

    @Test
    void shouldReturnEmptyWhenShortLinkDoesNotExist() throws Exception {
        LinkService service = new LinkService(new Gson(), repository, () -> GENERATED_SHORT_LINK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            repository.initializeSchema(connection);

            assertEquals(Optional.empty(), service.resolveShortLink("missing-id", connection));
        }
    }

    @Test
    void shouldRejectBlankShortLinkIds() throws Exception {
        LinkService service = new LinkService(new Gson(), repository, () -> GENERATED_SHORT_LINK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            repository.initializeSchema(connection);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    service.resolveShortLink("", connection));

            assertEquals("Invalid short link id", exception.getMessage());
        }
    }

    @Test
    void shouldCreateAndResolveShortLinkUsingAnAlias() throws Exception {
        LinkService service = new LinkService(new Gson(), repository, () -> GENERATED_ALIAS_FALLBACK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            repository.initializeSchema(connection);

            CreateLinkResult result = service.createShortLink(
                    "{\"url\":\"https://example.com\",\"alias\":\"docs\"}",
                    LOCALHOST_HOST,
                    connection
            );

            assertEquals("docs", result.id());
            assertEquals("http://" + LOCALHOST_HOST + "/docs", result.shortUrl());
            assertEquals(Optional.of("https://example.com"), service.resolveShortLink("docs", connection));
        }
    }

    @Test
    void shouldRejectDuplicateAliasesWhenCreatingShortLinks() throws Exception {
        LinkService service = new LinkService(new Gson(), repository, () -> GENERATED_ALIAS_FALLBACK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            repository.initializeSchema(connection);

            service.createShortLink(
                    "{\"url\":\"https://example.com\",\"alias\":\"docs\"}",
                    LOCALHOST_HOST,
                    connection
            );

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    service.createShortLink(
                            "{\"url\":\"https://another-example.com\",\"alias\":\"docs\"}",
                            LOCALHOST_HOST,
                            connection
                    ));

            assertEquals("Alias already exists", exception.getMessage());
        }
    }

    @Test
    void shouldTreatIntegrityConstraintViolationsAsDuplicateShortLinkIds() {
        LinkService service = new LinkService();

        assertTrue(service.isDuplicateShortLinkId(
                new SQLIntegrityConstraintViolationException("Duplicate entry '" + GENERATED_SHORT_LINK_ID + "' for key 'PRIMARY'")));
    }

    @Test
    void shouldRejectBlankAliasesWhenCreatingShortLinks() throws Exception {
        LinkService service = new LinkService(new Gson(), repository, () -> GENERATED_ALIAS_FALLBACK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            repository.initializeSchema(connection);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    service.createShortLink(
                            "{\"url\":\"https://example.com\",\"alias\":\"\"}",
                            LOCALHOST_HOST,
                            connection
                    ));

            assertEquals("Invalid alias", exception.getMessage());
        }
    }
}
