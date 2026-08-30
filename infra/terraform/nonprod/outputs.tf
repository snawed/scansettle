output "instance_id" {
  description = "EC2 instance ID — target for `aws ssm send-command` deploys."
  value       = aws_instance.app.id
}

output "public_ip" {
  description = "Elastic IP — hit http://<this>:3000 (web) and :8080 (api/actuator)."
  value       = aws_eip.app.public_ip
}

output "ecr_api_repository_url" {
  value = aws_ecr_repository.api.repository_url
}

output "ecr_web_repository_url" {
  value = aws_ecr_repository.web.repository_url
}
