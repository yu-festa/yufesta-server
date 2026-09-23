# ALB: 인터넷에서 오는 HTTPS를 받아 TLS를 끝내고 ECS 태스크의 8080으로 나눠 보낸다.
# 헬스체크 /actuator/health가 200이 아닌 태스크는 트래픽에서 제외되고, ECS 서비스가 교체한다

resource "aws_lb" "api" {
  name                       = "${local.name}-alb"
  load_balancer_type         = "application"
  security_groups            = [aws_security_group.alb.id]
  subnets                    = aws_subnet.public[*].id
  idle_timeout               = 60
  drop_invalid_header_fields = true

  tags = { Name = "${local.name}-alb" }
}

resource "aws_lb_target_group" "api" {
  name                 = "${local.name}-api-tg"
  port                 = 8080
  protocol             = "HTTP"
  target_type          = "ip" # Fargate 태스크는 IP로 등록된다
  vpc_id               = aws_vpc.main.id
  deregistration_delay = 30 # 배포 시 옛 태스크가 진행 중인 요청을 마칠 시간

  health_check {
    path                = "/actuator/health"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = { Name = "${local.name}-api-tg" }
}

resource "aws_lb_listener" "https" {
  load_balancer_arn = aws_lb.api.arn
  port              = 443
  protocol          = "HTTPS"
  ssl_policy        = "ELBSecurityPolicy-TLS13-1-2-2021-06"
  certificate_arn   = aws_acm_certificate_validation.api.certificate_arn

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api.arn
  }
}

# http://api.yufesta.com 으로 오면 https로 301
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.api.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"

    redirect {
      port        = "443"
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}
