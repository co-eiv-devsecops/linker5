package com.linker5;

import java.util.UUID;

public class UuidShortIdGenerator implements ShortIdGenerator {

    @Override
    public String generate() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}