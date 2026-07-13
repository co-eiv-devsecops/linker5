# Linker — Paridad de entorno con Terraform (OCI)

Infraestructura como código para crear, desde cero, una VM en Oracle Cloud
(OCI) con todo lo necesario para correr Linker. El mismo `terraform apply`
produce un entorno idéntico cada vez, así que puede usarse tanto para
**desarrollo** como para **producción**.

Qué crea:

- Una red completa: VCN, Internet Gateway, Route Table, Security List y Subnet pública.
- Una instancia de cómputo (Ubuntu 24.04) con IP pública.
- Aprovisionamiento automático vía **cloud-init**: instala JDK 21 + Maven + git,
  clona el repo, compila el jar y registra un servicio `systemd`
  (`linker.service`) que inicia Linker al arrancar y lo reinicia si falla.

## Prerrequisitos

- Acceso a un tenancy de OCI y a un **compartment** (necesitás su OCID).
- Una **clave pública SSH** (se encuentra en el secreto de Vault `sec-linker5-vm-sshpubkey`).
- Terraform >= 1.3 (ya instalado en OCI Cloud Shell).

## Inicio rápido (recomendado: OCI Cloud Shell)

Cloud Shell ya viene autenticado, así que no hacen falta claves de API.

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
# Editar terraform.tfvars: definir compartment_ocid y ssh_public_key
terraform init
terraform plan
terraform apply
```

Después de `apply`, Terraform imprime estos outputs:

```
app_url             = "http://<PUBLIC_IP>:8080/"
instance_public_ip  = "<PUBLIC_IP>"
ssh_command         = "ssh ubuntu@<PUBLIC_IP>"
```

`cloud-init` tarda unos 2 a 4 minutos después del arranque en terminar de
construir el entorno. Después abrí `app_url` en un navegador, o probá:

```bash
curl -X POST http://<PUBLIC_IP>:8080/link \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.google.com"}'
```

## Destruir el entorno

```bash
terraform destroy
```

## Ejecutar fuera de Cloud Shell (máquina local)

Configurá `~/.oci/config` con autenticación por API key y después sobrescribí el método de autenticación:

```bash
terraform apply -var="oci_auth=ApiKey"
```

## Variables útiles

| Variable            | Predeterminado              | Descripción                                  |
|---------------------|-----------------------------|----------------------------------------------|
| `compartment_ocid`  | —  (requerido)              | Compartment donde se crean los recursos      |
| `ssh_public_key`    | —  (requerido)              | Clave pública SSH para acceder a la VM       |
| `region`            | `sa-bogota-1`               | Región de OCI                                |
| `instance_shape`    | `VM.Standard.E2.1.Micro`    | Shape de la VM (Always Free, x86)            |
| `repo_url`          | Repo de Linker en GitHub (HTTPS) | Repositorio clonado y compilado en la VM |
| `github_token`      | `""`                        | PAT de solo lectura para clonar un repo PRIVADO (vacío = público) |
| `repo_branch`       | `main`                      | Rama a desplegar                             |
| `app_port`          | `8080`                      | Puerto en el que escucha Linker              |
| `ssh_allowed_cidr`  | `""` (requerido si `create_network = true`) | CIDR habilitado para SSH hacia la VM (puerto 22); no debe ser `0.0.0.0/0` |

Para usar un shape ARM Always-Free más grande, definí esto en `terraform.tfvars`:

```hcl
instance_shape      = "VM.Standard.A1.Flex"
instance_ocpus      = 1
instance_memory_gbs = 6
```

## Permisos IAM requeridos

`terraform apply` crea recursos de red y de cómputo, lo que requiere que la
identidad que ejecuta Terraform tenga permisos de manage en el compartment
objetivo. En un tenancy del curso o landing zone con permisos restringidos,
esto normalmente NO se les da a estudiantes (solo lectura), así que `apply`
falla con `404-NotAuthorizedOrNotFound` en `CreateVcn` / `LaunchInstance`
aunque `plan` funcione bien (porque `plan` es de solo lectura).

Para poder ejecutar `apply` de verdad, un administrador debe otorgarle al grupo
de estudiantes una política como esta (modo completo desde cero, un solo compartment):

```
Allow group <student-group> to manage virtual-network-family in compartment <target>
Allow group <student-group> to manage instance-family        in compartment <target>
Allow group <student-group> to manage volume-family          in compartment <target>
Allow group <student-group> to read   instance-images        in compartment <target>
```

Si se reutiliza una subnet existente que vive en un compartment de red separado
(`create_network = false`), también hay que agregar:

```
Allow group <student-group> to use subnets in compartment <network-compartment>
Allow group <student-group> to use vnics   in compartment <network-compartment>
```

Verificá rápido tus permisos con la CLI (crea y elimina una VCN descartable):

```bash
oci network vcn create --compartment-id <target> --cidr-blocks '["10.9.0.0/16"]' --display-name perm-test
# si funciona, tenés permisos de creación; eliminála con: oci network vcn delete --vcn-id <id> --force
```

## Notas

- Para un repo **privado**, definí `github_token` como un GitHub PAT granular y
  de solo lectura (scope: Contents → Read-only). `cloud-init` lo usa solamente
  para clonar. Queda almacenado en el `user_data` de la VM, así que conviene
  usar un token de vida corta y revocarlo después del despliegue. Para un repo
  público, dejá `github_token = ""`.
- `terraform.tfvars` y los state files están ignorados por git (pueden contener OCIDs o claves).
