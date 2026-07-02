output "instance_public_ip" {
  description = "Public IP of the Linker VM."
  value       = oci_core_instance.linker.public_ip
}

output "instance_private_ip" {
  description = "Private IP of the Linker VM."
  value       = oci_core_instance.linker.private_ip
}

output "ssh_command" {
  description = "Ready-to-use SSH command (needs the matching private key)."
  value       = "ssh ubuntu@${oci_core_instance.linker.public_ip}"
}

output "app_url" {
  description = "URL where Linker is served once cloud-init finishes (~2-4 min after boot)."
  value       = "http://${oci_core_instance.linker.public_ip}:${var.app_port}/"
}
