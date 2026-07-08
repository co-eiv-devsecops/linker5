package com.linker5;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkerTest {

    @Test
    void shouldProvideTheDatabaseConnectionString() {
        Linker linker = new Linker();
        String connectionString = linker.getDatabaseConnectionString();

        assertTrue(connectionString.startsWith("jdbc:mysql://"),
                "La cadena de conexión debe usar el driver estricto de MySQL");

        assertTrue(connectionString.contains("useSSL="));
        assertTrue(connectionString.contains("serverTimezone="));
    }
}