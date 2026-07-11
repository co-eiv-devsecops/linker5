package com.linker5.persistence;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkRepositoryTest {

    private static final String SHORT_LINK_ID = "abc12345";

    @Test
    void shouldPersistAndFindStoredUrls() throws Exception {
        LinkRepository repository = new LinkRepository();

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            repository.initializeSchema(connection);
            repository.save(connection, SHORT_LINK_ID, "https://example.com");

            assertTrue(repository.findUrlById(connection, SHORT_LINK_ID).isPresent());
            assertEquals("https://example.com", repository.findUrlById(connection, SHORT_LINK_ID).orElseThrow());
        }
    }
}
