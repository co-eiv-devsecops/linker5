# Variables y secretos

Referencia central de configuración de Linker. La convención del repo queda así:

- `.env.example` en la raíz: solo variables de **runtime local** de la app.
- GitHub Secrets / GitHub Variables: CI/CD y despliegues.
- `infra/terraform/*.tf`: variables de infraestructura en OCI.
- `infra/linker.env`: identificadores versionados del Load Balancer para Blue-Green.

## Ruta rápida

1. Para correr local: partir de [`.env.example`](https://5.n-la-c.app/50d0f47b) y exportar `MYSQL_*`, `PORT` y, si aplica, `FEATURE_*`.
2. Para CI/CD o producción: no usar `.env`; configurar GitHub Secrets, GitHub Variables y Terraform según la tabla.
3. Para Azure Functions: usar `AZURE_MYSQL_*` solo como app settings del runtime serverless.

### Ejemplo local mínimo

```bash
export PORT=8080
export MYSQL_HOST=127.0.0.1
export MYSQL_DATABASE=linker
export MYSQL_USER=linker
export MYSQL_PWD=change_me
export FEATURE_REDIRECTS_ENABLED=true

cd linker5-java
java -jar target/*-jar-with-dependencies.jar
```

### Qué NO poner en `.env.example`

- Secretos de CI/CD como `DEPLOYMENT_PRIVATE_KEY`, `OCI_CLI_KEY_CONTENT` o `GH_PAT`
- Credenciales de login federado como `AZURE_CLIENT_ID`, `AZURE_TENANT_ID` o `AZURE_SUBSCRIPTION_ID`
- Identificadores de infraestructura versionados como `OCI_LB_OCID` y `OCI_LB_LINKER_BACKEND`
- Variables exclusivas de Terraform (`TF_VAR_*`, `compartment_ocid`, `ssh_public_key`, etc.)

## Inventario centralizado

| Nombre | Tipo | Obligatoria | Ámbito/uso | Dónde se configura | Ejemplo/observaciones |
|--------|------|-------------|------------|--------------------|-----------------------|
| `PORT` | No secreta | No | Runtime app local/OCI | local, systemd, CI efímero | Default `8080`. La app también acepta `-Dlinker.port`. |
| `LINKER_LOG_LEVEL` | No secreta | No | Runtime app | local, systemd, Azure app settings, CI efímero | `INFO`, `DEBUG`, `WARN`, `ERROR`. Default `INFO`. |
| `MYSQL_HOST` | No secreta | Sí para runtime local/OCI | Runtime app MySQL | local, GitHub Secrets | Ej. `127.0.0.1` o host productivo. |
| `MYSQL_DATABASE` | No secreta | Sí para runtime local/OCI | Runtime app MySQL | local, GitHub Secrets | Ej. `linker`. |
| `MYSQL_USER` | Sensible | Sí para runtime local/OCI | Runtime app MySQL | local, GitHub Secrets | Usuario MySQL. |
| `MYSQL_PWD` | Secreta | Sí para runtime local/OCI | Runtime app MySQL | local, GitHub Secrets | No versionar secretos reales. |
| `MYSQL_SSL_MODE` | No secreta | No | Runtime app MySQL | local | Default implícito: `DISABLED`; si el host termina en `.mysql.database.azure.com`, pasa a `REQUIRED`. |
| `AZURE_MYSQL_HOST` | No secreta | Sí en Azure Functions | Runtime app MySQL (alias Azure) | GitHub Secrets, Azure app settings | Alias de `MYSQL_HOST` para el runtime serverless. |
| `AZURE_MYSQL_DATABASE` | No secreta | Sí en Azure Functions | Runtime app MySQL (alias Azure) | GitHub Secrets, Azure app settings | Alias de `MYSQL_DATABASE`. |
| `AZURE_MYSQL_USER` | Sensible | Sí en Azure Functions | Runtime app MySQL (alias Azure) | GitHub Secrets, Azure app settings | Alias de `MYSQL_USER`. |
| `AZURE_MYSQL_PWD` | Secreta | Sí en Azure Functions | Runtime app MySQL (alias Azure) | GitHub Secrets, Azure app settings | Alias de `MYSQL_PWD`. |
| `AZURE_MYSQL_SSL_MODE` | No secreta | No | Runtime app MySQL (alias Azure) | Azure app settings | Override explícito del SSL mode. |
| `FEATURE_REDIRECTS_ENABLED` | No secreta | No | Runtime app / feature flags | local, systemd, Azure app settings, CI efímero | Flag actual para `GET /<id>`. |
| `LAUNCHDARKLY_SDK_KEY` | Secreta | No | Runtime app / feature flags | local, secreto del entorno si se adopta | Si existe, la app usa LaunchDarkly; si no, usa `FEATURE_*`. |
| `OTEL_SERVICE_NAME` | No secreta | No | Runtime observabilidad | local, systemd, Azure app settings, Terraform | Default `linker5-java`. |
| `LINKER_OTEL_LOG_EXPORT` | No secreta | No | Runtime observabilidad | local, systemd, Azure app settings, Terraform | `true`/`false`. Default `false` en código. |
| `OTEL_EXPORTER_OTLP_ENDPOINT` | No secreta | No | Runtime observabilidad | local | Endpoint OTLP único; alternativa a los endpoints por señal. |
| `OTEL_EXPORTER_OTLP_HEADERS` | Secreta | Sí si se exporta a Grafana Cloud | Runtime observabilidad | local, GitHub Secrets, Terraform | Ej. `Authorization=Basic%20<token>`. |
| `OTEL_EXPORTER_OTLP_PROTOCOL` | No secreta | No | Runtime observabilidad | systemd, Azure app settings, Terraform | En despliegues actuales: `http/protobuf`. |
| `OTEL_RESOURCE_ATTRIBUTES` | No secreta | No | Runtime observabilidad | systemd, Azure app settings | En prod se usa `deployment.environment=...`. |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | No secreta | No | Runtime observabilidad | local, systemd, Azure app settings, Terraform | Endpoint OTLP de trazas. |
| `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT` | No secreta | No | Runtime observabilidad | local, systemd, Azure app settings, Terraform | Endpoint OTLP de métricas. |
| `OTEL_EXPORTER_OTLP_LOGS_ENDPOINT` | No secreta | No | Runtime observabilidad | local, systemd, Azure app settings, Terraform | Endpoint OTLP de logs. |
| `DEPLOYMENT_PRIVATE_KEY` | Secreta | Sí en CI/CD OCI | Despliegue/verify por Bastion | GitHub Secrets | Clave privada SSH de despliegue. |
| `DEPLOYMENT_PUBLIC_KEY` | No secreta | Sí en CI/CD OCI y Blue-Green | Despliegue/launch green | GitHub Secrets | Clave pública emparejada. |
| `OCI_CLI_USER` | Sensible | Sí en CI/CD OCI | OCI CLI | GitHub Secrets | OCID del usuario/API key principal. |
| `OCI_CLI_TENANCY` | Sensible | Sí en CI/CD OCI | OCI CLI | GitHub Secrets | OCID del tenancy. |
| `OCI_CLI_FINGERPRINT` | Sensible | Sí en CI/CD OCI | OCI CLI | GitHub Secrets | Fingerprint de la API key. |
| `OCI_CLI_KEY_CONTENT` | Secreta | Sí en CI/CD OCI | OCI CLI | GitHub Secrets | Contenido PEM de la API key. |
| `OCI_CLI_REGION` | No secreta | Sí en CI/CD OCI | OCI CLI | GitHub Variables, Terraform `region` | Región OCI, ej. `sa-bogota-1`. |
| `OCI_BASTION_OCID` | No secreta | Sí en CI/CD OCI | Despliegue/verify por Bastion | GitHub Variables | OCID del Bastion. |
| `OCI_INSTANCE_OCID` | No secreta | Sí en CI/CD OCI y Blue-Green | Puntero de despliegue activo | GitHub Variables | Blue-Green lo actualiza hacia green. |
| `OCI_LB_OCID` | No secreta | Sí en Blue-Green | OCI Load Balancer | `infra/linker.env` versionado | Lo consume `switch-lb-backend.sh`. |
| `OCI_LB_LINKER_BACKEND` | No secreta | Sí en Blue-Green | Backend set del Load Balancer | `infra/linker.env` versionado | Nombre del backend set de Linker. |
| `GH_PAT` | Secreta | No | Blue-Green | GitHub Secrets | PAT opcional para actualizar `OCI_INSTANCE_OCID` vía `gh variable set`. |
| `AZURE_CLIENT_ID` | Sensible | Sí en CI/CD Azure | Login OIDC a Azure | GitHub Secrets | App registration / service principal. |
| `AZURE_TENANT_ID` | Sensible | Sí en CI/CD Azure | Login OIDC a Azure | GitHub Secrets | Tenant de Azure. |
| `AZURE_SUBSCRIPTION_ID` | Sensible | Sí en CI/CD Azure | Login OIDC a Azure | GitHub Secrets | Subscription objetivo. |
| `AZURE_FUNCTION_APP_NAME` | No secreta | Sí en CI/CD Azure | Package, deploy y verify | GitHub Variables, script de provisión | También lo usa el `pom.xml` para empaquetar Azure Functions. |
| `AZURE_RESOURCE_GROUP` | No secreta | Sí en CI/CD Azure | Deploy y verify | GitHub Variables, script de provisión | Resource group de la Function App. |
| `AZURE_REGION` | No secreta | No | Package Azure y provisión | GitHub Variables, local para `scripts/provision-azure.sh` | Default en script: `mexicocentral`. |
| `AZURE_PREFIX` | No secreta | No | Provisión Azure | local para `scripts/provision-azure.sh` | Default `linker-prod`; deriva nombres. |
| `AZURE_DB_NAME` | No secreta | No | Provisión Azure | local para `scripts/provision-azure.sh` | Default `linker`. |
| `AZURE_MYSQL_ADMIN_USER` | Sensible | No | Provisión Azure | local para `scripts/provision-azure.sh` | Default `linkeradmin`. |
| `AZURE_MYSQL_ADMIN_PASSWORD` | Secreta | No | Provisión Azure | local para `scripts/provision-azure.sh` | Si no existe, el script genera una contraseña. |
| `AZURE_CREATE_SERVICE_PRINCIPAL` | No secreta | No | Provisión Azure | local para `scripts/provision-azure.sh` | Default `true`. |
| `AZURE_OUTPUT_DIR` | No secreta | No | Provisión Azure | local para `scripts/provision-azure.sh` | Default `/tmp/opencode`. |
| `AZURE_STORAGE_ACCOUNT_NAME` | No secreta | No | Provisión Azure | local para `scripts/provision-azure.sh` | Override opcional del nombre autogenerado. |
| `AZURE_MYSQL_SERVER_NAME` | No secreta | No | Provisión Azure | local para `scripts/provision-azure.sh` | Override opcional del nombre autogenerado. |
| `AZURE_SERVICE_PRINCIPAL_NAME` | No secreta | No | Provisión Azure | local para `scripts/provision-azure.sh` | Default `${AZURE_PREFIX}-github-actions`. |
| `AZURE_STORAGE_ACCOUNT` | No secreta | No | Salida de provisión Azure | archivo local generado por script | Sale en `<prefix>-azure-secrets.env`; no lo consume el runtime. |
| `AZURE_CLIENT_SECRET` | Secreta | No | Salida de provisión Azure | archivo local generado por script | Solo aparece si el script crea o resetea el service principal. |
| `GITHUB_TOKEN` | Secreta | Sí en publish package | GitHub Actions runtime | secreto automático de GitHub Actions | Se usa para `mvn deploy` a GitHub Packages. |
| `GITHUB_ACTOR` | No secreta | Sí en publish package | GitHub Actions runtime | contexto automático de GitHub Actions | Usuario para autenticación Maven. |
| `GRAFANA_PROM_URL` | No secreta | No | Verificación post-deploy propuesta | GitHub Secrets | Solo aplica al ejemplo opcional de `MONITOREO.md`. |
| `GRAFANA_PROM_AUTH` | Secreta | No | Verificación post-deploy propuesta | GitHub Secrets | Formato `user:api-token`. |
| `oci_auth` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_oci_auth` | `InstancePrincipal` en Cloud Shell, `ApiKey` local. |
| `region` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_region` | Default `sa-bogota-1`. |
| `compartment_ocid` | No secreta | Sí | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_compartment_ocid` | Compartment destino. |
| `ad_number` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_ad_number` | Availability Domain 1-based. |
| `project_name` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_project_name` | Prefijo de recursos. |
| `image_ocid` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_image_ocid` | Vacío = autoselección de Ubuntu 24.04. |
| `instance_shape` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_instance_shape` | Default `VM.Standard.E2.1.Micro`. |
| `instance_ocpus` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_instance_ocpus` | Solo shapes Flex. |
| `instance_memory_gbs` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_instance_memory_gbs` | Solo shapes Flex. |
| `ssh_public_key` | No secreta | Sí | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_ssh_public_key` | Clave pública SSH; no usar la privada. |
| `repo_url` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_repo_url` | Default al repo de GitHub. |
| `github_token` | Secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_github_token` | Solo para clonar repo privado; sensible. |
| `repo_branch` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_repo_branch` | Default `main`. |
| `app_port` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_app_port` | Se inyecta como `PORT` en systemd. |
| `linker_log_level` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_linker_log_level` | Se inyecta como `LINKER_LOG_LEVEL`. |
| `linker_otel_log_export` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_linker_otel_log_export` | Se inyecta como `LINKER_OTEL_LOG_EXPORT`. |
| `otel_service_name` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_otel_service_name` | Se inyecta como `OTEL_SERVICE_NAME`. |
| `otel_exporter_otlp_protocol` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_otel_exporter_otlp_protocol` | Se inyecta como `OTEL_EXPORTER_OTLP_PROTOCOL`. |
| `otel_exporter_otlp_traces_endpoint` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_otel_exporter_otlp_traces_endpoint` | Se inyecta como `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`. |
| `otel_exporter_otlp_metrics_endpoint` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_otel_exporter_otlp_metrics_endpoint` | Se inyecta como `OTEL_EXPORTER_OTLP_METRICS_ENDPOINT`. |
| `otel_exporter_otlp_logs_endpoint` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_otel_exporter_otlp_logs_endpoint` | Se inyecta como `OTEL_EXPORTER_OTLP_LOGS_ENDPOINT`. |
| `otel_exporter_otlp_headers` | Secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_otel_exporter_otlp_headers` | Sensible; se inyecta como `OTEL_EXPORTER_OTLP_HEADERS`. |
| `create_network` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_create_network` | `false` reutiliza subnet existente. |
| `subnet_ocid` | No secreta | Condicional | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_subnet_ocid` | Requerida cuando `create_network = false`. |
| `vcn_cidr` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_vcn_cidr` | Solo si Terraform crea la red. |
| `subnet_cidr` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_subnet_cidr` | Solo si Terraform crea la red. |
| `assign_public_ip` | No secreta | No | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_assign_public_ip` | Según políticas de subnet. |
| `ssh_allowed_cidr` | No secreta | Condicional | Terraform OCI | `terraform.tfvars`, `-var`, `TF_VAR_ssh_allowed_cidr` | Requerida cuando `create_network = true`; no puede ser `0.0.0.0/0`. |

## Caveats

- `.env.example` es una referencia para shell local; el binario Java no parsea `.env` por sí solo.
- Para local conviene usar `MYSQL_*`; `AZURE_MYSQL_*` queda reservado al runtime de Azure Functions.
- `infra/linker.env` no es un lugar para secretos: ahí solo deberían vivir identificadores estables del Load Balancer.
