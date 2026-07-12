package com.linker5.app;

import com.linker5.persistence.LinkRepository;
import com.linker5.redirect.RedirectHandler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class Linker implements LinkerUseCases {

    private final LinkService linkService;
    private final RedirectHandler redirectHandler;
    private final LinkRepository repository;

    public Linker(LinkService linkService, RedirectHandler redirectHandler, LinkRepository repository) {
        this.linkService = linkService;
        this.redirectHandler = redirectHandler;
        this.repository = repository;
    }

    @Override
    public CreateLinkResult createShortLink(CreateShortLinkRequest request, Connection connection) throws SQLException {
        return linkService.createShortLink(request, connection);
    }

    @Override
    public Optional<String> resolveRedirect(String id, Connection connection) throws SQLException {
        return redirectHandler.resolveRedirect(id, connection);
    }

    @Override
    public Optional<String> resolveMetadata(String id, Connection connection) throws SQLException {
        return repository.findUrlById(connection, id);
    }

    @Override
    public boolean deleteShortLink(String id, Connection connection) throws SQLException {
        return repository.deleteById(connection, id);
    }

    @Override
    public boolean isHealthy(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1");
             ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next();
        }
    }

    @Override
    public void initializeSchema(Connection connection) throws SQLException {
        repository.initializeSchema(connection);
    }
}
