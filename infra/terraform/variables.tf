variable "oci_auth" {
  description = "Provider authentication method. Use 'InstancePrincipal' in OCI Cloud Shell. On a local machine use 'ApiKey' (requires ~/.oci/config)."
  type        = string
  default     = "InstancePrincipal"
}

variable "region" {
  description = "OCI region."
  type        = string
  default     = "sa-bogota-1"
}

variable "compartment_ocid" {
  description = "OCID of the compartment where resources are created (e.g. cmp-lz-prod-linker-5). REQUIRED."
  type        = string
}

variable "ad_number" {
  description = "Availability Domain number to use (1-based)."
  type        = number
  default     = 1
}

variable "project_name" {
  description = "Prefix used to name the resources."
  type        = string
  default     = "linker5"
}

variable "instance_shape" {
  description = "VM shape. E2.1.Micro is Always Free (x86). For ARM use VM.Standard.A1.Flex and set ocpus/memory."
  type        = string
  default     = "VM.Standard.E2.1.Micro"
}

variable "instance_ocpus" {
  description = "OCPUs (Flex shapes only). Leave null for fixed shapes like E2.1.Micro."
  type        = number
  default     = null
}

variable "instance_memory_gbs" {
  description = "Memory in GB (Flex shapes only). Leave null for fixed shapes."
  type        = number
  default     = null
}

variable "ssh_public_key" {
  description = "SSH public key content used to access the VM (use the one in the Vault: sec-linker5-vm-sshpubkey). REQUIRED."
  type        = string
}

variable "repo_url" {
  description = "HTTPS URL of the repository cloned on the VM."
  type        = string
  default     = "https://github.com/co-eiv-devsecops/linker5.git"
}

variable "github_token" {
  description = "GitHub Personal Access Token (read-only, fine-grained) used to clone a PRIVATE repo. Leave empty for a public repo. NOTE: it ends up in the instance user_data, so use a short-lived, minimal-scope token."
  type        = string
  default     = ""
  sensitive   = true
}

variable "repo_branch" {
  description = "Branch to deploy."
  type        = string
  default     = "main"
}

variable "app_port" {
  description = "Port Linker listens on."
  type        = number
  default     = 8080
}

variable "create_network" {
  description = "true = create the full network from scratch (needs VCN/networking permissions). false = reuse an existing subnet (set subnet_ocid). Landing zones usually require false."
  type        = bool
  default     = false
}

variable "subnet_ocid" {
  description = "OCID of an existing subnet to place the VM in (used when create_network = false)."
  type        = string
  default     = ""
}

variable "vcn_cidr" {
  description = "CIDR of the new VCN (different from the existing 10.0.0.0/16 to avoid clashes)."
  type        = string
  default     = "10.1.0.0/16"
}

variable "subnet_cidr" {
  description = "CIDR of the public subnet."
  type        = string
  default     = "10.1.1.0/24"
}

variable "assign_public_ip" {
  description = "Assign a public IP to the VM. Set false when reusing a private subnet that prohibits public IPs."
  type        = bool
  default     = false
}
