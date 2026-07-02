package com.linker5;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Optional;

public class LinkRepository {

    public void initializeSchema(Connection connection) throws Exception {
        connection.createStatement().execute("CREATE TABLE IF NOT EXISTS shorturl(id TEXT PRIMARY KEY, url TEXT NOT NULL)");
    }

    public void save(Connection connection, String id, String url) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO shorturl(id,url) VALUES(?,?)")) {
            statement.setString(1, id);
            statement.setString(2, url);
            statement.executeUpdate();
        }
    }

    public Optional<String> findUrlById(Connection connection, String id) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("SELECT url FROM shorturl WHERE id=?")) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(resultSet.getString("url"));
                }
                return Optional.empty();
            }
        }
    }
}