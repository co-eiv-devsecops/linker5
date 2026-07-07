package com.linker5;

/**
 * Simple environment-variable based feature flag provider.
 * Usage: Set environment variables like FEATURE_REDIRECTS_ENABLED=true
 */
public class EnvFeatureFlagProvider implements FeatureFlagProvider {

    @Override
    public boolean isEnabled(String flagName) {
        String envVar = "FEATURE_" + flagName.toUpperCase().replace("-", "_");
        String value = System.getenv(envVar);
        return "true".equalsIgnoreCase(value);
    }
}
