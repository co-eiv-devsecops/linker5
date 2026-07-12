#!/usr/bin/env bash
set -euo pipefail

subscription_id="${AZURE_SUBSCRIPTION_ID:-$(az account show --query id -o tsv)}"
tenant_id="${AZURE_TENANT_ID:-$(az account show --query tenantId -o tsv)}"
region="${AZURE_REGION:-mexicocentral}"
prefix="${AZURE_PREFIX:-linker-prod}"
resource_group="${AZURE_RESOURCE_GROUP:-${prefix}-rg}"
database_name="${AZURE_DB_NAME:-linker}"
mysql_admin_user="${AZURE_MYSQL_ADMIN_USER:-linkeradmin}"
create_sp="${AZURE_CREATE_SERVICE_PRINCIPAL:-true}"
output_dir="${AZURE_OUTPUT_DIR:-/tmp/opencode}"

subscription_suffix="${subscription_id//-/}"
subscription_suffix="${subscription_suffix: -6}"

storage_account_name="${AZURE_STORAGE_ACCOUNT_NAME:-${prefix//-/}${subscription_suffix}sa}"
function_app_name="${AZURE_FUNCTION_APP_NAME:-${prefix}-func-${subscription_suffix}}"
mysql_server_name="${AZURE_MYSQL_SERVER_NAME:-${prefix}-mysql-${subscription_suffix}}"
service_principal_name="${AZURE_SERVICE_PRINCIPAL_NAME:-${prefix}-github-actions}"
rg_scope="/subscriptions/${subscription_id}/resourceGroups/${resource_group}"

summary_file="${output_dir}/${prefix}-azure-summary.json"
secret_file="${output_dir}/${prefix}-azure-secrets.env"

mkdir -p "${output_dir}"
chmod 700 "${output_dir}"

generate_password() {
  python3 - <<'PY'
import secrets
import string

alphabet = string.ascii_letters + string.digits + "!@#$%^&*()-_=+"
while True:
    password = ''.join(secrets.choice(alphabet) for _ in range(24))
    categories = [
        any(c.islower() for c in password),
        any(c.isupper() for c in password),
        any(c.isdigit() for c in password),
        any(not c.isalnum() for c in password),
    ]
    if sum(categories) >= 3:
        print(password)
        break
PY
}

ensure_resource_group() {
  az group create \
    --name "${resource_group}" \
    --location "${region}" \
    --tags project=linker environment=prod platform=azure \
    --output none
}

ensure_storage_account() {
  if ! az storage account show --name "${storage_account_name}" --resource-group "${resource_group}" --output none 2>/dev/null; then
    az storage account create \
      --name "${storage_account_name}" \
      --resource-group "${resource_group}" \
      --location "${region}" \
      --sku Standard_LRS \
      --allow-blob-public-access false \
      --min-tls-version TLS1_2 \
      --output none
  fi
}

ensure_mysql_server() {
  local mysql_password
  mysql_password="${AZURE_MYSQL_ADMIN_PASSWORD:-}"

  if az mysql flexible-server show --name "${mysql_server_name}" --resource-group "${resource_group}" --output none 2>/dev/null; then
    mysql_host="$(az mysql flexible-server show --name "${mysql_server_name}" --resource-group "${resource_group}" --query fullyQualifiedDomainName -o tsv)"
    mysql_password="${mysql_password:-__EXISTING_PASSWORD_NOT_RECOVERABLE__}"
  else
    mysql_password="${mysql_password:-$(generate_password)}"
    az mysql flexible-server create \
      --resource-group "${resource_group}" \
      --name "${mysql_server_name}" \
      --location "${region}" \
      --admin-user "${mysql_admin_user}" \
      --admin-password "${mysql_password}" \
      --sku-name Standard_B1ms \
      --tier Burstable \
      --storage-size 32 \
      --version 8.0.21 \
      --public-access None \
      --backup-retention 7 \
      --database-name "${database_name}" \
      --tags project=linker environment=prod platform=azure \
      --output none

    az mysql flexible-server firewall-rule create \
      --resource-group "${resource_group}" \
      --name "${mysql_server_name}" \
      --rule-name AllowAzureServices \
      --start-ip-address 0.0.0.0 \
      --end-ip-address 0.0.0.0 \
      --output none

    mysql_host="$(az mysql flexible-server show --name "${mysql_server_name}" --resource-group "${resource_group}" --query fullyQualifiedDomainName -o tsv)"
  fi

  az mysql flexible-server db create \
    --resource-group "${resource_group}" \
    --server-name "${mysql_server_name}" \
    --database-name "${database_name}" \
    --output none >/dev/null 2>&1 || true

  mysql_admin_password="${mysql_password}"
}

ensure_function_app() {
  if ! az functionapp show --name "${function_app_name}" --resource-group "${resource_group}" --output none 2>/dev/null; then
    if ! az functionapp create \
      --name "${function_app_name}" \
      --resource-group "${resource_group}" \
      --storage-account "${storage_account_name}" \
      --flexconsumption-location "${region}" \
      --runtime java \
      --runtime-version 21.0 \
      --instance-memory 2048 \
      --functions-version 4 \
      --disable-app-insights true \
      --https-only true \
      --tags project=linker environment=prod platform=azure \
      --output none; then
      if ! az functionapp show --name "${function_app_name}" --resource-group "${resource_group}" --output none 2>/dev/null; then
        return 1
      fi
    fi
  fi

  az functionapp config appsettings set \
    --name "${function_app_name}" \
    --resource-group "${resource_group}" \
    --settings \
      AZURE_MYSQL_HOST="${mysql_host}" \
      AZURE_MYSQL_DATABASE="${database_name}" \
      AZURE_MYSQL_USER="${mysql_admin_user}" \
      AZURE_MYSQL_PWD="${mysql_admin_password}" \
      FEATURE_REDIRECTS_ENABLED=true \
      LINKER_LOG_LEVEL=INFO \
      WEBSITE_RUN_FROM_PACKAGE=1 \
    --output none
}

ensure_service_principal() {
  sp_created="false"
  sp_client_id=""
  sp_client_secret=""
  sp_tenant_id="${tenant_id}"

  if [[ "${create_sp}" != "true" ]]; then
    return 0
  fi

  local existing_app_id
  existing_app_id="$(az ad app list --display-name "${service_principal_name}" --query "[0].appId" -o tsv 2>/dev/null || true)"

  if [[ -z "${existing_app_id}" ]]; then
    local sp_json
    if ! sp_json="$(az ad sp create-for-rbac --name "${service_principal_name}" --role Contributor --scopes "${rg_scope}" --query '{clientId:appId, clientSecret:password, tenantId:tenant}' -o json 2>/dev/null)"; then
      echo "WARNING: Unable to create service principal automatically. Check Entra/RBAC permissions." >&2
      return 0
    fi
    sp_client_id="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["clientId"])' <<<"${sp_json}")"
    sp_client_secret="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["clientSecret"])' <<<"${sp_json}")"
    sp_tenant_id="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["tenantId"])' <<<"${sp_json}")"
    sp_created="true"
    return 0
  fi

  sp_client_id="${existing_app_id}"
  sp_client_secret="$(az ad app credential reset --id "${existing_app_id}" --query password -o tsv)"

  az role assignment create \
    --assignee "${sp_client_id}" \
    --role Contributor \
    --scope "${rg_scope}" \
    --output none >/dev/null 2>&1 || true
}

write_outputs() {
  local function_url
  function_url="https://${function_app_name}.azurewebsites.net"

  cat > "${summary_file}" <<JSON
{
  "subscriptionId": "${subscription_id}",
  "tenantId": "${tenant_id}",
  "region": "${region}",
  "resourceGroup": "${resource_group}",
  "storageAccount": "${storage_account_name}",
  "functionApp": "${function_app_name}",
  "functionAppUrl": "${function_url}",
  "mysqlServer": "${mysql_server_name}",
  "mysqlHost": "${mysql_host}",
  "mysqlDatabase": "${database_name}",
  "servicePrincipalCreated": "${sp_created}"
}
JSON

  cat > "${secret_file}" <<EOF
AZURE_SUBSCRIPTION_ID=${subscription_id}
AZURE_TENANT_ID=${tenant_id}
AZURE_REGION=${region}
AZURE_RESOURCE_GROUP=${resource_group}
AZURE_FUNCTION_APP_NAME=${function_app_name}
AZURE_STORAGE_ACCOUNT=${storage_account_name}
AZURE_MYSQL_HOST=${mysql_host}
AZURE_MYSQL_DATABASE=${database_name}
AZURE_MYSQL_USER=${mysql_admin_user}
AZURE_MYSQL_PWD=${mysql_admin_password}
EOF

  if [[ -n "${sp_client_id}" && -n "${sp_client_secret}" ]]; then
    cat >> "${secret_file}" <<EOF
AZURE_CLIENT_ID=${sp_client_id}
AZURE_CLIENT_SECRET=${sp_client_secret}
EOF
  fi

  chmod 600 "${summary_file}" "${secret_file}"

  echo "Azure bootstrap complete."
  echo "Summary file: ${summary_file}"
  echo "Secrets file: ${secret_file}"
}

main() {
  current_subscription="$(az account show --query id -o tsv)"
  if [[ "${current_subscription}" != "${subscription_id}" ]]; then
    az account set --subscription "${subscription_id}"
  fi
  ensure_resource_group
  ensure_storage_account
  ensure_mysql_server
  ensure_function_app
  ensure_service_principal
  write_outputs
}

main "$@"
