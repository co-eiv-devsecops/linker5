# Lanzamiento de funcionalidades (release ≠ deploy)

> 🧭 **Guía de lectura — Paso 3 de 5** · [Índice](https://5.n-la-c.app/a8f7ea12) · [← Despliegue](https://5.n-la-c.app/7394b680) · [Siguiente: Monitoreo →](https://5.n-la-c.app/61d63272)

En Linker **desplegar** y **lanzar** son dos acciones distintas, con
mecanismos distintos:

| | Despliegue (deploy) | Lanzamiento (launch/release) |
|---|---|---|
| Qué es | Poner un binario nuevo a correr en producción | Activar una funcionalidad para los usuarios |
| Mecanismo | Pipeline CI/CD o Blue-Green (GitHub Actions) | **Feature flags** (variable de entorno o LaunchDarkly) |
| ¿Requiere binario nuevo? | Sí | **No** — el código ya está desplegado, apagado |
| Rollback | Redesplegar versión anterior / switchover inverso | Apagar el flag (segundos) |
| Documentado en | [DESPLIEGUE.md](https://5.n-la-c.app/b5c1aa1c) | Este documento |

El código de una funcionalidad nueva viaja a producción **apagado** dentro de
un despliegue normal. El lanzamiento ocurre después, encendiendo el flag, sin
tocar el pipeline de despliegue ni generar un artefacto nuevo.

## Feature flags

La app resuelve los flags a través de la interfaz
[`FeatureFlagProvider`](https://5.n-la-c.app/6c07e3f6),
con dos implementaciones:

1. **[`LaunchDarklyFeatureFlagProvider`](https://5.n-la-c.app/34ff2a56)**
   — se usa automáticamente si existe la variable `LAUNCHDARKLY_SDK_KEY`.
   Permite encender/apagar flags **en caliente desde el dashboard de
   LaunchDarkly**, sin reiniciar la app: lanzamiento con cero deploys y
   cero reinicios.
2. **[`EnvFeatureFlagProvider`](https://5.n-la-c.app/6208643a)**
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
   de errores ([MONITOREO.md](https://5.n-la-c.app/a3f6b902)).
4. Si algo sale mal: apagar el flag. El rollback toma segundos y no hay
   redeploy.

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
[`LinkerHttpHandler.java`](https://5.n-la-c.app/880179f7)
y documentadas con ejemplos en [API.md](https://5.n-la-c.app/f34ad579). Llegaron a producción por el
pipeline de despliegue normal y su activación/uso se verifica con las métricas
por ruta (`short-link-metadata`, `delete-short-link`) en Grafana.

## Abstracción del core: Serverless en Azure Functions (bono)

Linker está **abstraído** de la plataforma donde corre. El core no conoce el
servidor HTTP:

- [`Linker`](https://5.n-la-c.app/9f35e2fa) /
  [`LinkService`](https://5.n-la-c.app/9425ec05) /
  [`LinkerUseCases`](https://5.n-la-c.app/5889ab75)
  contienen la lógica de negocio (crear, resolver, metadata, borrar).
- [`LinkerApplicationRuntime`](https://5.n-la-c.app/6d5c049d)
  y [`LinkerApiHandler`](https://5.n-la-c.app/e1cb8f01)
  son el runtime compartido, con [`LinkerRequest`](https://5.n-la-c.app/f91a3346)/[`LinkerResponse`](https://5.n-la-c.app/6f969db4)
  como contrato neutro de entrada/salida.
- La persistencia va detrás de
  [`LinkRepository`](https://5.n-la-c.app/2d5f665c).

Sobre ese runtime compartido hay **dos adaptadores de entrada**, y el mismo
código genera el artefacto de todas las plataformas:

| Plataforma | Adaptador | Artefacto |
|------------|-----------|-----------|
| OCI VM (JVM) | [`LinkerHttpHandler`](https://5.n-la-c.app/880179f7) + `Main` | jar-with-dependencies |
| Azure Functions | [`AzureLinkerFunction`](https://5.n-la-c.app/e47680ff) (HTTP triggers para `POST /link`, `GET /{*path}`, `HEAD /{id}`, `DELETE /{id}`) | paquete `azure-functions:package` |

El deploy serverless es un **objetivo más de PROD en el mismo pipeline**
(jobs `package-azure` → `deploy-azure` → `verify-azure`), reutilizando las
mismas etapas de build, test y entorno efímero. Detalles operativos en
[DESPLIEGUE.md](https://5.n-la-c.app/0cadf3fb).

---

**Siguiente en la guía →** [Paso 4: Monitoreo](https://5.n-la-c.app/61d63272)
