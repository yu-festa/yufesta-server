# ECS Fargate: 클러스터(묶음) → 태스크 정의(무엇을 어떻게 실행할지) → 서비스(몇 개를 유지하고 ALB에 어떻게 붙일지)

resource "aws_ecs_cluster" "main" {
  name = "${local.name}-cluster"

  setting {
    name  = "containerInsights"
    value = "disabled" # 상세 메트릭은 비용이 들어 끈다. 알람은 ALB·RDS 메트릭으로 충분
  }
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/${local.name}-api"
  retention_in_days = 14
}

locals {
  # compose.yml의 environment와 같은 역할. 비밀이 아닌 값만 여기, 비밀은 아래 secrets
  container_environment = [
    { name = "SPRING_PROFILES_ACTIVE", value = "prod" },
    { name = "DB_URL", value = "jdbc:mysql://${aws_db_instance.main.address}:3306/${var.db_name}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8" },
    { name = "DB_USERNAME", value = var.db_username },
    { name = "FRONTEND_URL", value = var.frontend_url },
    { name = "ALLOWED_ORIGINS", value = join(",", var.allowed_origins) },
    { name = "AUTH_COOKIE_DOMAIN", value = var.cookie_domain },
    { name = "SWAGGER_ENABLED", value = tostring(var.swagger_enabled) },
  ]

  # ECS 에이전트가 시작 시 SSM에서 값을 꺼내 환경변수로 넣는다. 콘솔·로그에는 ARN만 보인다
  container_secrets = concat(
    [{ name = "DB_PASSWORD", valueFrom = aws_ssm_parameter.db_password.arn }],
    [for key, arn in local.secret_arns : { name = key, valueFrom = arn }]
  )
}

resource "aws_ecs_task_definition" "api" {
  family                   = "${local.name}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.task_cpu
  memory                   = var.task_memory
  execution_role_arn       = aws_iam_role.task_execution.arn
  task_role_arn            = aws_iam_role.task.arn

  # 리비전을 바꿀 때 옛 리비전을 Deregister하지 않는다. 이 API는 리소스 ARN 없이 호출돼 배포자 정책의 태그 Deny에 걸리고,
  # 남은 리비전은 비용이 없다
  skip_destroy = true

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = var.cpu_architecture
  }

  container_definitions = jsonencode([{
    name      = "api"
    image     = "${aws_ecr_repository.api.repository_url}:${var.image_tag}"
    essential = true

    portMappings = [{ containerPort = 8080, protocol = "tcp" }]
    environment  = local.container_environment
    secrets      = local.container_secrets
    stopTimeout  = 30 # Spring graceful shutdown이 진행 중인 요청을 마칠 시간

    logConfiguration = {
      logDriver = "awslogs"
      options = {
        "awslogs-group"         = aws_cloudwatch_log_group.api.name
        "awslogs-region"        = var.region
        "awslogs-stream-prefix" = "api"
      }
    }
  }])
}

resource "aws_ecs_service" "api" {
  name             = "${local.name}-api"
  cluster          = aws_ecs_cluster.main.id
  task_definition  = aws_ecs_task_definition.api.arn
  desired_count    = var.desired_count
  launch_type      = "FARGATE"
  platform_version = "LATEST"
  propagate_tags   = "SERVICE"

  # 롤링 배포: 새 태스크 2개를 추가로 띄워 건강해지면 옛 태스크를 내린다(최소 100%, 최대 200%)
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  # 새 버전이 계속 헬스체크에 실패하면 배포를 멈추고 이전 버전으로 자동 롤백
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  # 기동(JVM + Flyway) 동안 헬스체크 실패를 장애로 보지 않는 유예
  health_check_grace_period_seconds = 120

  network_configuration {
    subnets          = aws_subnet.public[*].id
    security_groups  = [aws_security_group.ecs.id]
    assign_public_ip = true # NAT 없이 ECR·외부 API로 나가기 위해. 들어오는 건 보안 그룹이 막는다
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "api"
    container_port   = 8080
  }

  depends_on = [aws_lb_listener.https, aws_iam_role_policy.task_execution_secrets]
}
