package com.linker5;

import java.sql.Connection;
import java.util.Optional;

/**
 * Handles redirect logic with feature flag support.
 * When the "redirects-enabled" flag is disabled, all redirects are blocked.
 */
public class RedirectHandler {

    private final LinkRepository repository;
    private final FeatureFlagProvider featureFlagProvider;

    public RedirectHandler(LinkRepository repository, FeatureFlagProvider featureFlagProvider) {
        this.repository = repository;
        this.featureFlagProvider = featureFlagProvider;
    }

    public Optional<String> resolveRedirect(String id, Connection connection) throws Exception {
        if (!featureFlagProvider.isEnabled("redirects-enabled")) {
            return Optional.empty();
        }
        return repository.findUrlById(connection, id);
    }
}
