# Linker

Linker is a monolithic URL shortener: it turns a long URL into a short one that
redirects. Single-file backend (`< 100` lines) plus a small static UI.

## Integrantes

- Cristian Santiago Pedraza Rodriguez
- Cristian Camilo Gomez Fernandez
- Nikolas Martinez Rivera
- Sergio Alejandro Idarraga Torres

## Repository layout

| Path | What it is |
|------|------------|
| `linker5-java/` | The Linker app (single-file `Main.java`, < 100 lines) + static UI |
| `scripts/deploy.sh` | Deployment script (build, restart service, healthcheck) |
| `scripts/linker.service` | systemd unit that runs Linker on the VM |
| `infra/terraform/` | Infrastructure as Code: creates a VM with everything to run Linker (environment parity) — see its [README](infra/terraform/README.md) |
| `.devcontainer/` | Coded development environment (JDK 21 + Maven) usable via Codespaces — see its [README](.devcontainer/README.md) |

## Build and run locally

```bash
cd linker5-java
mvn clean package -DskipTests
java -jar target/*-jar-with-dependencies.jar   # serves on http://localhost:8080/
```

## Observability configuration

The same JAR artifact can run with different log verbosity levels by changing only runtime configuration.

### Log verbosity

Linker reads the log level from either:

- Java system property: `-Dlinker.log.level=...`
- Environment variable: `LINKER_LOG_LEVEL=...`

Supported values are `DEBUG`, `INFO`, `WARN`, `ERROR`, `FATAL`.

Internally, the app maps them to Java logging thresholds, but the emitted application messages are reflected with the required names: `Debug`, `Info`, `Warn`, `Error`, `Fatal`.

To keep the terminal output cleaner in local runs, OpenTelemetry log export is disabled by default. You can enable duplicated OTel log records only when needed with:

- `LINKER_OTEL_LOG_EXPORT=true`
- or `-Dlinker.otel.logs.enabled=true`

Examples:

```bash
cd linker5-java
mvn clean package -DskipTests

# Normal operational logs
java -Dlinker.log.level=INFO -jar target/*-jar-with-dependencies.jar

# More verbose diagnostics with the exact same JAR
LINKER_LOG_LEVEL=DEBUG java -jar target/*-jar-with-dependencies.jar

# Only critical failures
LINKER_LOG_LEVEL=FATAL java -jar target/*-jar-with-dependencies.jar
```

### OpenTelemetry signals included

The application now emits OpenTelemetry:

- Logs
- Metrics
- Traces

The implementation uses the OpenTelemetry Java SDK and console/logging exporters so the telemetry can be observed locally without changing the deployable artifact.

### OTLP exporter preparation for Grafana / Collector

The SDK is also prepared to export telemetry through **OTLP** using environment variables.

Supported variables:

- `OTEL_EXPORTER_OTLP_ENDPOINT` — common OTLP endpoint
- `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` — optional trace-specific endpoint
- `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT` — optional metrics-specific endpoint
- `OTEL_EXPORTER_OTLP_LOGS_ENDPOINT` — optional logs-specific endpoint

Behavior:

- If no OTLP endpoint variable is defined, Linker uses local logging exporters for traces/metrics and keeps OTel log export disabled by default.
- If any OTLP endpoint variable is defined, Linker switches traces and metrics to OTLP exporters.
- Logs only go to OTLP if `LINKER_OTEL_LOG_EXPORT=true` is also enabled.

Example for a local collector:

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317 \
LINKER_OTEL_LOG_EXPORT=true \
LINKER_LOG_LEVEL=INFO \
java -jar target/*-jar-with-dependencies.jar
```

Create a short link:

```bash
curl -X POST http://localhost:8080/link \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.google.com"}'
```

## Development environment (DevContainer)

Open the repo in **GitHub Codespaces** (Code → Codespaces → Create) or in VS Code
with the Dev Containers extension, and you get JDK 21 + Maven ready to go with no
local setup. Details in [`.devcontainer/README.md`](.devcontainer/README.md).

## Environment parity (Terraform)

`infra/terraform/` provisions a VM (network + compute + cloud-init) that installs
Java, builds Linker and runs it as a systemd service — the same definition works
as a dev or prod environment. Details and required IAM permissions in
[`infra/terraform/README.md`](infra/terraform/README.md).
