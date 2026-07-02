locals {
  # Prefer the public IP; fall back to the private IP when the VM has no public IP.
  access_ip = oci_core_instance.linker.public_ip != "" ? oci_core_instance.linker.public_ip : oci_core_instance.linker.private_ip
}

output "instance_public_ip" {
  description = "Public IP of the Linker VM (empty if the subnet is private)."
  value       = oci_core_instance.linker.public_ip
}

output "instance_private_ip" {
  description = "Private IP of the Linker VM."
  value       = oci_core_instance.linker.private_ip
}

output "ssh_command" {
  description = "Ready-to-use SSH command (needs the matching private key)."
  value       = "ssh ubuntu@${local.access_ip}"
}

output "app_url" {
  description = "URL where Linker is served once cloud-init finishes (~2-4 min after boot)."
  value       = "http://${local.access_ip}:${var.app_port}/"
}
