package com.linker5.app;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

public interface LinkerUseCases {

    CreateLinkResult createShortLink(CreateShortLinkRequest request, Connection connection) throws SQLException;

    Optional<String> resolveRedirect(String id, Connection connection) throws SQLException;

    Optional<String> resolveMetadata(String id, Connection connection) throws SQLException;

    boolean deleteShortLink(String id, Connection connection) throws SQLException;

    boolean isHealthy(Connection connection) throws SQLException;

    void initializeSchema(Connection connection) throws SQLException;
}
