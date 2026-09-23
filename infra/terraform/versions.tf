# Terraform·프로바이더 버전과 state 저장 위치.
# state는 S3(버킷은 콘솔에서 먼저 생성)에 두고 use_lockfile로 동시 실행을 막는다. state에는 RDS 주소·비밀번호가 들어가므로 git에 올리지 않는다
terraform {
  required_version = ">= 1.10"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.0"
    }
  }

  backend "s3" {
    bucket       = "yufesta-tfstate"
    key          = "prod/terraform.tfstate"
    region       = "ap-northeast-2"
    use_lockfile = true
    encrypt      = true
  }
}
