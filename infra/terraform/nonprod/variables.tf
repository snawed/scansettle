variable "aws_region" {
  description = "AWS region for the non-prod environment."
  type        = string
  default     = "eu-west-2"
}

variable "instance_type" {
  description = "EC2 instance type. t3.micro is free-tier eligible on most accounts; use t2.micro if your account's free tier predates t3 support."
  type        = string
  default     = "t3.micro"
}

variable "root_volume_size_gb" {
  description = "Root EBS volume size — well within the 30GB free-tier allowance."
  type        = number
  default     = 20
}

variable "app_ports" {
  description = "Ports the security group opens to the internet for direct testing access (no domain/ALB in front for this environment)."
  type        = list(number)
  default     = [3000, 8080]
}

variable "tf_state_bucket" {
  description = "The S3 bucket infra/aws-bootstrap/bootstrap.sh created — reused for deploy artifacts (compose file + deploy script) under a deploy-artifacts/ prefix, rather than provisioning a second bucket."
  type        = string
}
