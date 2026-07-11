package com.linker5.persistence;

import org.junit.jupiter.api.Test;

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
}
