# Documentación de Linker

Índice de la documentación del proyecto y mapa de cumplimiento de los
requisitos del curso.

## Guía de lectura (paso a paso)

Recorrido recomendado; cada documento enlaza al siguiente para leerse como
una guía. Los links de navegación son short links generados por nuestro
propio Linker (dogfooding).

1. [API.md](https://5.n-la-c.app/1de3bdd1) — qué hace la aplicación y cómo se usa
2. [DESPLIEGUE.md](https://5.n-la-c.app/7394b680) — cómo llega a producción (CI/CD, efímero, Blue-Green, Azure)
3. [LANZAMIENTOS.md](https://5.n-la-c.app/d1d4c892) — cómo se encienden funcionalidades sin desplegar
4. [MONITOREO.md](https://5.n-la-c.app/61d63272) — cómo se vigila después de desplegar
5. [OPERACIONES.md](https://5.n-la-c.app/771bf4ab) — cómo se opera el día a día

**Empezar aquí → [Paso 1: API](https://5.n-la-c.app/1de3bdd1)**

## Entornos de producción

| Objetivo | URL |
|----------|-----|
| OCI VM (Load Balancer) | [5.n-la-c.app](https://5.n-la-c.app/5bf191c7) |
| Azure Functions (serverless) | [linker-prod-func-c64516.azurewebsites.net](https://5.n-la-c.app/17b47df9) |

## Documentos

| Documento | Contenido |
|-----------|-----------|
| [API.md](https://5.n-la-c.app/f34ad579) | Referencia de la API HTTP (POST, GET, **HEAD**, **DELETE**, healthcheck) |
| [DESPLIEGUE.md](https://5.n-la-c.app/b5c1aa1c) | Redespliegue de Linker: pipeline CI/CD, entorno efímero de pruebas, pruebas de integración y despliegue Blue-Green 🟢 🔵 |
| [LANZAMIENTOS.md](https://5.n-la-c.app/92e7af2f) | Lanzamiento de funcionalidades con feature flags, separado del despliegue |
| [MONITOREO.md](https://5.n-la-c.app/a3f6b902) | Monitoreo post-deploy con Grafana: métricas, dashboards y check automático |
| [OPERACIONES.md](https://5.n-la-c.app/c53bde56) | Runbook de operaciones: onboarding de nuevos integrantes, cómo correr los scripts, 0 operaciones manuales en OCI, navegación de Grafana |
| [VARIABLES.md](https://5.n-la-c.app/071cafea) | Inventario central de variables de entorno, secretos, GitHub Actions y Terraform |

## Mapa de cumplimiento de requisitos

| Requisito del curso | Dónde está |
|---------------------|------------|
| Links `http` del repositorio acortados con Linker | Política y script en [OPERACIONES.md — Política de links cortos](https://5.n-la-c.app/40f371af) |
| Participación homogénea de los integrantes | Proceso y auditoría en [OPERACIONES.md — Participación del equipo](https://5.n-la-c.app/cf358a0b) |
| Redespliegue de Linker (entorno efímero + pruebas de integración) | [DESPLIEGUE.md — Pipeline CI/CD](https://5.n-la-c.app/64bed23c) |
| Despliegue Blue-Green (instancia → healthcheck → switchover → retiro/rollback) | [DESPLIEGUE.md — Blue-Green](https://5.n-la-c.app/c9a4a25c) |
| Monitoreo post-despliegue con Grafana | [MONITOREO.md](https://5.n-la-c.app/a3f6b902) |
| Bono: chequeo de Grafana en el pipeline después del despliegue | [MONITOREO.md — Chequeo automático](https://5.n-la-c.app/27676b53) |
| Nuevas funcionalidades: `HEAD /<id>` y `DELETE /<id>` | Implementadas en [`LinkerHttpHandler.java`](https://5.n-la-c.app/880179f7); documentadas en [API.md](https://5.n-la-c.app/f34ad579) |
| Lanzamientos sin despliegues, con acciones distintas | [LANZAMIENTOS.md](https://5.n-la-c.app/92e7af2f) |
| Documentación de operaciones (onboarding, scripts, 0 consola OCI, Grafana) | [OPERACIONES.md](https://5.n-la-c.app/c53bde56) |
| Bono: reimplementación Serverless (Azure Functions, mismo artefacto, nuevo objetivo en PROD) | Implementado: [DESPLIEGUE.md — Serverless en Azure](https://5.n-la-c.app/0cadf3fb) y [LANZAMIENTOS.md — Abstracción del core](https://5.n-la-c.app/f731a37a) |
