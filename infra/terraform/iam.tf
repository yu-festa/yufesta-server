# ECS가 쓰는 역할 두 개.
# - 태스크 실행 역할: ECS 에이전트가 이미지를 받고 로그를 쓰고 SSM 비밀을 읽어 컨테이너에 넣을 때 쓴다
# - 태스크 역할: 컨테이너 안의 앱이 AWS API를 부를 때 쓴다. 지금은 권한이 없다(사진 S3 업로드는 팀원 도메인에서 추가)

data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "task_execution" {
  name               = "${local.name}-ecs-task-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

# AWS 관리형: ECR pull + CloudWatch Logs 쓰기
resource "aws_iam_role_policy_attachment" "task_execution_managed" {
  role       = aws_iam_role.task_execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_kms_alias" "ssm" {
  name = "alias/aws/ssm"
}

# /yufesta/prod/* 파라미터만 읽을 수 있다. 다른 프로젝트의 비밀은 경로가 달라 접근 불가
data "aws_iam_policy_document" "task_execution_secrets" {
  statement {
    actions   = ["ssm:GetParameters", "ssm:GetParameter"]
    resources = ["arn:aws:ssm:${var.region}:${local.account_id}:parameter${var.ssm_prefix}/*"]
  }

  statement {
    actions   = ["kms:Decrypt"]
    resources = [data.aws_kms_alias.ssm.target_key_arn]
  }
}

resource "aws_iam_role_policy" "task_execution_secrets" {
  name   = "${local.name}-read-ssm-secrets"
  role   = aws_iam_role.task_execution.id
  policy = data.aws_iam_policy_document.task_execution_secrets.json
}

resource "aws_iam_role" "task" {
  name               = "${local.name}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}
