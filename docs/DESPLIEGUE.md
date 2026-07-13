# Despliegue y redespliegue de Linker

> 🧭 **Guía de lectura — Paso 2 de 5** · [Índice](http://5.n-la-c.app/a8f7ea12) · [← API](http://5.n-la-c.app/1de3bdd1) · [Siguiente: Lanzamientos →](http://5.n-la-c.app/d1d4c892)

Este documento describe cómo se (re)despliega Linker: el pipeline de CI/CD
con su entorno efímero de pruebas, y el procedimiento de despliegue
**Blue-Green** 🟢 🔵.

Regla de oro: **todo despliegue pasa por GitHub Actions**. No se hacen
operaciones manuales en la consola de OCI (ver
[OPERACIONES.md](http://5.n-la-c.app/f8b20235)).

## Pipeline CI/CD (`ci-cd-pipeline.yml`)

Workflow: [`.github/workflows/ci-cd-pipeline.yml`](http://5.n-la-c.app/2a547e35)

Se dispara en cada `push`/`pull_request` a `main` (y manualmente con
`workflow_dispatch`). El mismo pipeline despliega a **dos objetivos de PROD**:
la VM de OCI y **Azure Functions** (serverless), generados desde el mismo
código:

```
build ──> test ──> integration-test (entorno efímero) ──┬──> package       ──> deploy-prod  (OCI VM)
                                                        └──> package-azure ──> deploy-azure ──> verify-azure
```

| Etapa | Qué hace |
|-------|----------|
| **build** | Compila con Maven (JDK 21). |
| **test** | Corre las pruebas unitarias (`mvn test`). |
| **integration-test** | Levanta un **entorno efímero**: un MySQL 8 como service container + la app real desde el jar, y ejecuta pruebas de integración y funcionalidad contra HTTP de verdad. El entorno vive solo durante el job y se destruye al terminar. |
| **package** | Publica el jar en GitHub Packages y lo sube como artefacto `linker-app`. |
| **deploy-prod** | Solo en `push` a `main` y con el environment `production`. Despliega el jar a la VM de OCI a través del **Bastion** (acción `oci-bastion-deploy`), instala/actualiza la unidad `linker.service` de systemd con la configuración (MySQL, OTel/Grafana) desde secretos, reinicia el servicio y hace **healthcheck** con reintentos. Si el healthcheck falla, el job falla y quedan los logs de `journalctl` en la salida. |
| **package-azure** | Empaqueta el objetivo serverless con `azure-functions:package` y lo sube como artefacto `linker-azure-function`. |
| **deploy-azure** | Login OIDC a Azure, sincroniza los app settings (`AZURE_MYSQL_*`, `FEATURE_REDIRECTS_ENABLED`) y despliega el paquete a la Function App con `Azure/functions-action`. |
| **verify-azure** | Espera a que `https://<function-app>.azurewebsites.net/healthz` responda y verifica las rutas de la Function desplegada. |

### Entorno efímero de pruebas

El job `integration-test` reproduce producción en miniatura dentro del runner:

1. MySQL 8 efímero (service container con healthcheck propio).
2. Se construye el **mismo jar** que va a producción (`package -DskipTests`).
3. Se arranca la app apuntando al MySQL efímero y se espera `GET /healthz`.
4. Pruebas funcionales con `curl`:
   - Crear un short link (`POST /link`) y validar que devuelve `id`.
   - Seguir la redirección (`GET /<id>`) y validar el header `Location`.
   - `GET` de un id inexistente devuelve `404`.
   - `POST` con payload inválido devuelve `400`.
5. Al terminar (éxito o falla) se apaga la app y el runner descarta todo.

Esto garantiza que lo que se prueba es el artefacto real, con una base de
datos real, sin dejar infraestructura viva ni costos residuales.

## Despliegue Blue-Green 🔵 🟢

Workflow: [`.github/workflows/blue-green-deploy.yml`](../.github/workflows/blue-green-deploy.yml)
(se dispara manualmente: Actions → *Blue-Green Deploy* → *Run workflow*).
Documentación detallada de la infraestructura en
[`infra/bluegreen/README.md`](../infra/bluegreen/README.md).

La idea: la VM **blue** (apuntada por la variable `OCI_INSTANCE_OCID`) sirve
tráfico detrás de un **Load Balancer de OCI**. Se crea una VM **green** nueva,
se prueba a fondo, y solo entonces el LB conmuta el tráfico. Si algo falla,
green se destruye y blue nunca se tocó.

### Flujo

```
        ┌──────────────────┐
        │ 1. build         │  Compila el jar (artefacto linker-app)
        └────────┬─────────┘
                 ▼
        ┌──────────────────┐
        │ 2. launch-green  │  Crea la instancia GREEN clonando el placement de
        │                  │  blue (scripts/bluegreen/launch-green.sh) y espera
        └────────┬─────────┘  el plugin de Bastion
                 ▼
        ┌──────────────────┐
        │ 3. deploy-green  │  Despliega el jar en green vía Bastion, instala la
        │                  │  unidad systemd y corre HEALTHCHECK + pruebas
        └────────┬─────────┘  funcionales (crear link y seguir su redirect)
       ok ▼             ▼ falla
┌──────────────────┐  ┌────────────────────────────┐
│ 4. switchover    │  │ rollback: se ELIMINA la VM │
│    (Load         │  │ green; blue sigue sirviendo │
│    Balancer)     │  │ tráfico sin interrupción    │
└────────┬─────────┘  └────────────────────────────┘
         ▼
┌──────────────────┐
│ 5. retirar blue  │  terminate-instance.sh sobre blue
│  (retire_blue)   │  (solo si el switchover fue exitoso)
└──────────────────┘
```

### Paso a paso

1. **Build**: se compila el jar y se publica como artefacto.
2. **Crear la instancia nueva** (`launch-green`): se lanza la VM green con
   [`scripts/bluegreen/launch-green.sh`](../scripts/bluegreen/launch-green.sh),
   clonando compartment, AD, shape, subnet e imagen de la blue actual, con
   [`cloud-init-green.yaml`](../infra/bluegreen/cloud-init-green.yaml) para el
   aprovisionamiento (Java). Se espera a que el plugin de Bastion esté RUNNING.
3. **Healthcheck y pruebas de funcionalidad** (`deploy-green`): vía Bastion se
   instala la misma unidad systemd de producción (MySQL + OTel/Grafana), se
   arranca la app y se valida: healthcheck en `:8080` con reintentos, creación
   de un short link (`POST /link`) y verificación del redirect (`GET /<id>`).
4. **Switchover** (`switchover`): el tráfico se conmuta en el **Load Balancer**
   con [`scripts/bluegreen/switch-lb-backend.sh`](../scripts/bluegreen/switch-lb-backend.sh):
   registra el backend green en el backend set, espera a que el LB lo reporte
   sano y remueve el backend blue. Después actualiza el puntero de despliegue
   (`OCI_INSTANCE_OCID`) para que el pipeline CD apunte a green.
5. **Retiro de la versión anterior**: con `retire_blue = true`, la VM blue se
   elimina con [`scripts/bluegreen/terminate-instance.sh`](../scripts/bluegreen/terminate-instance.sh).
   Por defecto queda viva (estabilidad primero) y se retira después.
6. **Rollback automático** (`rollback`): si cualquier paso falla, se **elimina
   la VM green** — blue nunca dejó de servir tráfico.

El LB y su backend set se leen de [`infra/linker.env`](../infra/linker.env)
(`OCI_LB_OCID`, `OCI_LB_LINKER_BACKEND`), versionados en el repo: cero
configuración manual en la consola.

## Despliegue Serverless en Azure Functions (bono)

Además de la VM de OCI, **el mismo pipeline** despliega Linker como Function
App en Azure (jobs `package-azure` → `deploy-azure` → `verify-azure`, ver
tabla anterior). Ambos objetivos se generan del mismo código: el core es
compartido y solo cambia el adaptador de entrada (ver
[LANZAMIENTOS.md](LANZAMIENTOS.md#abstracción-del-core-serverless-en-azure-functions-bono)).

- **Aprovisionamiento**: [`scripts/provision-azure.sh`](../scripts/provision-azure.sh)
  crea el resource group, la storage account, la Function App, el MySQL
  Flexible Server y el service principal para GitHub Actions — todo por CLI,
  nada en el portal.
- **Empaquetado**: `mvn azure-functions:package` genera el paquete en
  `linker5-java/target/azure-functions/<app-name>/`.
- **Despliegue**: login OIDC (`azure/login`) + `Azure/functions-action`, con
  los app settings (`AZURE_MYSQL_*`) sincronizados desde GitHub Secrets.
- **Verificación**: el job `verify-azure` espera el healthcheck en
  `https://<function-app>.azurewebsites.net/healthz` y valida las rutas.

La Function expone exactamente la misma API que la VM (`POST /link`,
`GET /<id>`, `HEAD /<id>`, `DELETE /<id>`, `/healthz` y la UI estática), ver
[API.md](API.md). Secretos y variables requeridos están listados en el
[README raíz](../README.md#github-actions-secrets-and-variables).

### Después del despliegue

Todo despliegue (normal o Blue-Green) termina con la verificación en Grafana
descrita en [MONITOREO.md](MONITOREO.md): revisar el dashboard de la app
durante los primeros minutos y confirmar tasa de errores y latencia normales.

## Redespliegue

Redesplegar la misma versión (o una rama específica) sin cambios de código:

- **Vía pipeline (preferido):** GitHub → Actions → *CI/CD Pipeline* →
  *Run workflow* sobre `main`. Repite todas las etapas, incluido el entorno
  efímero de pruebas, y despliega a producción.
- **Vía Blue-Green:** GitHub → Actions → *Blue-Green Deploy* → *Run workflow*
  (opcionalmente nombre para la instancia green y `retire_blue`). Útil cuando
  se quiere redesplegar sin ventana de indisponibilidad.

En ambos casos la evidencia queda en el run de Actions: logs de pruebas,
healthchecks y estado del servicio.

---

**Siguiente en la guía →** [Paso 3: Lanzamientos](http://5.n-la-c.app/d1d4c892)
