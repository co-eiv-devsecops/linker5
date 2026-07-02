package com.linker5;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LinkerTest {

    @Test
    void shouldProvideTheDatabaseConnectionString() {
        Linker linker = new Linker();

        assertEquals("jdbc:sqlite:linker.db", linker.getDatabaseConnectionString());
    }
}
