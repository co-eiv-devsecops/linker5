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
| `switchover` | On success only: repoints the active pointer `OCI_INSTANCE_OCID` to green, then **terminates blue**. |
| `rollback` | On failure only: **terminates green**; blue stays as the active instance. |

The "active pointer" is the repository variable **`OCI_INSTANCE_OCID`**, which the
normal CD pipeline (`ci-cd-pipeline.yml`) already deploys to. Switchover = updating
that variable to the green OCID. There is no load balancer in this environment,
so this variable is the single source of truth for "which VM is production".

## Prerequisites

- The same OCI secrets/variables used by the CD pipeline: `OCI_CLI_USER`,
  `OCI_CLI_TENANCY`, `OCI_CLI_FINGERPRINT`, `OCI_CLI_KEY_CONTENT`,
  `OCI_CLI_REGION`, `OCI_BASTION_OCID`, `OCI_INSTANCE_OCID`,
  `DEPLOYMENT_PUBLIC_KEY`, `OTEL_EXPORTER_OTLP_HEADERS`, `MYSQL_*`.
- The OCI identity behind those credentials must be allowed to **launch and
  terminate instances** in the compartment.
- A **`GH_PAT`** secret (fine-grained PAT with *Variables: read/write* on this
  repo) so `switchover` can update `OCI_INSTANCE_OCID`. Without it, green is
  built and tested but the pointer is not moved and blue is not retired — the
  job fails loudly and prints the green OCID to set manually.

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
