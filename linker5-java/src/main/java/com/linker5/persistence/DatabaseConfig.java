package com.linker5.persistence;

public class DatabaseConfig {

    public String getConnectionString() {
        String host = System.getenv("MYSQL_HOST");
        String database = System.getenv("MYSQL_DATABASE");

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
        return System.getenv("MYSQL_USER");
    }

    public String getDatabasePassword() {
        return System.getenv("MYSQL_PWD");
    }
}
