package com.linker5.redirect;

import com.linker5.persistence.LinkRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RedirectHandler")
class RedirectHandlerTest {

    private static final String IN_MEMORY_SQLITE_URL = "jdbc:sqlite::memory:";
    private static final String SHORT_LINK_ID = "abc123";
    private static final String TARGET_URL = "https://example.com";

    private final LinkRepository repository = new LinkRepository();

    @Nested
    @DisplayName("When redirect feature is enabled")
    class WhenRedirectEnabled {

        @Test
        void shouldReturnUrlWhenIdExists() throws Exception {
            try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
                repository.initializeSchema(connection);
                repository.save(connection, SHORT_LINK_ID, TARGET_URL);

                RedirectHandler handler = new RedirectHandler(repository, flag -> true);

                Optional<String> result = handler.resolveRedirect(SHORT_LINK_ID, connection);

                assertEquals(Optional.of(TARGET_URL), result);
            }
        }

        @Test
        void shouldReturnEmptyWhenIdDoesNotExist() throws Exception {
            try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
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
            try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
                repository.initializeSchema(connection);
                repository.save(connection, SHORT_LINK_ID, TARGET_URL);

                RedirectHandler handler = new RedirectHandler(repository, flag -> false);

                Optional<String> result = handler.resolveRedirect(SHORT_LINK_ID, connection);

                assertEquals(Optional.empty(), result);
            }
        }

        @Test
        void shouldReturnEmptyForMissingIdWhenDisabled() throws Exception {
            try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
                repository.initializeSchema(connection);

                RedirectHandler handler = new RedirectHandler(repository, flag -> false);

                Optional<String> result = handler.resolveRedirect("missing", connection);

                assertEquals(Optional.empty(), result);
            }
        }
    }
}
