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
