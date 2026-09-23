# 보안 그룹 3개가 "누가 누구에게 어떤 포트로" 를 정한다. 인터넷 → ALB(80·443) → ECS(8080) → RDS(3306) 한 방향뿐이다

resource "aws_security_group" "alb" {
  name        = "${local.name}-alb-sg"
  description = "ALB: internet 80/443"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${local.name}-alb-sg" }
}

resource "aws_vpc_security_group_ingress_rule" "alb_https" {
  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

# 80은 받아서 443으로 리다이렉트만 한다(alb.tf)
resource "aws_vpc_security_group_ingress_rule" "alb_http" {
  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 80
  to_port           = 80
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "alb_all" {
  security_group_id = aws_security_group.alb.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_security_group" "ecs" {
  name        = "${local.name}-ecs-sg"
  description = "ECS tasks: 8080 from ALB only"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${local.name}-ecs-sg" }
}

# 공인 IP가 있어도 이 규칙 때문에 ALB 보안 그룹을 통하지 않은 접속은 거부된다
resource "aws_vpc_security_group_ingress_rule" "ecs_from_alb" {
  security_group_id            = aws_security_group.ecs.id
  referenced_security_group_id = aws_security_group.alb.id
  from_port                    = 8080
  to_port                      = 8080
  ip_protocol                  = "tcp"
}

# 나가는 통신은 전부 허용: ECR 이미지 pull, 카카오·구글 토큰 교환, SSM·CloudWatch
resource "aws_vpc_security_group_egress_rule" "ecs_all" {
  security_group_id = aws_security_group.ecs.id
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "-1"
}

resource "aws_security_group" "rds" {
  name        = "${local.name}-rds-sg"
  description = "RDS: 3306 from ECS only"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${local.name}-rds-sg" }
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_ecs" {
  security_group_id            = aws_security_group.rds.id
  referenced_security_group_id = aws_security_group.ecs.id
  from_port                    = 3306
  to_port                      = 3306
  ip_protocol                  = "tcp"
}
