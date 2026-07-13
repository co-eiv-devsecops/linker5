# Documentación de Linker

Índice de la documentación del proyecto y mapa de cumplimiento de los
requisitos del curso.

## Guía de lectura (paso a paso)

Recorrido recomendado; cada documento enlaza al siguiente para leerse como
una guía. Los links de navegación son short links generados por nuestro
propio Linker (dogfooding).

1. [API.md](http://5.n-la-c.app/1de3bdd1) — qué hace la aplicación y cómo se usa
2. [DESPLIEGUE.md](http://5.n-la-c.app/7394b680) — cómo llega a producción (CI/CD, efímero, Blue-Green, Azure)
3. [LANZAMIENTOS.md](http://5.n-la-c.app/d1d4c892) — cómo se encienden funcionalidades sin desplegar
4. [MONITOREO.md](http://5.n-la-c.app/61d63272) — cómo se vigila después de desplegar
5. [OPERACIONES.md](http://5.n-la-c.app/771bf4ab) — cómo se opera el día a día

**Empezar aquí → [Paso 1: API](http://5.n-la-c.app/1de3bdd1)**

## Entornos de producción

| Objetivo | URL |
|----------|-----|
| OCI VM (Load Balancer) | [5.n-la-c.app](http://5.n-la-c.app/5bf191c7) |
| Azure Functions (serverless) | [linker-prod-func-c64516.azurewebsites.net](http://5.n-la-c.app/17b47df9) |

## Documentos

| Documento | Contenido |
|-----------|-----------|
| [API.md](API.md) | Referencia de la API HTTP (POST, GET, **HEAD**, **DELETE**, healthcheck) |
| [DESPLIEGUE.md](DESPLIEGUE.md) | Redespliegue de Linker: pipeline CI/CD, entorno efímero de pruebas, pruebas de integración y despliegue Blue-Green 🟢 🔵 |
| [LANZAMIENTOS.md](LANZAMIENTOS.md) | Lanzamiento de funcionalidades con feature flags, separado del despliegue |
| [MONITOREO.md](MONITOREO.md) | Monitoreo post-despliegue con Grafana: métricas, dashboards y chequeo automático |
| [OPERACIONES.md](OPERACIONES.md) | Runbook de operaciones: onboarding de nuevos integrantes, cómo correr los scripts, 0 operaciones manuales en OCI, navegación de Grafana |

## Mapa de cumplimiento de requisitos

| Requisito del curso | Dónde está |
|---------------------|------------|
| Links `http` del repositorio acortados con Linker | Política y script en [OPERACIONES.md — Política de links cortos](OPERACIONES.md#política-de-links-cortos) |
| Participación homogénea de los integrantes | Proceso y auditoría en [OPERACIONES.md — Participación del equipo](OPERACIONES.md#participación-del-equipo) |
| Redespliegue de Linker (entorno efímero + pruebas de integración) | [DESPLIEGUE.md — Pipeline CI/CD](DESPLIEGUE.md#pipeline-cicd-ci-cd-pipelineyml) |
| Despliegue Blue-Green (instancia → healthcheck → switchover → retiro/rollback) | [DESPLIEGUE.md — Blue-Green](DESPLIEGUE.md#despliegue-blue-green--) |
| Monitoreo post-despliegue con Grafana | [MONITOREO.md](MONITOREO.md) |
| Bono: chequeo de Grafana en el pipeline después del despliegue | [MONITOREO.md — Chequeo automático](MONITOREO.md#chequeo-automático-de-grafana-en-el-pipeline-bono) |
| Nuevas funcionalidades: `HEAD /<id>` y `DELETE /<id>` | Implementadas en [`LinkerHttpHandler.java`](../linker5-java/src/main/java/com/linker5/http/LinkerHttpHandler.java); documentadas en [API.md](API.md) |
| Lanzamientos sin despliegues, con acciones distintas | [LANZAMIENTOS.md](LANZAMIENTOS.md) |
| Documentación de operaciones (onboarding, scripts, 0 consola OCI, Grafana) | [OPERACIONES.md](OPERACIONES.md) |
| Bono: reimplementación Serverless (Azure Functions, mismo artefacto, nuevo objetivo en PROD) | Implementado: [DESPLIEGUE.md — Serverless en Azure](DESPLIEGUE.md#despliegue-serverless-en-azure-functions-bono) y [LANZAMIENTOS.md — Abstracción del core](LANZAMIENTOS.md#abstracción-del-core-serverless-en-azure-functions-bono) |
