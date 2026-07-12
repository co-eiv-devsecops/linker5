package com.linker5.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

public class LinkRepository {

    private static final String CREATE_SHORTURL_TABLE = """
            CREATE TABLE IF NOT EXISTS shorturl(
                id VARCHAR(64) PRIMARY KEY,
                url TEXT NOT NULL
            )
            """;

    public void initializeSchema(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(CREATE_SHORTURL_TABLE);
        }
    }

    public void save(Connection connection, String id, String url) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO shorturl(id,url) VALUES(?,?)")) {
            statement.setString(1, id);
            statement.setString(2, url);
            statement.executeUpdate();
        }
    }

    public Optional<String> findUrlById(Connection connection, String id) throws SQLException {
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

    public boolean deleteById(Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM shorturl WHERE id=?")) {
            statement.setString(1, id);
            return statement.executeUpdate() > 0;
        }
    }
}
