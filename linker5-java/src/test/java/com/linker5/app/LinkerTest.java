package com.linker5.app;

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
    private static final String LOCALHOST_PUBLIC_BASE_URL = "http://localhost:8080";
    private static final String IN_MEMORY_SQLITE_URL = "jdbc:sqlite::memory:";
    private static final String EXAMPLE_URL = "https://example.com";

    @Test
    void shouldDelegateCreateAndResolveFlows() throws Exception {
        LinkRepository repository = new LinkRepository();
        LinkService linkService = new LinkService(repository, () -> SHORT_LINK_ID);
        RedirectHandler redirectHandler = new RedirectHandler(repository, flagName -> true);
        Linker linker = new Linker(linkService, redirectHandler, repository);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            linker.initializeSchema(connection);

            CreateLinkResult result = linker.createShortLink(
                    new CreateShortLinkRequest(EXAMPLE_URL, null, LOCALHOST_PUBLIC_BASE_URL),
                    connection
            );

            assertEquals(SHORT_LINK_ID, result.id());
            assertEquals(LOCALHOST_PUBLIC_BASE_URL + "/" + SHORT_LINK_ID, result.shortUrl());
            assertEquals(Optional.of(EXAMPLE_URL), linker.resolveRedirect(SHORT_LINK_ID, connection));
            assertTrue(linker.isHealthy(connection));
        }
    }

    @Test
    void shouldExposeMetadataForAnExistingShortLink() throws Exception {
        LinkRepository repository = new LinkRepository();
        LinkService linkService = new LinkService(repository, () -> SHORT_LINK_ID);
        RedirectHandler redirectHandler = new RedirectHandler(repository, flagName -> true);
        Linker linker = new Linker(linkService, redirectHandler, repository);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            linker.initializeSchema(connection);
            linker.createShortLink(new CreateShortLinkRequest(EXAMPLE_URL, null, LOCALHOST_PUBLIC_BASE_URL), connection);

            assertEquals(Optional.of(EXAMPLE_URL), linker.resolveMetadata(SHORT_LINK_ID, connection));
        }
    }

    @Test
    void shouldDeleteShortLinkAndReportWhetherItExisted() throws Exception {
        LinkRepository repository = new LinkRepository();
        LinkService linkService = new LinkService(repository, () -> SHORT_LINK_ID);
        RedirectHandler redirectHandler = new RedirectHandler(repository, flagName -> true);
        Linker linker = new Linker(linkService, redirectHandler, repository);

        try (Connection connection = DriverManager.getConnection(IN_MEMORY_SQLITE_URL)) {
            linker.initializeSchema(connection);
            linker.createShortLink(new CreateShortLinkRequest(EXAMPLE_URL, null, LOCALHOST_PUBLIC_BASE_URL), connection);

            assertTrue(linker.deleteShortLink(SHORT_LINK_ID, connection));
            assertEquals(Optional.empty(), linker.resolveMetadata(SHORT_LINK_ID, connection));
            assertFalse(linker.deleteShortLink(SHORT_LINK_ID, connection));
        }
    }
}
