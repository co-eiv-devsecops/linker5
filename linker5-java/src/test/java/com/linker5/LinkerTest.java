package com.linker5;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class LinkerTest {

    @Test
    void shouldProvideTheDatabaseConnectionString() {
        Linker linker = new Linker();

        String expectedUrl = "jdbc:mysql://10.0.65.126:3306/linker_db_5?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

        assertEquals(expectedUrl, linker.getDatabaseConnectionString());
    }
}