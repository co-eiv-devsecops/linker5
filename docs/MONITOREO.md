# Monitoreo con Grafana

> 🧭 **Guía de lectura — Paso 4 de 5** · [Índice](https://5.n-la-c.app/a8f7ea12) · [← Lanzamientos](https://5.n-la-c.app/d1d4c892) · [Siguiente: Operaciones →](https://5.n-la-c.app/771bf4ab)

Linker emite **logs, métricas y trazas** con OpenTelemetry y las exporta por
OTLP a **Grafana Cloud** (stack `prod-us-east-3`). La instrumentación vive en
[`Observability.java`](https://5.n-la-c.app/ff04ab37)
y la configuración del exporter la inyecta el pipeline de despliegue como
variables de entorno del servicio (ver `deploy-prod` en
[`ci-cd-pipeline.yml`](https://5.n-la-c.app/641808b5)).

- Service name: `linker5-java` (`OTEL_SERVICE_NAME`)
- Protocolo: `http/protobuf` hacia `otlp-gateway-prod-us-east-3.grafana.net`
- Credenciales: secreto `OTEL_EXPORTER_OTLP_HEADERS` del repositorio

## Métricas emitidas

| Métrica | Tipo | Qué mide |
|---------|------|----------|
| `linker.http.requests.total` | counter | Requests HTTP procesados |
| `linker.http.errors.total` | counter | Respuestas de error |
| `linker.http.request.duration.ms` | histograma | Latencia por request |
| `linker.http.requests.in_flight` | gauge | Requests en vuelo |
| `linker.short_links.created.total` | counter | Short links creados |
| `linker.db.operations.total` | counter | Operaciones a MySQL |
| `linker.db.operation.duration.ms` | histograma | Latencia de la base de datos |
| `linker.db.connection.state` | gauge | 1 = DB conectada, 0 = caída |
| `linker.http.request.payload.size.bytes` | histograma | Tamaño de payloads |
| `linker.http.response.size.bytes` | histograma | Tamaño de respuestas |

Atributos disponibles para filtrar/agrupar: `linker.route` (p. ej.
`create-short-link`, `redirect-short-link`, `short-link-metadata`,
`delete-short-link`, `healthcheck`), `http.request.method`,
`http.response.status_code`, `db.operation` (`SELECT`/`INSERT`/`DELETE`).

## Cómo navegar Grafana

1. Entrar al stack de Grafana Cloud del equipo (invitación por correo; el
   owner del stack agrega a los integrantes nuevos — ver
   [OPERACIONES.md](https://5.n-la-c.app/8193bc1d)).
2. **Dashboards** (menú lateral → Dashboards): el dashboard principal de
   Linker grafica requests por ruta, tasa de errores, latencia p95 y estado
   de la conexión a la base de datos, construido sobre las métricas de la
   tabla anterior.
3. **Explore** (menú lateral → Explore) para consultas ad-hoc:
   - Datasource **Prometheus** (métricas): las métricas OTLP llegan con los
     puntos convertidos a `_`, p. ej. `linker_http_requests_total`.
   - Datasource **Loki** (logs): filtrar por `service_name="linker5-java"`;
     los mensajes llevan el prefijo `[Info]`, `[Warn]`, `[Error]`, etc.
   - Datasource **Tempo** (trazas): buscar por service `linker5-java`; cada
     request genera un span `http.request` con hijos como
     `db.insert_short_url` o `http.delete_short_link`.

Consultas útiles en Explore (Prometheus):

```promql
# Tráfico por ruta (req/s)
sum by (linker_route) (rate(linker_http_requests_total[5m]))

# Tasa de errores
sum(rate(linker_http_errors_total[5m])) / sum(rate(linker_http_requests_total[5m]))

# Latencia p95
histogram_quantile(0.95, sum by (le) (rate(linker_http_request_duration_ms_bucket[5m])))

# ¿La base de datos está conectada?
linker_db_connection_state
```

## Monitoreo post-deploy (checklist)

Después de **cada** despliegue (normal o Blue-Green), quien desplegó debe:

1. Confirmar que el run de Actions terminó verde (el healthcheck ya pasó).
2. Abrir el dashboard de Linker en Grafana y observar ~10 minutos:
   - `linker_db_connection_state` = 1.
   - La tasa de errores no subió respecto al periodo previo al despliegue.
   - La latencia p95 se mantiene en el rango habitual.
   - Siguen llegando datapoints nuevos (si las métricas se congelan, la app
     dejó de exportar: revisar `journalctl -u linker.service`).
3. Ejecutar una prueba funcional real contra producción (crear un link,
   seguirlo, borrarlo — ver [API.md](https://5.n-la-c.app/f34ad579)).
4. Dejar registro en el PR/issue del despliegue: captura del dashboard y
   resultado del checklist.

Si algo está mal: rollback según [DESPLIEGUE.md](https://5.n-la-c.app/b5c1aa1c) (redesplegar
la versión anterior, o switchover inverso si fue Blue-Green) y registrar el
incidente.

## Check automático de Grafana en el pipeline (bono)

El chequeo manual anterior se automatiza agregando un job posterior a
`deploy-prod` que consulta la API de Prometheus de Grafana Cloud y falla el
pipeline si producción no se ve sana:

```yaml
  grafana-check:
    name: Grafana post-deploy check
    runs-on: ubuntu-latest
    needs: deploy-prod
    steps:
      - name: Verify metrics are flowing and error rate is low
        env:
          PROM_URL: ${{ secrets.GRAFANA_PROM_URL }}    # https://prometheus-prod-us-east-3.grafana.net/api/prom
          PROM_AUTH: ${{ secrets.GRAFANA_PROM_AUTH }}  # user:api-token
        run: |
          set -euo pipefail
          query() { curl -fsS -u "$PROM_AUTH" "$PROM_URL/api/v1/query" --data-urlencode "query=$1" | jq -r '.data.result[0].value[1] // "0"'; }

          # 1. La app está exportando métricas (hubo requests en los últimos 5m)
          REQS="$(query 'sum(increase(linker_http_requests_total[5m]))')"
          echo "Requests (5m): $REQS"

          # 2. La base de datos está conectada
          DB="$(query 'linker_db_connection_state')"
          [ "$DB" = "1" ] || { echo "DB desconectada según Grafana"; exit 1; }

          # 3. Tasa de errores < 5%
          ERR="$(query 'sum(rate(linker_http_errors_total[5m])) / clamp_min(sum(rate(linker_http_requests_total[5m])), 1e-9)')"
          awk -v e="$ERR" 'BEGIN { exit (e < 0.05) ? 0 : 1 }' \
            || { echo "Tasa de errores $ERR >= 5%"; exit 1; }

          echo "Grafana check OK"
```

Requiere dos secretos nuevos en el repositorio: `GRAFANA_PROM_URL` y
`GRAFANA_PROM_AUTH` (token de Grafana Cloud con permiso de lectura de
métricas).

---

**Siguiente en la guía →** [Paso 5: Operaciones](https://5.n-la-c.app/771bf4ab)
