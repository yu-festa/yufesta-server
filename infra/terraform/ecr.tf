# ECR: 컨테이너 이미지 저장소. 첫 배포 전에 이 리소스만 먼저 만들어(apply -target) 이미지를 올린다(README)

resource "aws_ecr_repository" "api" {
  name                 = "${local.name}-api"
  image_tag_mutability = "MUTABLE" # latest 태그를 덮어쓰는 배포 방식
  force_delete         = true      # destroy 때 이미지가 남아 있어도 지운다

  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "api" {
  repository = aws_ecr_repository.api.name

  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "최근 10개 이미지만 유지"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 10
      }
      action = { type = "expire" }
    }]
  })
}
