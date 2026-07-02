package com.linker5;

public class Linker {

    private static final String DATABASE_CONNECTION_STRING = "jdbc:sqlite:linker.db";

    public String getDatabaseConnectionString() {
        return DATABASE_CONNECTION_STRING;
    }
}