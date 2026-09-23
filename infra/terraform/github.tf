# GitHub Actions가 액세스 키 없이 AWS를 쓰기 위한 OIDC 연동.
# 워크플로가 GitHub의 서명 토큰을 제시하면 STS가 yufesta-github-deploy 역할의 임시 자격 증명을 내준다.
# 신뢰 조건이 "이 저장소의 main 브랜치"로 고정되어 다른 저장소·브랜치는 역할을 빌릴 수 없다

variable "github_repository" {
  description = "OIDC 역할을 빌릴 수 있는 저장소(owner/name)"
  type        = string
  default     = "yu-festa/yufesta-server"
}

variable "deploy_branch" {
  description = "배포 트리거 브랜치. 이 브랜치의 워크플로만 역할을 assume할 수 있다"
  type        = string
  default     = "main"
}

variable "create_github_oidc_provider" {
  description = "계정에 GitHub OIDC 공급자가 이미 있으면(apply에서 EntityAlreadyExists) false로 바꿔 기존 것을 참조한다"
  type        = bool
  default     = true
}

locals {
  github_oidc_url = "https://token.actions.githubusercontent.com"
  github_oidc_arn = var.create_github_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : data.aws_iam_openid_connect_provider.github[0].arn
}

# 계정당 하나만 존재할 수 있는 공급자. AWS가 GitHub의 인증서를 직접 검증하므로 thumbprint는 형식상 값이다
resource "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 1 : 0

  url             = local.github_oidc_url
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = ["6938fd4d98bab03faadb97b34396831e3780aea1", "1c58a3a8518e8759bf075b76b750d4f2df264fcd"]
}

data "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 0 : 1

  url = local.github_oidc_url
}

data "aws_iam_policy_document" "github_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.github_oidc_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    # sub 클레임: repo:<owner/name>:ref:refs/heads/<branch>. push와 workflow_dispatch 모두 이 형태
    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:sub"
      values   = ["repo:${var.github_repository}:ref:refs/heads/${var.deploy_branch}"]
    }
  }
}

resource "aws_iam_role" "github_deploy" {
  name               = "${local.name}-github-deploy"
  assume_role_policy = data.aws_iam_policy_document.github_assume.json
}

# 배포에 필요한 최소 권한: 우리 저장소에 이미지 push, 우리 서비스 재배포·상태 조회
data "aws_iam_policy_document" "github_deploy" {
  statement {
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:InitiateLayerUpload",
      "ecr:UploadLayerPart",
      "ecr:CompleteLayerUpload",
      "ecr:PutImage",
      "ecr:BatchGetImage",
      "ecr:DescribeImages",
    ]
    resources = [aws_ecr_repository.api.arn]
  }

  statement {
    actions   = ["ecs:UpdateService", "ecs:DescribeServices"]
    resources = [aws_ecs_service.api.id]
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "${local.name}-github-deploy"
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.github_deploy.json
}

output "github_deploy_role_arn" {
  description = "GitHub 저장소 Variables의 AWS_DEPLOY_ROLE_ARN에 넣는 값"
  value       = aws_iam_role.github_deploy.arn
}
