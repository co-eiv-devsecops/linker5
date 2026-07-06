package com.linker5;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UuidShortIdGeneratorTest {

    @Test
    void shouldGenerateEightCharacterIdentifier() {
        UuidShortIdGenerator generator = new UuidShortIdGenerator();

        String generatedId = generator.generate();

        assertNotNull(generatedId);
        assertEquals(8, generatedId.length());
        assertTrue(generatedId.matches("[0-9a-fA-F]{8}"));
    }
}
