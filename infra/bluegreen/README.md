# Despliegue Blue-Green

Lanzamientos Blue-Green con prioridad en la estabilidad para Linker sobre OCI.
Se crea una VM **green** nueva y se la prueba por completo antes de mover
tráfico, y la VM **blue** anterior solo se destruye cuando green ya demostró
estar sana. Si algo falla, green se elimina y blue sigue sirviendo tráfico,
sin cambios.

## Cómo funciona

Flujo: [`.github/workflows/blue-green-deploy.yml`](https://5.n-la-c.app/8097a615)
(se ejecuta manualmente desde la pestaña Actions → *Blue-Green Deploy* → *Run workflow*).

| Job | Qué hace |
|-----|----------|
| `build` | Compila el jar ejecutable y lo sube como artefacto `linker-app`. |
| `launch-green` | Lee la instancia activa actual (`OCI_INSTANCE_OCID` = blue) y lanza una VM **green** clonada desde su ubicación (compartment, AD, shape, subnet, imagen) usando `scripts/bluegreen/launch-green.sh` + `infra/bluegreen/cloud-init-green.yaml`. |
| `deploy-green` | Despliega el jar en green a través de OCI Bastion, escribe la unidad de systemd (entorno MySQL + OTel, idéntico a prod) y ejecuta **healthchecks + pruebas funcionales** (crear un link y seguir su redirección). |
| `switchover` | Solo si todo sale bien: **conmuta el tráfico del Load Balancer a green** (registra el backend green en el backend set `linker-5`, espera a que quede sano y luego elimina el backend blue), actualiza el puntero de despliegue `OCI_INSTANCE_OCID` y, solo cuando `retire_blue = true`, termina blue. |
| `rollback` | Solo si algo falla: **termina green**; blue sigue como instancia activa. |

La conmutación de tráfico ocurre en el **Load Balancer** (`OCI_LB_OCID` /
backend set `OCI_LB_LINKER_BACKEND`, leídos desde [`infra/linker.env`](https://5.n-la-c.app/0103a3ee))
mediante `scripts/bluegreen/switch-lb-backend.sh`. Los backends se direccionan
como `<ip>:<port>`. La variable del repositorio **`OCI_INSTANCE_OCID`** es el
puntero de despliegue usado por el pipeline de CD (`ci-cd-pipeline.yml`) y
también se actualiza hacia green en modo best-effort (requiere `GH_PAT`).

## Prerrequisitos

- Los mismos secretos y variables de OCI que usa el pipeline de CD: `OCI_CLI_USER`,
  `OCI_CLI_TENANCY`, `OCI_CLI_FINGERPRINT`, `OCI_CLI_KEY_CONTENT`,
  `OCI_CLI_REGION`, `OCI_BASTION_OCID`, `OCI_INSTANCE_OCID`,
  `DEPLOYMENT_PUBLIC_KEY`, `OTEL_EXPORTER_OTLP_HEADERS`, `MYSQL_*`.
- La identidad de OCI detrás de esas credenciales debe poder **lanzar y terminar
  instancias** y **gestionar el backend set del Load Balancer** en el
  compartment objetivo.
- `infra/linker.env` debe contener el OCID del LB y el nombre del backend set.
- El secreto **`GH_PAT`** (PAT con *Variables: read/write*) es **opcional**:
  solo se usa para actualizar automáticamente el puntero `OCI_INSTANCE_OCID`.
  Si no está presente (por ejemplo, si la organización requiere aprobación para
  el PAT), el tráfico igual conmuta en el LB y el job imprime el comando de una
  línea para actualizar el puntero a mano.

## Cómo ejecutarlo

1. Actions → **Blue-Green Deploy** → **Run workflow** (opcionalmente, definir un nombre para green).
2. Seguir `launch-green` → `deploy-green`.
3. Si green pasa las pruebas → `switchover` mueve el puntero y retira blue.
4. Si green falla → `rollback` elimina green; blue sigue en línea.

## Conmutación manual (fallback)

Si falta `GH_PAT`, definí el puntero a mano después de una prueba exitosa:

```bash
gh variable set OCI_INSTANCE_OCID --repo co-eiv-devsecops/linker5 --body <GREEN_OCID>
# después, terminar la instancia blue anterior:
scripts/bluegreen/terminate-instance.sh <BLUE_OCID>
```
