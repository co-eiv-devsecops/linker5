# Lanzamiento de funcionalidades (release ≠ deploy)

> 🧭 **Guía de lectura — Paso 3 de 5** · [Índice](http://5.n-la-c.app/a8f7ea12) · [← Despliegue](http://5.n-la-c.app/7394b680) · [Siguiente: Monitoreo →](http://5.n-la-c.app/61d63272)

En Linker **desplegar** y **lanzar** son dos acciones distintas, con
mecanismos distintos:

| | Despliegue (deploy) | Lanzamiento (launch/release) |
|---|---|---|
| Qué es | Poner un binario nuevo a correr en producción | Activar una funcionalidad para los usuarios |
| Mecanismo | Pipeline CI/CD o Blue-Green (GitHub Actions) | **Feature flags** (variable de entorno o LaunchDarkly) |
| ¿Requiere binario nuevo? | Sí | **No** — el código ya está desplegado, apagado |
| Rollback | Redesplegar versión anterior / switchover inverso | Apagar el flag (segundos) |
| Documentado en | [DESPLIEGUE.md](DESPLIEGUE.md) | Este documento |

El código de una funcionalidad nueva viaja a producción **apagado** dentro de
un despliegue normal. El lanzamiento ocurre después, encendiendo el flag, sin
tocar el pipeline de despliegue ni generar un artefacto nuevo.

## Feature flags

La app resuelve los flags a través de la interfaz
[`FeatureFlagProvider`](../linker5-java/src/main/java/com/linker5/flags/FeatureFlagProvider.java),
con dos implementaciones:

1. **[`LaunchDarklyFeatureFlagProvider`](../linker5-java/src/main/java/com/linker5/flags/LaunchDarklyFeatureFlagProvider.java)**
   — se usa automáticamente si existe la variable `LAUNCHDARKLY_SDK_KEY`.
   Permite encender/apagar flags **en caliente desde el dashboard de
   LaunchDarkly**, sin reiniciar la app: lanzamiento con cero despliegues y
   cero reinicios.
2. **[`EnvFeatureFlagProvider`](../linker5-java/src/main/java/com/linker5/flags/EnvFeatureFlagProvider.java)**
   — fallback por variables de entorno. Un flag `mi-funcionalidad` se lee de
   `FEATURE_MI_FUNCIONALIDAD=true`.

Flags actuales:

| Flag | Variable de entorno | Controla |
|------|--------------------|----------|
| `redirects-enabled` | `FEATURE_REDIRECTS_ENABLED` | Las redirecciones `GET /<id>`. Apagado, los short links no redirigen. |

### Cómo se hace un lanzamiento

**Con LaunchDarkly (sin despliegue, sin reinicio):**

1. Entrar al dashboard de LaunchDarkly → proyecto Linker.
2. Activar el flag en el environment de producción.
3. Verificar en Grafana que la funcionalidad se está usando y no sube la tasa
   de errores ([MONITOREO.md](MONITOREO.md)).
4. Si algo sale mal: apagar el flag. El rollback toma segundos y no hay
   redespliegue.

**Con variables de entorno (fallback):** el flag se define como
`Environment=FEATURE_...=true` en la unidad systemd que instala el job de
despliegue (ver `deploy-prod` en el pipeline) y se aplica con un reinicio del
servicio. Es la opción usada cuando no hay SDK key de LaunchDarkly.

### Acciones distintas para desplegar y lanzar

- **Desplegar** = correr el workflow *CI/CD Pipeline* o *Blue-Green Deploy*
  (GitHub Actions). Produce y publica un binario.
- **Lanzar** = cambiar el estado de un flag (LaunchDarkly dashboard o
  configuración del servicio). No compila, no publica, no crea VMs.

Los dos caminos nunca se mezclan: un PR que agrega una funcionalidad detrás de
un flag se despliega apagado por el pipeline normal; el encendido es una
operación separada, posterior y reversible.

## Caso real: HEAD y DELETE

Las funcionalidades nuevas del curso se implementaron siguiendo este modelo:

- **`HEAD /<id>`** — devuelve la metadata del short link: la URL verdadera
  como body de la respuesta (commit `83b6dba`).
- **`DELETE /<id>`** — borra la URL especificada (commit `7c74e35`).

Ambas están implementadas en
[`LinkerHttpHandler.java`](../linker5-java/src/main/java/com/linker5/http/LinkerHttpHandler.java)
y documentadas con ejemplos en [API.md](API.md). Llegaron a producción por el
pipeline de despliegue normal y su activación/uso se verifica con las métricas
por ruta (`short-link-metadata`, `delete-short-link`) en Grafana.

## Abstracción del core: Serverless en Azure Functions (bono)

Linker está **abstraído** de la plataforma donde corre. El core no conoce el
servidor HTTP:

- [`Linker`](../linker5-java/src/main/java/com/linker5/app/Linker.java) /
  [`LinkService`](../linker5-java/src/main/java/com/linker5/app/LinkService.java) /
  [`LinkerUseCases`](../linker5-java/src/main/java/com/linker5/app/LinkerUseCases.java)
  contienen la lógica de negocio (crear, resolver, metadata, borrar).
- [`LinkerApplicationRuntime`](../linker5-java/src/main/java/com/linker5/http/LinkerApplicationRuntime.java)
  y [`LinkerApiHandler`](../linker5-java/src/main/java/com/linker5/http/LinkerApiHandler.java)
  son el runtime compartido, con [`LinkerRequest`](../linker5-java/src/main/java/com/linker5/http/LinkerRequest.java)/[`LinkerResponse`](../linker5-java/src/main/java/com/linker5/http/LinkerResponse.java)
  como contrato neutro de entrada/salida.
- La persistencia va detrás de
  [`LinkRepository`](../linker5-java/src/main/java/com/linker5/persistence/LinkRepository.java).

Sobre ese runtime compartido hay **dos adaptadores de entrada**, y el mismo
código genera el artefacto de todas las plataformas:

| Plataforma | Adaptador | Artefacto |
|------------|-----------|-----------|
| OCI VM (JVM) | [`LinkerHttpHandler`](../linker5-java/src/main/java/com/linker5/http/LinkerHttpHandler.java) + `Main` | jar-with-dependencies |
| Azure Functions | [`AzureLinkerFunction`](../linker5-java/src/main/java/com/linker5/http/AzureLinkerFunction.java) (HTTP triggers para `POST /link`, `GET /{*path}`, `HEAD /{id}`, `DELETE /{id}`) | paquete `azure-functions:package` |

El despliegue serverless es un **objetivo más de PROD en el mismo pipeline**
(jobs `package-azure` → `deploy-azure` → `verify-azure`), reutilizando las
mismas etapas de build, test y entorno efímero. Detalles operativos en
[DESPLIEGUE.md](DESPLIEGUE.md#despliegue-serverless-en-azure-functions-bono).

---

**Siguiente en la guía →** [Paso 4: Monitoreo](http://5.n-la-c.app/61d63272)
