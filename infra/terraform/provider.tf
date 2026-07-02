terraform {
  required_version = ">= 1.3.0"
  required_providers {
    oci = {
      source  = "oracle/oci"
      version = ">= 5.0.0"
    }
  }
}

# In OCI Cloud Shell the session is already authenticated (instance principal),
# so NO API keys, fingerprints or ~/.oci/config are needed.
# To run this outside Cloud Shell, set oci_auth = "ApiKey" and configure
# ~/.oci/config (see README.md).
provider "oci" {
  auth   = var.oci_auth
  region = var.region
}
