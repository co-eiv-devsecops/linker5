# Linker

[![CI/CD Pipeline](https://github.com/co-eiv-devsecops/linker5/actions/workflows/ci-cd-pipeline.yml/badge.svg?branch=main)](http://5.n-la-c.app/c87c006d)

**Producción:** [5.n-la-c.app](http://5.n-la-c.app/5bf191c7) (OCI VM tras Load Balancer) · [Azure Functions](http://5.n-la-c.app/17b47df9) (serverless)

Linker is a monolithic URL shortener: it turns a long URL into a short one that
redirects. Java backend plus a small static UI.

## Documentación

El índice completo (y el mapa de cumplimiento de requisitos del curso) está en
[`docs/README.md`](docs/README.md):

Los links de navegación son short links generados por nuestro propio Linker:

- [`docs/API.md`](http://5.n-la-c.app/1de3bdd1) — API HTTP: `POST /link`, `GET /<id>`, `HEAD /<id>` (metadata), `DELETE /<id>`, `/healthz`
- [`docs/DESPLIEGUE.md`](http://5.n-la-c.app/7394b680) — pipeline CI/CD, entorno efímero de pruebas, despliegue Blue-Green 🟢 🔵 y objetivo serverless en Azure Functions
- [`docs/LANZAMIENTOS.md`](http://5.n-la-c.app/d1d4c892) — lanzamientos con feature flags, separados del despliegue
- [`docs/MONITOREO.md`](http://5.n-la-c.app/61d63272) — monitoreo post-despliegue con Grafana
- [`docs/OPERACIONES.md`](http://5.n-la-c.app/771bf4ab) — runbook: onboarding, scripts, 0 operaciones manuales en OCI/Azure

## Integrantes

- Cristian Santiago Pedraza Rodriguez
- Cristian Camilo Gomez Fernandez
- Nikolas Martinez Rivera
- Sergio Alejandro Idarraga Torres

## Repository layout

| Path | What it is |
|------|------------|
| `linker5-java/` | The Linker app (Java 21, shared core + OCI/Azure adapters) + static UI |
| `docs/` | Project documentation: API, deployment, launches, monitoring, operations |
| `scripts/deploy.sh` | Deployment script (build, restart service, healthcheck) |
| `scripts/bluegreen/` | Blue-Green scripts: launch green, LB switchover, terminate instance |
| `scripts/provision-azure.sh` | One-time bootstrap of the Azure Functions target (az CLI) |
| `scripts/shorten-url.sh` | Creates a short link via the Linker API |
| `scripts/linker.service` | systemd unit that runs Linker on the VM |
| `infra/bluegreen/` | Blue-Green infra: cloud-init for green + [README](infra/bluegreen/README.md) |
| `infra/terraform/` | Infrastructure as Code: creates a VM with everything to run Linker (environment parity) — see its [README](infra/terraform/README.md) |
| `.devcontainer/` | Coded development environment (JDK 21 + Maven) usable via Codespaces — see its [README](.devcontainer/README.md) |

## Build and run locally

```bash
cd linker5-java
mvn clean package -DskipTests
java -jar target/*-jar-with-dependencies.jar   # serves on http://localhost:8080/
```

## Deployment targets

Linker now supports two deploy targets with the same core use cases:

- OCI VM: keeps the current fat-jar + systemd deployment and uses `MYSQL_HOST`, `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PWD`.
- Azure Functions: packages an HTTP-triggered Java Functions app and uses `AZURE_MYSQL_HOST`, `AZURE_MYSQL_DATABASE`, `AZURE_MYSQL_USER`, `AZURE_MYSQL_PWD`.

The runtime keeps OCI compatibility. Azure reads the Azure-specific variable names without requiring OCI secrets to be renamed.

### Azure Functions packaging

```bash
cd linker5-java
mvn -DskipTests \
  -Dazure.functions.appName="your-function-app" \
  -Dazure.functions.resourceGroup="your-resource-group" \
  -Dazure.functions.region="mexicocentral" \
  azure-functions:package
```

The Azure package is written to `linker5-java/target/azure-functions/<app-name>/`.

### Azure route support

The Azure Functions target exposes:

- `GET /`
- `GET /css/*`
- `GET /js/*`
- `POST /link`
- `GET /{id}`
- `HEAD /{id}`
- `DELETE /{id}`
- `GET /healthz`

Both deploy targets reuse the same `src/main/resources/wwwroot` assets, so OCI and Azure serve the same frontend without duplicating files.

### GitHub Actions secrets and variables

OCI deploy continues using the existing OCI and `MYSQL_*` secrets.

Azure deploy requires these GitHub secrets:

- `AZURE_CLIENT_ID`
- `AZURE_TENANT_ID`
- `AZURE_SUBSCRIPTION_ID`
- `AZURE_MYSQL_HOST`
- `AZURE_MYSQL_DATABASE`
- `AZURE_MYSQL_USER`
- `AZURE_MYSQL_PWD`

Azure deploy also requires these repository/environment variables:

- `AZURE_FUNCTION_APP_NAME`
- `AZURE_RESOURCE_GROUP`
- `AZURE_REGION` (recommended value: `mexicocentral`)

### Azure bootstrap

Use `scripts/provision-azure.sh` to create the Function App, storage account, MySQL Flexible Server, and the Azure-specific app settings expected by the Functions target.

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
- `OTEL_EXPORTER_OTLP_PROTOCOL` — use `http/protobuf` for direct Grafana Cloud export
- `OTEL_EXPORTER_OTLP_HEADERS` — optional OTLP headers, e.g. `Authorization=Basic%20<token>`
- `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` — optional trace-specific endpoint
- `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT` — optional metrics-specific endpoint
- `OTEL_EXPORTER_OTLP_LOGS_ENDPOINT` — optional logs-specific endpoint

Behavior:

- If no OTLP endpoint variable is defined, Linker uses local logging exporters for traces/metrics and keeps OTel log export disabled by default.
- If any OTLP endpoint variable is defined, Linker switches traces and metrics to OTLP exporters.
- Logs only go to OTLP if `LINKER_OTEL_LOG_EXPORT=true` is also enabled.

Example for a local collector:

```bash
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4318 \
OTEL_EXPORTER_OTLP_PROTOCOL=http/protobuf \
OTEL_EXPORTER_OTLP_HEADERS="Authorization=Basic%20<token>" \
LINKER_OTEL_LOG_EXPORT=true \
LINKER_LOG_LEVEL=INFO \
java -jar target/*-jar-with-dependencies.jar
```

For direct Grafana Cloud OTLP, header values should be URL-encoded, so `Basic ` becomes `Basic%20`.

Create a short link:

```bash
curl -X POST http://localhost:8080/link \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.google.com"}'
```

Full API reference (redirect, `HEAD` metadata, `DELETE`, healthcheck) in
[`docs/API.md`](docs/API.md).

## Development environment (DevContainer)

Open the repo in **GitHub Codespaces** (Code → Codespaces → Create) or in VS Code
with the Dev Containers extension, and you get JDK 21 + Maven ready to go with no
local setup. Details in [`.devcontainer/README.md`](.devcontainer/README.md).

## Environment parity (Terraform)

`infra/terraform/` provisions a VM (network + compute + cloud-init) that installs
Java, builds Linker and runs it as a systemd service — the same definition works
as a dev or prod environment. Details and required IAM permissions in
[`infra/terraform/README.md`](infra/terraform/README.md).
