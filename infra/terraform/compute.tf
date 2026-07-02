locals {
  # If a GitHub token is provided, inject it into the HTTPS clone URL (private repo).
  # Otherwise clone the URL as-is (public repo).
  clone_url = var.github_token != "" ? replace(var.repo_url, "https://", "https://${var.github_token}@") : var.repo_url
}

# Availability Domains in the tenancy
data "oci_identity_availability_domains" "ads" {
  compartment_id = var.compartment_ocid
}

# Latest Ubuntu 24.04 image compatible with the chosen shape (auto = repeatable across regions).
# Skipped when image_ocid is set explicitly (e.g. when the compartment can't list images).
data "oci_core_images" "ubuntu" {
  count                    = var.image_ocid == "" ? 1 : 0
  compartment_id           = var.compartment_ocid
  operating_system         = "Canonical Ubuntu"
  operating_system_version = "24.04"
  shape                    = var.instance_shape
  sort_by                  = "TIMECREATED"
  sort_order               = "DESC"
}

resource "oci_core_instance" "linker" {
  compartment_id      = var.compartment_ocid
  availability_domain = data.oci_identity_availability_domains.ads.availability_domains[var.ad_number - 1].name
  display_name        = "${var.project_name}-app"
  shape               = var.instance_shape

  # shape_config only applies to Flex shapes (when ocpus/memory are set)
  dynamic "shape_config" {
    for_each = var.instance_ocpus != null ? [1] : []
    content {
      ocpus         = var.instance_ocpus
      memory_in_gbs = var.instance_memory_gbs
    }
  }

  create_vnic_details {
    subnet_id        = local.subnet_id
    assign_public_ip = var.assign_public_ip
    display_name     = "${var.project_name}-vnic"
  }

  source_details {
    source_type = "image"
    source_id   = var.image_ocid != "" ? var.image_ocid : try(data.oci_core_images.ubuntu[0].images[0].id, null)
  }

  metadata = {
    ssh_authorized_keys = var.ssh_public_key
    user_data = base64encode(templatefile("${path.module}/cloud-init.yaml", {
      clone_url   = local.clone_url
      repo_branch = var.repo_branch
      app_port    = var.app_port
    }))
  }
}
