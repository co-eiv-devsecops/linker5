package com.linker5.flags;

import com.launchdarkly.sdk.LDContext;
import com.launchdarkly.sdk.server.LDClient;

/**
 * Feature flag provider using LaunchDarkly. The LDClient is injected rather than
 * pulled from a static singleton, so it can be swapped or stubbed in tests.
 */
public class LaunchDarklyFeatureFlagProvider implements FeatureFlagProvider {

    private final LDClient client;

    public LaunchDarklyFeatureFlagProvider(LDClient client) {
        this.client = client;
    }

    public static LaunchDarklyFeatureFlagProvider forSdkKey(String sdkKey) {
        return new LaunchDarklyFeatureFlagProvider(new LDClient(sdkKey));
    }

    @Override
    public boolean isEnabled(String flagName) {
        // Use a default context for now (can be enhanced to use user-specific context)
        LDContext context = LDContext.create("server");
        return client.boolVariation(flagName, context, false);
    }
}
