# 이미지 저장소: 비공개 S3 버킷 + CloudFront(OAC). 라인업 대표 사진·축제 사진이 여기 올라가고 앱은 CloudFront URL을 응답한다(CLAUDE.md §8).
# 기존 프로젝트의 버킷·배포와의 경계: 버킷은 이름 접두사 yufesta-, 배포는 Project 태그. 배포자 정책이 그 밖의 S3·CloudFront 수정을 거부한다.
# 커스텀 도메인(img.yufesta.com)은 CloudFront가 us-east-1 인증서를 요구해 보류하고 기본 도메인(*.cloudfront.net)을 쓴다

resource "aws_s3_bucket" "images" {
  bucket = var.image_bucket_name
}

# 퍼블릭 접근 전면 차단. 읽기는 아래 버킷 정책으로 CloudFront에만 연다
resource "aws_s3_bucket_public_access_block" "images" {
  bucket                  = aws_s3_bucket.images.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# ACL을 쓰지 않는다. 객체 소유권은 항상 버킷 소유자
resource "aws_s3_bucket_ownership_controls" "images" {
  bucket = aws_s3_bucket.images.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

# OAC: CloudFront가 S3에 서명 요청으로 접근한다. 옛 방식(OAI) 대신 권장되는 방식
resource "aws_cloudfront_origin_access_control" "images" {
  name                              = "${local.name}-images"
  description                       = "${local.name} images bucket"
  origin_access_control_origin_type = "s3"
  signing_behavior                  = "always"
  signing_protocol                  = "sigv4"
}

# AWS 관리형 캐시 정책(쿼리·쿠키·헤더 무시, 기본 TTL 1일). 객체 키에 uuid가 들어가 갱신 시 새 키가 되므로 무효화가 필요 없다
data "aws_cloudfront_cache_policy" "caching_optimized" {
  name = "Managed-CachingOptimized"
}

resource "aws_cloudfront_distribution" "images" {
  enabled      = true
  comment      = "${local.name} images"
  price_class  = "PriceClass_200" # 한국 엣지 포함. PriceClass_100은 북미·유럽뿐이라 한국 요청이 멀리 간다
  http_version = "http2and3"

  origin {
    domain_name              = aws_s3_bucket.images.bucket_regional_domain_name
    origin_id                = "s3-images"
    origin_access_control_id = aws_cloudfront_origin_access_control.images.id
  }

  default_cache_behavior {
    target_origin_id       = "s3-images"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    compress               = true
    cache_policy_id        = data.aws_cloudfront_cache_policy.caching_optimized.id
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}

# 버킷 정책: 이 배포(ARN 일치)만 객체를 읽는다. 다른 배포·직접 URL은 403
data "aws_iam_policy_document" "images_bucket" {
  statement {
    sid       = "AllowOnlyThisCloudFrontDistribution"
    actions   = ["s3:GetObject"]
    resources = ["${aws_s3_bucket.images.arn}/*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "AWS:SourceArn"
      values   = [aws_cloudfront_distribution.images.arn]
    }
  }
}

resource "aws_s3_bucket_policy" "images" {
  bucket = aws_s3_bucket.images.id
  policy = data.aws_iam_policy_document.images_bucket.json

  depends_on = [aws_s3_bucket_public_access_block.images]
}

# 태스크 역할(컨테이너 안의 앱): 이 버킷에 객체 쓰기·삭제만. 읽기는 CloudFront를 거치므로 필요 없다
data "aws_iam_policy_document" "task_images" {
  statement {
    actions   = ["s3:PutObject", "s3:DeleteObject"]
    resources = ["${aws_s3_bucket.images.arn}/*"]
  }
}

resource "aws_iam_role_policy" "task_images" {
  name   = "${local.name}-write-images"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.task_images.json
}
