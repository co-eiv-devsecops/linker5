package com.linker5.app;

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
    private static final String LOCALHOST_PUBLIC_BASE_URL = "http://localhost:8080";
    private static final String EXAMPLE_URL = "https://example.com";
    private static final String INVALID_URL_MESSAGE = "Invalid URL";

    private final LinkRepository repository = new LinkRepository();

    @Nested
    @DisplayName("Cuando el short link id está vacío o en blanco")
    class WhenShortLinkIdIsBlank {

        @ParameterizedTest(name = "When_ShortLinkIdIsBlank_Expect_IllegalArgumentException [{index}]")
        @ValueSource(strings = {"", " ", "   ", "\t"})
        void shouldRejectBlankShortLinkId(String shortLinkId) throws Exception {
            LinkService service = new LinkService(repository, () -> GENERATED_SHORT_LINK_ID);

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
        @ValueSource(strings = {EXAMPLE_URL, "https://escuelaing.edu.co"})
        void shouldFailWhenGeneratedShortLinkIdAlreadyExists(String url) throws Exception {
            LinkService service = new LinkService(repository, () -> GENERATED_SHORT_LINK_ID);

            try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
                repository.initializeSchema(connection);
                repository.save(connection, GENERATED_SHORT_LINK_ID, "https://existing-link.com");

                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                        service.createShortLink(request(url), connection));

                assertEquals("Short link id already exists", exception.getMessage());
            }
        }
    }

    @Test
    void shouldCreateShortLinkUsingTheProvidedDependencies() throws Exception {
        LinkService service = new LinkService(repository, () -> GENERATED_SHORT_LINK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            repository.initializeSchema(connection);

            CreateLinkResult result = service.createShortLink(request(EXAMPLE_URL), connection);

            assertEquals(GENERATED_SHORT_LINK_ID, result.id());
            assertEquals(LOCALHOST_PUBLIC_BASE_URL + "/" + GENERATED_SHORT_LINK_ID, result.shortUrl());
            assertEquals(Optional.of(EXAMPLE_URL), service.resolveShortLink(GENERATED_SHORT_LINK_ID, connection));
        }
    }

    @Test
    void shouldRejectRelativeUrls() throws Exception {
        LinkService service = new LinkService(repository, () -> GENERATED_SHORT_LINK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    service.createShortLink(request("/relative"), connection));

            assertEquals(INVALID_URL_MESSAGE, exception.getMessage());
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
        LinkService service = new LinkService(repository, () -> GENERATED_SHORT_LINK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    service.createShortLink(request(url), connection));

            assertEquals(INVALID_URL_MESSAGE, exception.getMessage());
        }
    }

    @Test
    void shouldRejectUrlsWithInvalidUriSyntax() throws Exception {
        LinkService service = new LinkService(repository, () -> GENERATED_SHORT_LINK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    service.createShortLink(request("http://example.com/a b"), connection));

            assertEquals(INVALID_URL_MESSAGE, exception.getMessage());
        }
    }

    @Test
    void shouldRejectRequestsMissingTheUrlField() throws Exception {
        LinkService service = new LinkService(repository, () -> GENERATED_SHORT_LINK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    service.createShortLink(new CreateShortLinkRequest(null, null, LOCALHOST_PUBLIC_BASE_URL), connection));

            assertEquals("Missing 'url'", exception.getMessage());
        }
    }

    @Test
    void shouldReturnEmptyWhenShortLinkDoesNotExist() throws Exception {
        LinkService service = new LinkService(repository, () -> GENERATED_SHORT_LINK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            repository.initializeSchema(connection);

            assertEquals(Optional.empty(), service.resolveShortLink("missing-id", connection));
        }
    }

    @Test
    void shouldRejectBlankShortLinkIds() throws Exception {
        LinkService service = new LinkService(repository, () -> GENERATED_SHORT_LINK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            repository.initializeSchema(connection);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    service.resolveShortLink("", connection));

            assertEquals("Invalid short link id", exception.getMessage());
        }
    }

    @Test
    void shouldCreateAndResolveShortLinkUsingAnAlias() throws Exception {
        LinkService service = new LinkService(repository, () -> GENERATED_ALIAS_FALLBACK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            repository.initializeSchema(connection);

            CreateLinkResult result = service.createShortLink(request(EXAMPLE_URL, "docs"), connection);

            assertEquals("docs", result.id());
            assertEquals(LOCALHOST_PUBLIC_BASE_URL + "/docs", result.shortUrl());
            assertEquals(Optional.of(EXAMPLE_URL), service.resolveShortLink("docs", connection));
        }
    }

    @Test
    void shouldRejectDuplicateAliasesWhenCreatingShortLinks() throws Exception {
        LinkService service = new LinkService(repository, () -> GENERATED_ALIAS_FALLBACK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            repository.initializeSchema(connection);

            service.createShortLink(request(EXAMPLE_URL, "docs"), connection);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    service.createShortLink(request("https://another-example.com", "docs"), connection));

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
        LinkService service = new LinkService(repository, () -> GENERATED_ALIAS_FALLBACK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            repository.initializeSchema(connection);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    service.createShortLink(request(EXAMPLE_URL, ""), connection));

            assertEquals("Invalid alias", exception.getMessage());
        }
    }

    @Test
    void shouldRejectMissingPublicBaseUrl() throws Exception {
        LinkService service = new LinkService(repository, () -> GENERATED_SHORT_LINK_ID);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    service.createShortLink(new CreateShortLinkRequest(EXAMPLE_URL, null, ""), connection));

            assertEquals("Invalid public base URL", exception.getMessage());
        }
    }

    private static CreateShortLinkRequest request(String url) {
        return new CreateShortLinkRequest(url, null, LOCALHOST_PUBLIC_BASE_URL);
    }

    private static CreateShortLinkRequest request(String url, String alias) {
        return new CreateShortLinkRequest(url, alias, LOCALHOST_PUBLIC_BASE_URL);
    }
}
