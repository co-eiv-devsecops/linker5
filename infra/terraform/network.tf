# Network created from scratch ONLY when create_network = true.
# In a locked-down landing zone (no VCN permissions), set create_network = false
# and pass an existing subnet via subnet_ocid instead.

resource "oci_core_vcn" "vcn" {
  count          = var.create_network ? 1 : 0
  compartment_id = var.compartment_ocid
  cidr_blocks    = [var.vcn_cidr]
  display_name   = "${var.project_name}-vcn"
  dns_label      = "linkervcn"
}

resource "oci_core_internet_gateway" "ig" {
  count          = var.create_network ? 1 : 0
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.vcn[0].id
  display_name   = "${var.project_name}-ig"
  enabled        = true
}

resource "oci_core_route_table" "rt" {
  count          = var.create_network ? 1 : 0
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.vcn[0].id
  display_name   = "${var.project_name}-rt"

  route_rules {
    destination       = "0.0.0.0/0"
    network_entity_id = oci_core_internet_gateway.ig[0].id
  }
}

resource "oci_core_security_list" "sl" {
  count          = var.create_network ? 1 : 0
  compartment_id = var.compartment_ocid
  vcn_id         = oci_core_vcn.vcn[0].id
  display_name   = "${var.project_name}-sl"

  egress_security_rules {
    destination = "0.0.0.0/0"
    protocol    = "all"
  }

  # SSH
  ingress_security_rules {
    protocol = "6" # TCP
    source   = "0.0.0.0/0"
    tcp_options {
      min = 22
      max = 22
    }
  }

  # HTTP (in case it is served on port 80 behind a proxy)
  ingress_security_rules {
    protocol = "6"
    source   = "0.0.0.0/0"
    tcp_options {
      min = 80
      max = 80
    }
  }

  # Application port
  ingress_security_rules {
    protocol = "6"
    source   = "0.0.0.0/0"
    tcp_options {
      min = var.app_port
      max = var.app_port
    }
  }
}

resource "oci_core_subnet" "subnet" {
  count                      = var.create_network ? 1 : 0
  compartment_id             = var.compartment_ocid
  vcn_id                     = oci_core_vcn.vcn[0].id
  cidr_block                 = var.subnet_cidr
  display_name               = "${var.project_name}-subnet"
  dns_label                  = "linkersubnet"
  route_table_id             = oci_core_route_table.rt[0].id
  security_list_ids          = [oci_core_security_list.sl[0].id]
  prohibit_public_ip_on_vnic = !var.assign_public_ip
}

locals {
  # Use the created subnet when create_network = true, otherwise the provided one.
  subnet_id = var.create_network ? oci_core_subnet.subnet[0].id : var.subnet_ocid
}
