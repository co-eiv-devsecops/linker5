package com.linker5;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RedirectHandler")
class RedirectHandlerTest {

    private final LinkRepository repository = new LinkRepository();

    @Nested
    @DisplayName("When redirect feature is enabled")
    class WhenRedirectEnabled {

        @Test
        void shouldReturnUrlWhenIdExists() throws Exception {
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
                repository.initializeSchema(connection);
                repository.save(connection, "abc123", "https://example.com");

                RedirectHandler handler = new RedirectHandler(repository, flag -> true);

                Optional<String> result = handler.resolveRedirect("abc123", connection);

                assertEquals(Optional.of("https://example.com"), result);
            }
        }

        @Test
        void shouldReturnEmptyWhenIdDoesNotExist() throws Exception {
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
                repository.initializeSchema(connection);

                RedirectHandler handler = new RedirectHandler(repository, flag -> true);

                Optional<String> result = handler.resolveRedirect("missing", connection);

                assertEquals(Optional.empty(), result);
            }
        }
    }

    @Nested
    @DisplayName("When redirect feature is disabled")
    class WhenRedirectDisabled {

        @Test
        void shouldReturnEmptyRegardlessOfIdExistence() throws Exception {
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
                repository.initializeSchema(connection);
                repository.save(connection, "abc123", "https://example.com");

                RedirectHandler handler = new RedirectHandler(repository, flag -> false);

                Optional<String> result = handler.resolveRedirect("abc123", connection);

                assertEquals(Optional.empty(), result);
            }
        }

        @Test
        void shouldReturnEmptyForMissingIdWhenDisabled() throws Exception {
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
                repository.initializeSchema(connection);

                RedirectHandler handler = new RedirectHandler(repository, flag -> false);

                Optional<String> result = handler.resolveRedirect("missing", connection);

                assertEquals(Optional.empty(), result);
            }
        }
    }
}
