package com.linker5;

import com.launchdarkly.sdk.*;
import com.launchdarkly.sdk.server.LDClient;

/**
 * Feature flag provider using LaunchDarkly.
 */
public class LaunchDarklyFeatureFlagProvider implements FeatureFlagProvider {

    private final LDClient client;

    public LaunchDarklyFeatureFlagProvider() {
        this.client = LaunchDarklyConfig.getClient();
    }

    @Override
    public boolean isEnabled(String flagName) {
        // Use a default context for now (can be enhanced to use user-specific context)
        LDContext context = LDContext.create("server");
        return client.boolVariation(flagName, context, false);
    }
}
