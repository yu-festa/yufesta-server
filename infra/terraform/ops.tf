# 운영 DB 접속 경로. RDS는 프라이빗 서브넷이라 밖에서 붙을 수 없으므로, 필요할 때만 mysql 클라이언트 태스크를 띄우고
# ECS Exec(SSM 세션)으로 그 안에서 mysql을 연다. 회원 삭제·운영자 지정·긴급 SQL용이며 평소에는 실행 중인 태스크가 없다(비용 0)

data "aws_iam_policy_document" "ecs_exec" {
  statement {
    actions = [
      "ssmmessages:CreateControlChannel",
      "ssmmessages:CreateDataChannel",
      "ssmmessages:OpenControlChannel",
      "ssmmessages:OpenDataChannel",
    ]
    resources = ["*"]
  }
}

# ECS Exec은 태스크 역할로 SSM 채널을 연다. API 태스크 역할과 분리해 API 컨테이너에는 이 권한을 주지 않는다
resource "aws_iam_role" "dbshell" {
  name               = "${local.name}-dbshell"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

resource "aws_iam_role_policy" "dbshell_exec" {
  name   = "${local.name}-dbshell-exec"
  role   = aws_iam_role.dbshell.id
  policy = data.aws_iam_policy_document.ecs_exec.json
}

resource "aws_ecs_task_definition" "dbshell" {
  family                   = "${local.name}-dbshell"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.task_execution.arn # SSM 비밀 읽기·로그 권한 재사용
  task_role_arn            = aws_iam_role.dbshell.arn
  skip_destroy             = true

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }

  container_definitions = jsonencode([{
    name      = "dbshell"
    image     = "public.ecr.aws/docker/library/mysql:8.4" # 공식 이미지의 public ECR 미러(Docker Hub 제한 회피)
    essential = true
    command   = ["sleep", "3600"] # 셸을 열 시간. 1시간 뒤 스스로 종료돼 잊어도 비용이 쌓이지 않는다

    environment = [
      { name = "DB_HOST", value = aws_db_instance.main.address },
      { name = "DB_USER", value = var.db_username },
      { name = "DB_NAME", value = var.db_name },
    ]
    secrets = [{ name = "DB_PASSWORD", valueFrom = aws_ssm_parameter.db_password.arn }]

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.api.name
        "awslogs-region"        = var.region
        "awslogs-stream-prefix" = "dbshell"
      }
    }
  }])
}

# run-task에 그대로 넣는 네트워크 설정. 퍼블릭 서브넷 + 공인 IP(이미지 pull), ECS 보안 그룹(RDS 3306 허용 대상)
output "dbshell_network" {
  value = "awsvpcConfiguration={subnets=[${join(",", aws_subnet.public[*].id)}],securityGroups=[${aws_security_group.ecs.id}],assignPublicIp=ENABLED}"
}
