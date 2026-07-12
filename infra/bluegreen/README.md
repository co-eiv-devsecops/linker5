# Blue-Green Deployment

Stability-first blue-green releases for Linker on OCI. A new **green** VM is
created and fully tested before any traffic is moved, and the old **blue** VM is
only destroyed once green is proven healthy. If anything fails, green is removed
and blue keeps serving — untouched.

## How it works

Workflow: [`.github/workflows/blue-green-deploy.yml`](../../.github/workflows/blue-green-deploy.yml)
(run manually from the Actions tab → *Blue-Green Deploy* → *Run workflow*).

| Job | What it does |
|-----|--------------|
| `build` | Builds the runnable jar and uploads it as the `linker-app` artifact. |
| `launch-green` | Reads the current active instance (`OCI_INSTANCE_OCID` = blue) and launches a **green** VM cloned from its placement (compartment, AD, shape, subnet, image) using `scripts/bluegreen/launch-green.sh` + `infra/bluegreen/cloud-init-green.yaml`. |
| `deploy-green` | Deploys the jar to green through the OCI Bastion, writes the systemd unit (MySQL + OTel env, identical to prod) and runs **health + functional tests** (create a link, follow its redirect). |
| `switchover` | On success only: **switches Load Balancer traffic to green** (registers the green backend in the `linker-5` backend set, waits until it is healthy, then removes the blue backend), updates the deploy pointer `OCI_INSTANCE_OCID`, and — only when `retire_blue = true` — terminates blue. |
| `rollback` | On failure only: **terminates green**; blue stays as the active instance. |

Traffic switchover happens at the **Load Balancer** (`OCI_LB_OCID` / backend set
`OCI_LB_LINKER_BACKEND`, read from [`infra/linker.env`](../linker.env)) via
`scripts/bluegreen/switch-lb-backend.sh`. Backends are addressed as `<ip>:<port>`.
The repository variable **`OCI_INSTANCE_OCID`** is the deploy pointer used by the
CD pipeline (`ci-cd-pipeline.yml`) and is updated to green as well.

## Prerequisites

- The same OCI secrets/variables used by the CD pipeline: `OCI_CLI_USER`,
  `OCI_CLI_TENANCY`, `OCI_CLI_FINGERPRINT`, `OCI_CLI_KEY_CONTENT`,
  `OCI_CLI_REGION`, `OCI_BASTION_OCID`, `OCI_INSTANCE_OCID`,
  `DEPLOYMENT_PUBLIC_KEY`, `OTEL_EXPORTER_OTLP_HEADERS`, `MYSQL_*`.
- The OCI identity behind those credentials must be allowed to **launch and
  terminate instances** and **manage the Load Balancer backend set** in the
  target compartment.
- `infra/linker.env` must contain the LB OCID and backend set name.
- **`GH_PAT`** secret (PAT with *Variables: read/write*) is **optional**: it is
  only used to update the `OCI_INSTANCE_OCID` deploy pointer automatically. If it
  is absent (e.g. the org requires approval for the PAT), traffic still switches
  at the LB and the job prints the one-line command to update the pointer by hand.

## Run it

1. Actions → **Blue-Green Deploy** → **Run workflow** (optionally set a green name).
2. Watch `launch-green` → `deploy-green`.
3. If green passes → `switchover` moves the pointer and retires blue.
4. If green fails → `rollback` deletes green; blue is still live.

## Manual switchover (fallback)

If `GH_PAT` is missing, set the pointer by hand after a successful test:

```bash
gh variable set OCI_INSTANCE_OCID --repo co-eiv-devsecops/linker5 --body <GREEN_OCID>
# then terminate the old blue instance:
scripts/bluegreen/terminate-instance.sh <BLUE_OCID>
```
