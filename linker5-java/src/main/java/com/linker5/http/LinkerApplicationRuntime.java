package com.linker5.http;

import com.google.gson.Gson;
import com.linker5.app.LinkService;
import com.linker5.app.Linker;
import com.linker5.app.LinkerUseCases;
import com.linker5.config.RuntimeConfig;
import com.linker5.flags.FeatureFlagProvider;
import com.linker5.ids.UuidShortIdGenerator;
import com.linker5.observability.AppObservability;
import com.linker5.observability.Observability;
import com.linker5.observability.OpenTelemetryAppObservability;
import com.linker5.persistence.LinkRepository;
import com.linker5.redirect.RedirectHandler;

import java.sql.Connection;
import java.util.logging.Logger;

public final class LinkerApplicationRuntime implements AutoCloseable {

    private static final Gson GSON = new Gson();

    private final RuntimeConfig config;
    private final LinkerUseCases linker;
    private final Connection db;
    private final AppObservability observability;
    private final Logger logger;

    private LinkerApplicationRuntime(RuntimeConfig config, LinkerUseCases linker, Connection db, AppObservability observability, Logger logger) {
        this.config = config;
        this.linker = linker;
        this.db = db;
        this.observability = observability;
        this.logger = logger;
    }

    public static LinkerApplicationRuntime initialize() throws Exception {
        RuntimeConfig config = RuntimeConfig.load();
        Main.configureLogging(config.logLevel());
        Observability.initialize(config);
        AppObservability observability = new OpenTelemetryAppObservability(Observability.get());
        Connection db = Main.openDatabase(observability);
        FeatureFlagProvider featureFlagProvider = Main.createFeatureFlagProvider(System.getenv("LAUNCHDARKLY_SDK_KEY"));
        LinkRepository repository = new LinkRepository();
        LinkerUseCases linker = new Linker(
                new LinkService(repository, new UuidShortIdGenerator()),
                new RedirectHandler(repository, featureFlagProvider),
                repository
        );
        Main.initializeSchema(db, linker, observability);
        return new LinkerApplicationRuntime(config, linker, db, observability, Logger.getLogger(Main.class.getName()));
    }

    public RuntimeConfig config() {
        return config;
    }

    public LinkerUseCases linker() {
        return linker;
    }

    public Connection db() {
        return db;
    }

    public AppObservability observability() {
        return observability;
    }

    public Gson gson() {
        return GSON;
    }

    public Logger logger() {
        return logger;
    }

    @Override
    public void close() throws Exception {
        try {
            db.close();
        } finally {
            observability.close();
        }
    }
}
