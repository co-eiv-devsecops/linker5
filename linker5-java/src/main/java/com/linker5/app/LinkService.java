package com.linker5.app;

import com.linker5.ids.ShortIdGenerator;
import com.linker5.ids.UuidShortIdGenerator;
import com.linker5.persistence.LinkRepository;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public class LinkService {

    private static final Set<String> ALLOWED_URL_SCHEMES = Set.of("http", "https");

    private final LinkRepository repository;
    private final ShortIdGenerator idGenerator;

    public LinkService() {
        this(new LinkRepository(), new UuidShortIdGenerator());
    }

    public LinkService(LinkRepository repository, ShortIdGenerator idGenerator) {
        this.repository = repository;
        this.idGenerator = idGenerator;
    }

    public CreateLinkResult createShortLink(CreateShortLinkRequest request, Connection connection) throws SQLException {
        String url = extractUrl(request);
        validateAbsoluteUrl(url);
        validatePublicBaseUrl(request.publicBaseUrl());

        String id = resolveId(request, connection);
        try {
            repository.save(connection, id, url);
        } catch (SQLException exception) {
            if (isDuplicateShortLinkId(exception)) {
                throw new IllegalArgumentException("Short link id already exists");
            }
            throw exception;
        }

        return new CreateLinkResult(id, buildShortUrl(request.publicBaseUrl(), id));
    }

    boolean isDuplicateShortLinkId(SQLException exception) {
        if (exception instanceof SQLIntegrityConstraintViolationException) {
            return true;
        }
        return exception.getMessage() != null && exception.getMessage().contains("UNIQUE constraint failed: shorturl.id");
    }

    private String resolveId(CreateShortLinkRequest request, Connection connection) throws SQLException {
        Optional<String> alias = extractAlias(request);
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

    Optional<String> extractAlias(CreateShortLinkRequest request) {
        return Optional.ofNullable(request.alias());
    }

    public Optional<String> resolveShortLink(String id, Connection connection) throws SQLException {
        validateShortLinkId(id);
        return repository.findUrlById(connection, id);
    }

    String extractUrl(CreateShortLinkRequest request) {
        if (request == null || request.url() == null) {
            throw new IllegalArgumentException("Missing 'url'");
        }
        return request.url();
    }

    void validateAbsoluteUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid URL");
        }
        String scheme = uri.getScheme();
        if (!uri.isAbsolute() || scheme == null || !ALLOWED_URL_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Invalid URL");
        }
    }

    void validateShortLinkId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Invalid short link id");
        }
    }

    void validatePublicBaseUrl(String publicBaseUrl) {
        if (publicBaseUrl == null || publicBaseUrl.isBlank()) {
            throw new IllegalArgumentException("Invalid public base URL");
        }
        validateAbsoluteUrl(publicBaseUrl);
    }

    String buildShortUrl(String publicBaseUrl, String id) {
        String normalizedBaseUrl = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return String.format("%s/%s", normalizedBaseUrl, id);
    }
}
