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
        assertTrue(connectionString.contains("sslMode="));
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

        assertEquals("jdbc:mysql://azure-host:3306/azure-db?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=UTC", config.getConnectionString());
        assertEquals("azure-user", config.getDatabaseUser());
        assertEquals("azure-pwd", config.getDatabasePassword());
    }

    @Test
    void shouldRequireSslForAzureManagedMysqlHosts() {
        DatabaseConfig config = new DatabaseConfig(Map.of(
                "AZURE_MYSQL_HOST", "azure-server.mysql.database.azure.com",
                "AZURE_MYSQL_DATABASE", "azure-db"
        )::get);

        assertEquals(
                "jdbc:mysql://azure-server.mysql.database.azure.com:3306/azure-db?sslMode=REQUIRED&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                config.getConnectionString()
        );
    }
}
