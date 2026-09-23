# 자격 증명은 코드에 두지 않는다. 실행 전 `export AWS_PROFILE=yufesta`(전용 IAM 사용자 프로필).
# default_tags: 모든 리소스에 Project=yufesta가 붙는다. IAM 정책이 이 태그가 없는 리소스의 수정·삭제를 거부하므로 기존 인프라와의 경계선이다
provider "aws" {
  region = var.region

  default_tags {
    tags = {
      Project     = "yufesta"
      Environment = "prod"
      ManagedBy   = "terraform"
    }
  }
}

data "aws_caller_identity" "current" {}

locals {
  name       = var.name_prefix
  account_id = data.aws_caller_identity.current.account_id
  api_domain = "${var.api_subdomain}.${var.domain_name}"
}
