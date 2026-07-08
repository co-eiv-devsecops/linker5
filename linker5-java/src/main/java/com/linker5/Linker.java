
package com.linker5;

public class Linker {

    public String getDatabaseConnectionString() {
        String host = System.getenv("MYSQL_HOST");
        String database = System.getenv("MYSQL_DATABASE");

        if (host != null && !host.isBlank() && database != null && !database.isBlank()) {
            return "jdbc:mysql://" + host + ":3306/" + database
                    + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        }

        return "jdbc:sqlite:linker.db";
    }

    public String getDatabaseUser() {
        return System.getenv("MYSQL_USER");
    }

    public String getDatabasePassword() {
        return System.getenv("MYSQL_PWD");
    }
}