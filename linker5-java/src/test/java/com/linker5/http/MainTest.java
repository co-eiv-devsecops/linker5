package com.linker5.http;

import com.linker5.app.LinkService;
import com.linker5.app.Linker;
import com.linker5.flags.EnvFeatureFlagProvider;
import com.linker5.observability.NoopAppObservability;
import com.linker5.persistence.LinkRepository;
import com.linker5.redirect.RedirectHandler;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @Test
    void shouldFallBackToEnvProviderWhenLaunchDarklyKeyIsMissing() {
        assertTrue(Main.createFeatureFlagProvider(null) instanceof EnvFeatureFlagProvider);
        assertTrue(Main.createFeatureFlagProvider("") instanceof EnvFeatureFlagProvider);
        assertTrue(Main.createFeatureFlagProvider("   ") instanceof EnvFeatureFlagProvider);
    }

    @Test
    void shouldConfigureLoggingAndExposeElapsedMillisHelper() throws Exception {
        Main.configureLogging(Level.FINE);
        assertEquals(Level.FINE, Logger.getLogger("").getLevel());

        long value = Main.elapsedMillis(System.nanoTime() - 2_000_000);
        assertTrue(value >= 0);
    }

    @Test
    void shouldInitializeSchemaUsingTheLinkerFacade() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            assertNotNull(connection);
            LinkRepository repository = new LinkRepository();
            Linker linker = new Linker(new LinkService(), new RedirectHandler(repository, new EnvFeatureFlagProvider()), repository);

            Main.initializeSchema(connection, linker, new NoopAppObservability());

            String id = UUID.randomUUID().toString().substring(0, 8);
            repository.save(connection, id, "https://example.com");

            assertEquals("https://example.com", repository.findUrlById(connection, id).orElseThrow());
        }
    }
}
