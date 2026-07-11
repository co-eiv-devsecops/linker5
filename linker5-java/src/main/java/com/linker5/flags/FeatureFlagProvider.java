package com.linker5.flags;

/**
 * Interface for feature flag evaluation.
 * Implementations can use LaunchDarkly, environment variables, or any other strategy.
 */
public interface FeatureFlagProvider {
    boolean isEnabled(String flagName);
}
