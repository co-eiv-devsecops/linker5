package com.linker5.app;

import com.linker5.persistence.LinkRepository;
import com.linker5.redirect.RedirectHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class Linker {

    private final LinkService linkService;
    private final RedirectHandler redirectHandler;
    private final LinkRepository repository;

    public Linker(LinkService linkService, RedirectHandler redirectHandler, LinkRepository repository) {
        this.linkService = linkService;
        this.redirectHandler = redirectHandler;
        this.repository = repository;
    }

    public CreateLinkResult createShortLink(String requestBody, String host, Connection connection) throws Exception {
        return linkService.createShortLink(requestBody, host, connection);
    }

    public Optional<String> resolveRedirect(String id, Connection connection) throws Exception {
        return redirectHandler.resolveRedirect(id, connection);
    }

    public Optional<String> resolveMetadata(String id, Connection connection) throws Exception {
        return repository.findUrlById(connection, id);
    }

    public boolean isHealthy(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next();
        }
    }

    public void initializeSchema(Connection connection) throws Exception {
        repository.initializeSchema(connection);
    }
}
