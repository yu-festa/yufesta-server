# ElastiCache(Valkey): 응답 캐시 전용. 부하 테스트에서 처리량 천장이 앱 CPU 100%였고(RDS는 31%),
# 요청당 CPU가 Hibernate 매핑 → DTO 변환 → JSON 직렬화에 쓰이므로 직렬화된 응답을 캐시해 그 경로를 건너뛴다(load/README.md).
# 캐시가 죽어도 앱은 DB 경로로 동작한다. 그래서 단일 노드·백업 없음·영속성 없음으로 가장 싸게 둔다

resource "aws_elasticache_subnet_group" "main" {
  name       = "${local.name}-cache-subnets"
  subnet_ids = aws_subnet.private[*].id # RDS와 같은 프라이빗 서브넷. 밖에서 붙을 수 없다
}

resource "aws_security_group" "redis" {
  name        = "${local.name}-redis-sg"
  description = "Redis: 6379 from ECS only"
  vpc_id      = aws_vpc.main.id

  tags = { Name = "${local.name}-redis-sg" }
}

resource "aws_vpc_security_group_ingress_rule" "redis_from_ecs" {
  security_group_id            = aws_security_group.redis.id
  referenced_security_group_id = aws_security_group.ecs.id
  from_port                    = 6379
  to_port                      = 6379
  ip_protocol                  = "tcp"
}

# 캐시 전용이므로 메모리가 차면 오래된 키부터 버린다(allkeys-lru). 데이터를 잃어도 DB에서 다시 만들 수 있다
resource "aws_elasticache_parameter_group" "main" {
  name   = "${local.name}-cache-params"
  family = "valkey8"

  parameter {
    name  = "maxmemory-policy"
    value = "allkeys-lru"
  }
}

resource "aws_elasticache_replication_group" "main" {
  replication_group_id = "${local.name}-cache"
  description          = "${local.name} response cache"

  engine         = "valkey" # Redis OSS와 프로토콜 호환. 같은 스펙에서 더 싸다
  engine_version = "8.0"
  node_type      = var.redis_node_type
  port           = 6379

  num_cache_clusters         = 1     # 캐시가 죽어도 DB 경로로 동작하므로 이중화하지 않는다
  automatic_failover_enabled = false # 노드가 하나라 불가
  multi_az_enabled           = false

  subnet_group_name    = aws_elasticache_subnet_group.main.name
  security_group_ids   = [aws_security_group.redis.id]
  parameter_group_name = aws_elasticache_parameter_group.main.name

  # 캐시에는 개인정보를 넣지 않지만(공개 응답만) VPC 밖으로 나가지 않도록 기본값 유지
  transit_encryption_enabled = false
  at_rest_encryption_enabled = false

  snapshot_retention_limit   = 0 # 백업 없음. 캐시라 복구할 것이 없다
  apply_immediately          = true
  auto_minor_version_upgrade = false # 축제 전 예고 없는 재시작 방지

  maintenance_window = "mon:19:00-mon:20:00" # UTC = 화 04:00~05:00 KST

  tags = { Name = "${local.name}-cache" }
}
