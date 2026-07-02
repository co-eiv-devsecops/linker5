# Linker — Environment Parity with Terraform (OCI)

Infrastructure as Code to create, from scratch, a VM on Oracle Cloud (OCI) with
everything needed to run Linker. The same `terraform apply` produces an identical
environment every time, so it can be used as **development** or **production**.

What it creates:

- A full network: VCN, Internet Gateway, Route Table, Security List, public Subnet.
- A compute instance (Ubuntu 24.04) with a public IP.
- Automatic provisioning via **cloud-init**: installs JDK 21 + Maven + git, clones
  the repo, builds the jar, and registers a `systemd` service (`linker.service`)
  that starts Linker on boot and restarts it on failure.

## Prerequisites

- Access to an OCI tenancy and a **compartment** (you need its OCID).
- An **SSH public key** (found in the Vault secret `sec-linker5-vm-sshpubkey`).
- Terraform >= 1.3 (already installed in OCI Cloud Shell).

## Quick start (recommended: OCI Cloud Shell)

Cloud Shell is pre-authenticated, so no API keys are required.

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars
# Edit terraform.tfvars: set compartment_ocid and ssh_public_key
terraform init
terraform plan
terraform apply
```

After `apply`, Terraform prints the outputs:

```
app_url             = "http://<PUBLIC_IP>:8080/"
instance_public_ip  = "<PUBLIC_IP>"
ssh_command         = "ssh ubuntu@<PUBLIC_IP>"
```

cloud-init takes ~2–4 minutes after boot to finish building. Then open `app_url`
in a browser, or:

```bash
curl -X POST http://<PUBLIC_IP>:8080/link \
  -H "Content-Type: application/json" \
  -d '{"url":"https://www.google.com"}'
```

## Destroy the environment

```bash
terraform destroy
```

## Running outside Cloud Shell (local machine)

Set up `~/.oci/config` with API-key auth, then override the auth method:

```bash
terraform apply -var="oci_auth=ApiKey"
```

## Useful variables

| Variable            | Default                     | Description                                  |
|---------------------|-----------------------------|----------------------------------------------|
| `compartment_ocid`  | —  (required)               | Compartment where resources are created      |
| `ssh_public_key`    | —  (required)               | SSH public key to access the VM              |
| `region`            | `sa-bogota-1`               | OCI region                                   |
| `instance_shape`    | `VM.Standard.E2.1.Micro`    | VM shape (Always Free, x86)                  |
| `repo_url`          | Linker GitHub repo (HTTPS)  | Repository cloned and built on the VM        |
| `github_token`      | `""`                        | Read-only PAT to clone a PRIVATE repo (empty = public) |
| `repo_branch`       | `main`                      | Branch to deploy                             |
| `app_port`          | `8080`                      | Port Linker listens on                       |

For a larger ARM Always-Free shape, set in `terraform.tfvars`:

```hcl
instance_shape      = "VM.Standard.A1.Flex"
instance_ocpus      = 1
instance_memory_gbs = 6
```

## Notes

- For a **private** repo, set `github_token` to a read-only, fine-grained GitHub
  PAT (scope: Contents → Read-only). cloud-init uses it only to clone. It is stored
  in the VM `user_data`, so prefer a short-lived token and revoke it after deploy.
  For a public repo, leave `github_token = ""`.
- `terraform.tfvars` and state files are git-ignored (they may contain OCIDs/keys).
