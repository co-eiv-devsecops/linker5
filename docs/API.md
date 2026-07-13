# API de Linker

> 🧭 **Guía de lectura — Paso 1 de 5** · [Índice](http://5.n-la-c.app/a8f7ea12) · [Siguiente: Despliegue →](http://5.n-la-c.app/7394b680)

Linker expone una API HTTP sencilla en el puerto `8080` (configurable con
`PORT` o `-Dlinker.port`). Todos los ejemplos usan `$LINKER_HOST` como la URL
base de la instancia: `http://localhost:8080` en local, o en producción
`https://5.n-la-c.app` (OCI, tras el Load Balancer) /
`https://linker-prod-func-c64516.azurewebsites.net` (Azure Functions).

La **misma API** se sirve en los dos objetivos de producción: la VM de OCI y
la Function App de Azure (`https://<function-app>.azurewebsites.net`), porque
ambos comparten el mismo runtime (ver
[LANZAMIENTOS.md](LANZAMIENTOS.md#abstracción-del-core-serverless-en-azure-functions-bono)).

## Resumen de endpoints

| Método | Ruta | Descripción | Respuestas |
|--------|------|-------------|------------|
| `GET` | `/` | UI estática para crear links | `200` |
| `POST` | `/link` | Crea un short link | `201`, `400` |
| `GET` | `/<id>` | Redirige a la URL original | `302`, `404` |
| `HEAD` | `/<id>` | Metadata del short link (la URL verdadera en el body) | `200`, `404` |
| `DELETE` | `/<id>` | Borra el short link | `204`, `404` |
| `GET` | `/healthz` | Healthcheck (verifica la conexión a la base de datos) | `200`, `500` |

## POST /link — crear un short link

```bash
curl -X POST "$LINKER_HOST/link" \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.google.com"}'
```

Respuesta `201 Created`:

```json
{"id":"a1b2c3", "shortUrl":"http://<host>/a1b2c3"}
```

Validaciones (`400 Bad Request` con `{"error":"<motivo>"}`):

- El body debe ser JSON con el campo `url`.
- Solo se aceptan URLs con esquema `http` o `https`.

## GET /\<id\> — redirección

```bash
curl -i "$LINKER_HOST/a1b2c3"
# HTTP/1.1 302 Found
# Location: https://www.google.com
```

`404 Not Found` si el id no existe. La redirección está controlada por el
feature flag `redirects-enabled` (ver [LANZAMIENTOS.md](LANZAMIENTOS.md)).

## HEAD /\<id\> — metadata del short link

Devuelve la URL verdadera (original) como body de la respuesta, en
`text/plain`:

```bash
curl -sS -X HEAD "$LINKER_HOST/a1b2c3"
# https://www.google.com
```

> Nota: `curl -I` descarta el body por convención de HEAD; para ver el body
> de la respuesta use `curl -X HEAD` como arriba (o `curl --head --include`).

`404 Not Found` si el id no existe.

## DELETE /\<id\> — borrar un short link

```bash
curl -i -X DELETE "$LINKER_HOST/a1b2c3"
# HTTP/1.1 204 No Content
```

`404 Not Found` si el id no existe (o ya fue borrado — la operación es
idempotente en la práctica: repetirla devuelve 404).

## GET /healthz — healthcheck

```bash
curl -sS "$LINKER_HOST/healthz"
# {"status":"ok"}
```

Devuelve `500` con `{"status":"fail"}` si la base de datos no responde. Este
endpoint es el que usan el pipeline de despliegue y el procedimiento
Blue-Green para validar una instancia antes de enviarle tráfico.

## Errores

Todos los errores de la API se serializan como JSON
(`{"error":"..."}` o `{"status":"fail"}`); las rutas de contenido
(`404` de redirect/metadata) responden `text/plain`.

---

**Siguiente en la guía →** [Paso 2: Despliegue](http://5.n-la-c.app/7394b680)
