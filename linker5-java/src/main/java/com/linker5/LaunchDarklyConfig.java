package com.linker5;

import com.launchdarkly.sdk.*;
import com.launchdarkly.sdk.server.*;

public class LaunchDarklyConfig {
    private static LDClient client;

    public static synchronized LDClient getClient() {
        if (client == null) {
            String sdkKey = System.getenv("LAUNCHDARKLY_SDK_KEY");
            client = new LDClient(sdkKey);
        }
        return client;
    }

    public static void shutdown() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                // log
            }
        }
    }
}