# 도메인·인증서: Route 53의 yufesta.com 존은 도메인 구매 시 자동 생성되므로 data로 읽기만 한다(우리가 만들거나 지우지 않는다).
# ACM 인증서는 DNS 검증용 CNAME을 존에 넣어 자동 발급받고, api.yufesta.com은 ALB를 가리키는 alias 레코드다

data "aws_route53_zone" "main" {
  name = var.domain_name
}

resource "aws_acm_certificate" "api" {
  domain_name       = local.api_domain
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_route53_record" "acm_validation" {
  for_each = {
    for dvo in aws_acm_certificate.api.domain_validation_options : dvo.domain_name => {
      name   = dvo.resource_record_name
      record = dvo.resource_record_value
      type   = dvo.resource_record_type
    }
  }

  zone_id         = data.aws_route53_zone.main.zone_id
  name            = each.value.name
  type            = each.value.type
  ttl             = 60
  records         = [each.value.record]
  allow_overwrite = true
}

# 검증이 끝날 때까지(보통 수 분) 기다린 뒤 리스너가 인증서를 쓴다
resource "aws_acm_certificate_validation" "api" {
  certificate_arn         = aws_acm_certificate.api.arn
  validation_record_fqdns = [for record in aws_route53_record.acm_validation : record.fqdn]
}

resource "aws_route53_record" "api" {
  zone_id = data.aws_route53_zone.main.zone_id
  name    = local.api_domain
  type    = "A"

  alias {
    name                   = aws_lb.api.dns_name
    zone_id                = aws_lb.api.zone_id
    evaluate_target_health = true
  }
}
