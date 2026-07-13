# Operaciones de Linker (runbook)

> 🧭 **Guía de lectura — Paso 5 de 5** · [Índice](https://5.n-la-c.app/a8f7ea12) · [← Monitoreo](https://5.n-la-c.app/61d63272)

Guía para operar Linker en el día a día y para que un integrante nuevo pueda
contribuir desde el primer día.

## Onboarding: cómo contribuir siendo nuevo en el equipo

### Accesos que necesita un integrante nuevo

| Acceso | Quién lo da | Para qué |
|--------|-------------|----------|
| Repositorio GitHub (org `co-eiv-devsecops`) | Cualquier admin del repo | Código, PRs, Actions |
| Grafana Cloud (stack del equipo) | Owner del stack (invitación por correo) | Dashboards y monitoreo |
| Compartment de OCI + Vault | Admin del tenancy (solo si va a tocar infra) | Terraform, secretos (`sec-linker5-vm-sshpubkey`) |
| LaunchDarkly (si está configurado) | Owner del proyecto | Lanzar funcionalidades |

### Primer día

1. Clonar el repo y levantar el entorno de desarrollo. La vía sin fricción es
   **GitHub Codespaces** (Code → Codespaces → Create): el
   [devcontainer](https://5.n-la-c.app/cf744019) trae JDK 21 + Maven listos.
   En local solo se necesita JDK 21 y Maven.
2. Compilar, probar y correr:

   ```bash
   cd linker5-java
   mvn test
   mvn clean package
   java -jar target/*-jar-with-dependencies.jar   # http://localhost:8080/
   ```

3. Probar la API con los ejemplos de [API.md](https://5.n-la-c.app/f34ad579).
4. Leer [DESPLIEGUE.md](https://5.n-la-c.app/b5c1aa1c), [LANZAMIENTOS.md](https://5.n-la-c.app/92e7af2f) y
   [MONITOREO.md](https://5.n-la-c.app/a3f6b902) — con eso se entiende el ciclo completo
   código → pipeline → producción → monitoreo.

### Flujo de trabajo

Detallado en [CONTRIBUTING.md](https://5.n-la-c.app/ed449b8b). Resumen:

1. Rama nueva desde `main` (`feature/...`, `fix/...`).
2. Cambios chicos y enfocados; agregar/ajustar tests.
3. `mvn test` en local antes de subir.
4. Abrir PR hacia `main` usando la plantilla; el pipeline corre build, tests
   unitarios y el entorno efímero de integración automáticamente.
5. Revisión de otro integrante y merge. El merge a `main` **despliega a
   producción** automáticamente — después del merge, ejecutar el checklist
   post-despliegue de [MONITOREO.md](https://5.n-la-c.app/f26d466d).

## Participación del equipo

El trabajo se reparte de forma homogénea entre los cuatro integrantes:

- Todo cambio entra por **PR con revisor distinto al autor**, rotando
  revisores para que todos revisen y todos escriban.
- Las tareas (features, infra, docs, monitoreo) se rotan en cada entrega para
  que nadie quede pegado a un solo tipo de trabajo.
- La participación es auditable con git:

  ```bash
  git shortlog -sn --all --no-merges   # commits por persona
  git log --author="<nombre>" --stat   # detalle por persona
  ```

  y en GitHub: Insights → Contributors, y la pestaña de PRs por autor/revisor.

## Cómo se corren los scripts

Todos los scripts viven en [`scripts/`](https://5.n-la-c.app/dcb8e4ee) y son bash. En Windows se
corren desde Git Bash o WSL.

### `scripts/deploy.sh` — desplegar en una VM

Corre **en la VM** (o vía SSH). Actualiza el repo, compila el jar, reinicia el
servicio systemd y hace healthchecks con reintentos:

```bash
# En la VM, con el repo clonado en ~/linker5:
BRANCH=main ./scripts/deploy.sh
```

Variables (todas opcionales, con defaults): `BRANCH` (`main`), `APP_DIR`
(`$HOME/linker5`), `SERVICE_NAME` (`linker.service`), `HEALTHCHECK_URL`
(`http://127.0.0.1:8080/`), `HEALTHCHECK_RETRIES` (20), `HEALTHCHECK_DELAY`
(1s). Si el healthcheck falla, el script termina con error y vuelca los
últimos logs de `journalctl`.

> En operación normal **no se corre a mano**: lo invoca el pipeline. Es útil
> para diagnóstico o para el paso `deploy-inactive` del Blue-Green.

### `scripts/linker.service` — unidad systemd

Plantilla de la unidad que ejecuta Linker en la VM. El job `deploy-prod` del
pipeline instala/actualiza la unidad real con los secretos inyectados; este
archivo documenta su forma. Operaciones útiles en la VM:

```bash
sudo systemctl status linker.service
sudo journalctl -u linker.service -n 100 --no-pager
sudo systemctl restart linker.service
```

### `scripts/bluegreen/` — despliegue Blue-Green

Los invoca el workflow *Blue-Green Deploy* (no se corren a mano en operación
normal; sirven como fallback scriptado, nunca la consola):

```bash
# Lanzar la VM green clonando el placement de la blue actual
scripts/bluegreen/launch-green.sh <BLUE_OCID> <ssh-pub-key-file> infra/bluegreen/cloud-init-green.yaml [nombre]

# Conmutar el tráfico del Load Balancer al backend green y retirar el blue
scripts/bluegreen/switch-lb-backend.sh <LB_OCID> <backend-set> <GREEN_OCID> <BLUE_OCID>

# Eliminar una instancia (retirar blue tras el switchover, o green en rollback)
scripts/bluegreen/terminate-instance.sh <INSTANCE_OCID>
```

Requieren OCI CLI configurada. El detalle del flujo completo está en
[DESPLIEGUE.md](https://5.n-la-c.app/c9a4a25c) y
[`infra/bluegreen/README.md`](https://5.n-la-c.app/adf46677).

### `scripts/provision-azure.sh` — bootstrap del objetivo serverless

Crea toda la infraestructura de Azure por CLI (resource group, storage
account, Function App, MySQL Flexible Server, service principal para GitHub
Actions) y deja los secretos generados en archivos locales:

```bash
az login
AZURE_REGION=mexicocentral AZURE_PREFIX=linker-prod ./scripts/provision-azure.sh
```

Se corre **una sola vez** por entorno; después el pipeline se encarga de todos
los despliegues.

### `scripts/shorten-url.sh` — crear un short link

Acorta una URL usando la instancia de Linker (ver
[Política de links cortos](https://5.n-la-c.app/40f371af)):

```bash
LINKER_HOST=https://5.n-la-c.app ./scripts/shorten-url.sh https://www.google.com
# -> https://5.n-la-c.app/a1b2c3
```

### Terraform (infraestructura)

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars   # completar compartment_ocid y ssh_public_key
terraform init && terraform plan && terraform apply
terraform destroy                              # retirar el entorno
```

Detalles, variables y permisos IAM en
[`infra/terraform/README.md`](https://5.n-la-c.app/6554ff68).

## 0 operaciones manuales en la consola de OCI

Regla del equipo: **nada se crea, cambia ni borra desde la consola web de
OCI**. Toda la operación pasa por herramientas versionadas:

| Operación | Herramienta (no consola) |
|-----------|--------------------------|
| Crear/destruir VMs y red | Terraform (`infra/terraform/`) |
| Desplegar la app (OCI) | GitHub Actions (`deploy-prod`, vía OCI Bastion) |
| Desplegar la app (Azure Functions) | GitHub Actions (`deploy-azure` con login OIDC) |
| Blue-Green: crear instancia, switchover en el Load Balancer, retirar VM | Workflow *Blue-Green Deploy* + `scripts/bluegreen/*.sh` (OCI CLI) |
| Aprovisionar el objetivo Azure | `scripts/provision-azure.sh` (az CLI), nada en el portal |
| Acceso a la VM | SSH por Bastion (la acción `oci-bastion-deploy` lo gestiona) |
| Verificaciones puntuales | OCI CLI / az CLI (scriptable), nunca clicks en la consola |
| Secretos | GitHub Secrets del repositorio + OCI Vault |
| Config del LB (OCID, backend set) | Versionada en `infra/linker.env` |

Beneficios: todo cambio de infraestructura queda en el historial de git y en
los runs de Actions (auditable y reproducible), y no hay estado "hecho a mano"
que Terraform desconozca.

## Cómo navegar Grafana y sus dashboards

Guía completa en [MONITOREO.md](https://5.n-la-c.app/ba5dff2f): dónde está
el dashboard de Linker, qué paneles tiene, cómo usar Explore
(Prometheus/Loki/Tempo) y las consultas PromQL de referencia.

## Política de links cortos

**Todo link clickeable de Markdown que aparezca en la documentación del repositorio debe usar un short link generado por nuestro propio Linker** (dogfooding).

Si el destino original vive en este repo, no se deja como link relativo: primero se canoniza a GitHub en la rama `main` (`blob` para archivos, `tree` para directorios, preservando `#anchor`) y recién después se acorta.

Se dejan sin acortar solo los valores de configuración, URLs de ejemplo dentro de bloques de código o texto inline, y los sources de imágenes/badges.

Para acortar un link antes de usarlo en un documento:

```bash
LINKER_HOST=https://5.n-la-c.app ./scripts/shorten-url.sh <url-larga>
```

y usar en el markdown el `shortUrl` devuelto, normalizado a `https://5.n-la-c.app/<id>`. En la revisión de PRs se verifica que los links nuevos cumplan la política.

---

**Fin de la guía** · [Volver al índice](https://5.n-la-c.app/a8f7ea12)
