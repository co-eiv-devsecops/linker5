package com.linker5;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkRepositoryTest {

    @Test
    void shouldPersistAndFindStoredUrls() throws Exception {
        LinkRepository repository = new LinkRepository();

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            repository.initializeSchema(connection);
            repository.save(connection, "abc12345", "https://example.com");

            assertTrue(repository.findUrlById(connection, "abc12345").isPresent());
            assertEquals("https://example.com", repository.findUrlById(connection, "abc12345").orElseThrow());
        }
    }
}