package com.linker5.persistence;

import java.util.function.Function;

public class DatabaseConfig {

    private final Function<String, String> envProvider;

    public DatabaseConfig() {
        this(System::getenv);
    }

    DatabaseConfig(Function<String, String> envProvider) {
        this.envProvider = envProvider;
    }

    public String getConnectionString() {
        String host = getenv("MYSQL_HOST", "AZURE_MYSQL_HOST");
        String database = getenv("MYSQL_DATABASE", "AZURE_MYSQL_DATABASE");

        if (host == null || host.isBlank()) {
            host = "localhost";
        }

        if (database == null || database.isBlank()) {
            database = "linker";
        }

        return "jdbc:mysql://" + host + ":3306/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    public String getDatabaseUser() {
        return getenv("MYSQL_USER", "AZURE_MYSQL_USER");
    }

    public String getDatabasePassword() {
        return getenv("MYSQL_PWD", "AZURE_MYSQL_PWD");
    }

    private String getenv(String primary, String secondary) {
        String value = envProvider.apply(primary);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return envProvider.apply(secondary);
    }
}
