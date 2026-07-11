package com.linker5.app;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.linker5.ids.ShortIdGenerator;
import com.linker5.ids.UuidShortIdGenerator;
import com.linker5.persistence.LinkRepository;

import java.net.URI;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

public class LinkService {

    private static final String ALIAS_FIELD = "alias";

    private final Gson gson;
    private final LinkRepository repository;
    private final ShortIdGenerator idGenerator;

    public LinkService() {
        this(new Gson(), new LinkRepository(), new UuidShortIdGenerator());
    }

    public LinkService(Gson gson, LinkRepository repository, ShortIdGenerator idGenerator) {
        this.gson = gson;
        this.repository = repository;
        this.idGenerator = idGenerator;
    }

    public CreateLinkResult createShortLink(String requestBody, String host, Connection connection) throws Exception {
        String url = extractUrl(requestBody);
        validateAbsoluteUrl(url);

        String id = resolveId(requestBody, connection);
        try {
            repository.save(connection, id, url);
        } catch (SQLException exception) {
            if (isDuplicateShortLinkId(exception)) {
                throw new IllegalArgumentException("Short link id already exists");
            }
            throw exception;
        }

        return new CreateLinkResult(id, buildShortUrl(host, id));
    }

    boolean isDuplicateShortLinkId(SQLException exception) {
        if (exception instanceof SQLIntegrityConstraintViolationException) {
            return true;
        }
        return exception.getMessage() != null && exception.getMessage().contains("UNIQUE constraint failed: shorturl.id");
    }

    private String resolveId(String requestBody, Connection connection) throws Exception {
        Optional<String> alias = extractAlias(requestBody);
        if (alias.isEmpty()) {
            return idGenerator.generate();
        }

        String requestedAlias = alias.get();
        if (requestedAlias.isBlank()) {
            throw new IllegalArgumentException("Invalid alias");
        }
        if (repository.findUrlById(connection, requestedAlias).isPresent()) {
            throw new IllegalArgumentException("Alias already exists");
        }
        return requestedAlias;
    }

    Optional<String> extractAlias(String requestBody) {
        JsonObject payload = gson.fromJson(requestBody, JsonObject.class);
        if (payload.has(ALIAS_FIELD) && !payload.get(ALIAS_FIELD).isJsonNull()) {
            return Optional.of(payload.get(ALIAS_FIELD).getAsString());
        }
        return Optional.empty();
    }

    public Optional<String> resolveShortLink(String id, Connection connection) throws Exception {
        validateShortLinkId(id);
        return repository.findUrlById(connection, id);
    }

    String extractUrl(String requestBody) {
        JsonObject payload = gson.fromJson(requestBody, JsonObject.class);
        if (payload == null || !payload.has("url") || payload.get("url").isJsonNull()) {
            throw new IllegalArgumentException("Missing 'url'");
        }
        return payload.get("url").getAsString();
    }

    void validateAbsoluteUrl(String url) {
        if (!URI.create(url).isAbsolute()) {
            throw new IllegalArgumentException("Invalid URL");
        }
    }

    void validateShortLinkId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Invalid short link id");
        }
    }

    String buildShortUrl(String host, String id) {
        return String.format("http://%s/%s", host, id);
    }
}
