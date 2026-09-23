# RDS MySQL 8.4: 프라이빗 서브넷, Multi-AZ(대기 복제본이 다른 AZ에서 대기, 장애 시 약 1분 내 자동 전환), 자동 백업 7일.
# 스키마는 앱의 Flyway가 첫 기동 때 만든다(V1·V2)

resource "aws_db_subnet_group" "main" {
  name       = "${local.name}-db-subnets"
  subnet_ids = aws_subnet.private[*].id

  tags = { Name = "${local.name}-db-subnets" }
}

# compose의 MySQL과 같은 타임존·문자셋. 회차 시각 비교가 KST 기준이어야 한다(CLAUDE.md §5)
resource "aws_db_parameter_group" "mysql" {
  name   = "${local.name}-mysql84"
  family = "mysql8.4"

  parameter {
    name  = "time_zone"
    value = "Asia/Seoul"
  }

  parameter {
    name  = "character_set_server"
    value = "utf8mb4"
  }

  parameter {
    name  = "collation_server"
    value = "utf8mb4_unicode_ci"
  }
}

resource "aws_db_instance" "main" {
  identifier     = "${local.name}-mysql"
  engine         = "mysql"
  engine_version = "8.4"
  instance_class = var.db_instance_class

  allocated_storage     = 20
  max_allocated_storage = 50 # 꽉 차면 여기까지 자동 확장
  storage_type          = "gp3"
  storage_encrypted     = true

  db_name  = var.db_name
  username = var.db_username
  password = var.db_password

  db_subnet_group_name   = aws_db_subnet_group.main.name
  vpc_security_group_ids = [aws_security_group.rds.id]
  parameter_group_name   = aws_db_parameter_group.mysql.name
  publicly_accessible    = false
  multi_az               = true

  backup_retention_period = 7
  backup_window           = "18:00-19:00"         # UTC = 03:00~04:00 KST
  maintenance_window      = "Mon:19:00-Mon:20:00" # UTC = 화 04:00~05:00 KST, 축제(금) 전 화요일 새벽

  auto_minor_version_upgrade = false # 축제 전 예고 없는 재시작 방지
  apply_immediately          = true
  deletion_protection        = false # 축제 뒤 destroy로 걷어내기 위해
  skip_final_snapshot        = false
  final_snapshot_identifier  = "${local.name}-mysql-final"

  tags = { Name = "${local.name}-mysql" }

  lifecycle {
    ignore_changes = [engine_version] # "8.4"로 생성 후 실제 마이너 버전(8.4.x)과의 차이를 매번 보고하지 않게
  }
}
