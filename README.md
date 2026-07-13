# Linker

[![CI/CD Pipeline](https://github.com/co-eiv-devsecops/linker5/actions/workflows/ci-cd-pipeline.yml/badge.svg?branch=main)](https://5.n-la-c.app/c87c006d)

Linker es un acortador de URLs monolítico: recibe una URL larga, genera una URL corta y redirige al destino original.
La aplicación usa un backend en Java y una interfaz web estática.

## Producción

| Entorno | URL |
|---------|-----|
| OCI VM (Load Balancer) | [5.n-la-c.app](https://5.n-la-c.app/5bf191c7) |
| Azure Functions (serverless) | [linker-prod-func-c64516.azurewebsites.net](https://5.n-la-c.app/17b47df9) |

## Inicio rápido local

Configuración local:

```bash
cp .env.example .env
# ajustar valores locales antes de exportarlos
set -a
source .env
set +a
```

La app no carga `.env` automáticamente; ese archivo es solo una referencia para exportar variables. La referencia completa está en [`docs/VARIABLES.md`](https://5.n-la-c.app/071cafea).

Compilar y ejecutar:

```bash
cd linker5-java
mvn clean package -DskipTests
java -jar target/*-jar-with-dependencies.jar
```

La aplicación queda disponible en:

- `http://localhost:8080/`

Crear un short link de prueba:

```bash
curl -X POST http://localhost:8080/link \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.google.com"}'
```

Para la referencia completa de endpoints, ver [`docs/API.md`](https://5.n-la-c.app/f34ad579).

## Documentación

La documentación completa del proyecto está en [`docs/README.md`](https://5.n-la-c.app/700c9e43).

Documentos principales:

- [`docs/API.md`](https://5.n-la-c.app/f34ad579) — referencia de la API HTTP
- [`docs/DESPLIEGUE.md`](https://5.n-la-c.app/b5c1aa1c) — CI/CD, Blue-Green y Azure Functions
- [`docs/LANZAMIENTOS.md`](https://5.n-la-c.app/92e7af2f) — feature flags y lanzamientos sin despliegue
- [`docs/MONITOREO.md`](https://5.n-la-c.app/a3f6b902) — logs, métricas, trazas y Grafana
- [`docs/OPERACIONES.md`](https://5.n-la-c.app/c53bde56) — onboarding, scripts y operación del sistema
- [`docs/VARIABLES.md`](https://5.n-la-c.app/071cafea) — inventario central de variables, secretos y convenciones de configuración

## Estructura del repositorio

| Ruta | Descripción |
|------|-------------|
| `linker5-java/` | Aplicación principal en Java + interfaz estática |
| `docs/` | Documentación funcional, operativa y de despliegue |
| `scripts/` | Scripts de despliegue y soporte operativo |
| `infra/` | Infraestructura como código y soporte de Blue-Green |
| `.devcontainer/` | Entorno de desarrollo reproducible con JDK 21 + Maven |

## Entorno de desarrollo

Podés abrir el repositorio en **GitHub Codespaces** o en **VS Code** con **Dev Containers**.
Más detalles en [`.devcontainer/README.md`](https://5.n-la-c.app/cf744019).

## Infraestructura

La infraestructura y la paridad de entorno están documentadas en:

- [`infra/terraform/README.md`](https://5.n-la-c.app/6554ff68)
- [`infra/bluegreen/README.md`](https://5.n-la-c.app/adf46677)

## Integrantes

- Cristian Santiago Pedraza Rodriguez
- Cristian Camilo Gomez Fernandez
- Nikolas Martinez Rivera
- Sergio Alejandro Idarraga Torres
