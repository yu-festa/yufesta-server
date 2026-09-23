# 네트워크: 새 VPC 하나에 퍼블릭 서브넷 2개(ALB·ECS)와 프라이빗 서브넷 2개(RDS)를 가용 영역 a·c에 나눠 만든다.
# NAT 게이트웨이는 두지 않는다. ECS는 퍼블릭 서브넷의 공인 IP로 ECR·카카오·구글에 나가고, 들어오는 길은 보안 그룹이 ALB로만 제한한다

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "${local.name}-vpc" }
}

# 인터넷 게이트웨이: 퍼블릭 서브넷이 인터넷과 통하는 문
resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "${local.name}-igw" }
}

# 퍼블릭 서브넷: 10.20.0.0/24(a), 10.20.1.0/24(c). 여기 뜨는 ECS 태스크는 공인 IP를 받는다
resource "aws_subnet" "public" {
  count = length(var.availability_zones)

  vpc_id                  = aws_vpc.main.id
  cidr_block              = cidrsubnet(var.vpc_cidr, 8, count.index)
  availability_zone       = var.availability_zones[count.index]
  map_public_ip_on_launch = true

  tags = { Name = "${local.name}-public-${substr(var.availability_zones[count.index], -1, 1)}", Tier = "public" }
}

# 프라이빗 서브넷: 10.20.10.0/24(a), 10.20.11.0/24(c). 인터넷 경로가 없어 RDS는 밖에서 닿을 수 없다
resource "aws_subnet" "private" {
  count = length(var.availability_zones)

  vpc_id            = aws_vpc.main.id
  cidr_block        = cidrsubnet(var.vpc_cidr, 8, 10 + count.index)
  availability_zone = var.availability_zones[count.index]

  tags = { Name = "${local.name}-private-${substr(var.availability_zones[count.index], -1, 1)}", Tier = "private" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "${local.name}-public-rt" }
}

resource "aws_route_table_association" "public" {
  count = length(aws_subnet.public)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

# 프라이빗 라우트 테이블에는 일부러 기본 경로(0.0.0.0/0)를 넣지 않는다. VPC 내부 통신만 가능
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "${local.name}-private-rt" }
}

resource "aws_route_table_association" "private" {
  count = length(aws_subnet.private)

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}
