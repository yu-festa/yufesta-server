output "api_url" {
  value = "https://${local.api_domain}"
}

output "alb_dns_name" {
  description = "ALB 주소. Route 53 alias가 가리키는 대상"
  value       = aws_lb.api.dns_name
}

output "ecr_repository_url" {
  description = "docker tag/push 대상"
  value       = aws_ecr_repository.api.repository_url
}

output "ecs_cluster_name" {
  value = aws_ecs_cluster.main.name
}

output "ecs_service_name" {
  value = aws_ecs_service.api.name
}

output "log_group" {
  description = "aws logs tail <log_group> --follow"
  value       = aws_cloudwatch_log_group.api.name
}

output "rds_endpoint" {
  description = "RDS 주소(프라이빗, VPC 안에서만 접근)"
  value       = aws_db_instance.main.address
}

output "ssm_parameters_to_create" {
  description = "사용자가 apply 전에 CLI로 넣어야 하는 SecureString 파라미터"
  value       = [for key in local.user_managed_secrets : "${var.ssm_prefix}/${key}"]
}

output "image_bucket" {
  description = "이미지 버킷. 앱은 태스크 역할로 여기에 쓴다"
  value       = aws_s3_bucket.images.bucket
}

output "image_cdn_url" {
  description = "이미지 공개 주소(IMAGE_CDN_URL). 응답의 photoUrl이 이 도메인으로 나간다"
  value       = "https://${aws_cloudfront_distribution.images.domain_name}"
}
