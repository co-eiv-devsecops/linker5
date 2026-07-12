package com.linker5.persistence;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseConfigTest {

    @Test
    void shouldProvideTheDatabaseConnectionString() {
        DatabaseConfig config = new DatabaseConfig();
        String connectionString = config.getConnectionString();

        assertTrue(connectionString.startsWith("jdbc:mysql://"),
                "La cadena de conexión debe usar el driver estricto de MySQL");
        assertTrue(connectionString.contains("useSSL="));
        assertTrue(connectionString.contains("serverTimezone="));
    }

    @Test
    void shouldFallBackToAzureSpecificVariableNames() {
        DatabaseConfig config = new DatabaseConfig(Map.of(
                "AZURE_MYSQL_HOST", "azure-host",
                "AZURE_MYSQL_DATABASE", "azure-db",
                "AZURE_MYSQL_USER", "azure-user",
                "AZURE_MYSQL_PWD", "azure-pwd"
        )::get);

        assertEquals("jdbc:mysql://azure-host:3306/azure-db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC", config.getConnectionString());
        assertEquals("azure-user", config.getDatabaseUser());
        assertEquals("azure-pwd", config.getDatabasePassword());
    }
}
