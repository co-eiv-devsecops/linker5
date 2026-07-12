package com.linker5.app;

import com.google.gson.Gson;
import com.linker5.persistence.LinkRepository;
import com.linker5.redirect.RedirectHandler;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkerTest {

    private static final String SHORT_LINK_ID = "docs1234";

    @Test
    void shouldDelegateCreateAndResolveFlows() throws Exception {
        LinkRepository repository = new LinkRepository();
        LinkService linkService = new LinkService(new Gson(), repository, () -> SHORT_LINK_ID);
        RedirectHandler redirectHandler = new RedirectHandler(repository, flagName -> true);
        Linker linker = new Linker(linkService, redirectHandler, repository);

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            linker.initializeSchema(connection);

            CreateLinkResult result = linker.createShortLink(
                    "{\"url\":\"https://example.com\"}",
                    "localhost:8080",
                    connection
            );

            assertEquals(SHORT_LINK_ID, result.id());
            assertEquals("http://localhost:8080/" + SHORT_LINK_ID, result.shortUrl());
            assertEquals(Optional.of("https://example.com"), linker.resolveRedirect(SHORT_LINK_ID, connection));
            assertTrue(linker.isHealthy(connection));
        }
    }

    @Test
    void shouldExposeMetadataForAnExistingShortLink() throws Exception {
        LinkRepository repository = new LinkRepository();
        LinkService linkService = new LinkService(new Gson(), repository, () -> SHORT_LINK_ID);
        RedirectHandler redirectHandler = new RedirectHandler(repository, flagName -> true);
        Linker linker = new Linker(linkService, redirectHandler, repository);

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            linker.initializeSchema(connection);
            linker.createShortLink("{\"url\":\"https://example.com\"}", "localhost:8080", connection);

            assertEquals(Optional.of("https://example.com"), linker.resolveMetadata(SHORT_LINK_ID, connection));
        }
    }

    @Test
    void shouldDeleteShortLinkAndReportWhetherItExisted() throws Exception {
        LinkRepository repository = new LinkRepository();
        LinkService linkService = new LinkService(new Gson(), repository, () -> SHORT_LINK_ID);
        RedirectHandler redirectHandler = new RedirectHandler(repository, flagName -> true);
        Linker linker = new Linker(linkService, redirectHandler, repository);

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            linker.initializeSchema(connection);
            linker.createShortLink("{\"url\":\"https://example.com\"}", "localhost:8080", connection);

            assertTrue(linker.deleteShortLink(SHORT_LINK_ID, connection));
            assertEquals(Optional.empty(), linker.resolveMetadata(SHORT_LINK_ID, connection));
            assertFalse(linker.deleteShortLink(SHORT_LINK_ID, connection));
        }
    }
}
