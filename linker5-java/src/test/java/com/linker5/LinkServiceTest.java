package com.linker5;

import com.google.gson.Gson;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LinkServiceTest {

    private final LinkRepository repository = new LinkRepository();

    @Nested
    @DisplayName("Cuando el short link id está vacío o en blanco")
    class WhenShortLinkIdIsBlank {

        @ParameterizedTest(name = "When_ShortLinkIdIsBlank_Expect_IllegalArgumentException [{index}]")
        @ValueSource(strings = {"", " ", "   ", "\t"})
        void When_ShortLinkIdIsBlank_Expect_IllegalArgumentException(String shortLinkId) throws Exception {

            LinkService service = new LinkService(new Gson(), repository, () -> "abcd1234");

            try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
                repository.initializeSchema(connection);

                IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                        service.resolveShortLink(shortLinkId, connection));

                assertEquals("Invalid short link id", exception.getMessage());
            }
        }
    }

    @Test
    void shouldCreateShortLinkUsingTheProvidedDependencies() throws Exception {
        LinkService service = new LinkService(new Gson(), repository, () -> "abcd1234");

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            repository.initializeSchema(connection);

            CreateLinkResult result = service.createShortLink("{\"url\":\"https://example.com\"}", "localhost:8080", connection);

            assertEquals("abcd1234", result.id());
            assertEquals("http://localhost:8080/abcd1234", result.shortUrl());
            assertEquals(Optional.of("https://example.com"), service.resolveShortLink("abcd1234", connection));
        }
    }

    @Test
    void shouldRejectRelativeUrls() throws Exception {
        LinkService service = new LinkService(new Gson(), repository, () -> "abcd1234");

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    service.createShortLink("{\"url\":\"/relative\"}", "localhost:8080", connection));

            assertEquals("Invalid URL", exception.getMessage());
        }
    }

    @Test
    void shouldReturnEmptyWhenShortLinkDoesNotExist() throws Exception {
        LinkService service = new LinkService(new Gson(), repository, () -> "abcd1234");

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            repository.initializeSchema(connection);

            assertEquals(Optional.empty(), service.resolveShortLink("missing-id", connection));
        }
    }

    @Test
    void shouldRejectBlankShortLinkIds() throws Exception {
        LinkService service = new LinkService(new Gson(), repository, () -> "abcd1234");

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            repository.initializeSchema(connection);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    service.resolveShortLink("", connection));

            assertEquals("Invalid short link id", exception.getMessage());
        }
    }

    @Test
    void shouldCreateAndResolveShortLinkUsingAnAlias() throws Exception {
        LinkService service = new LinkService(new Gson(), repository, () -> "generated-id");

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            repository.initializeSchema(connection);

            CreateLinkResult result = service.createShortLink(
                    "{\"url\":\"https://example.com\",\"alias\":\"docs\"}",
                    "localhost:8080",
                    connection
            );

            assertEquals("docs", result.id());
            assertEquals("http://localhost:8080/docs", result.shortUrl());
            assertEquals(Optional.of("https://example.com"), service.resolveShortLink("docs", connection));
        }
    }

    @Test
    void shouldRejectDuplicateAliasesWhenCreatingShortLinks() throws Exception {
        LinkService service = new LinkService(new Gson(), repository, () -> "generated-id");

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            repository.initializeSchema(connection);

            service.createShortLink(
                    "{\"url\":\"https://example.com\",\"alias\":\"docs\"}",
                    "localhost:8080",
                    connection
            );

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    service.createShortLink(
                            "{\"url\":\"https://another-example.com\",\"alias\":\"docs\"}",
                            "localhost:8080",
                            connection
                    ));

            assertEquals("Alias already exists", exception.getMessage());
        }
    }

    @Test
    void shouldRejectBlankAliasesWhenCreatingShortLinks() throws Exception {
        LinkService service = new LinkService(new Gson(), repository, () -> "generated-id");

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            repository.initializeSchema(connection);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                    service.createShortLink(
                            "{\"url\":\"https://example.com\",\"alias\":\"\"}",
                            "localhost:8080",
                            connection
                    ));

            assertEquals("Invalid alias", exception.getMessage());
        }
    }
}