# 비밀값은 SSM Parameter Store(SecureString)에 두고 태스크 정의의 secrets로 주입한다. 코드·이미지·state에는 값이 남지 않는다.
# JWT_SECRET, KAKAO_*, GOOGLE_*, VAPID_* 8개는 사용자가 CLI로 직접 넣는다(README). DB 비밀번호만 RDS 생성에 필요해 변수로 받아 같은 값을 파라미터로 만든다

resource "aws_ssm_parameter" "db_password" {
  name  = "${var.ssm_prefix}/DB_PASSWORD"
  type  = "SecureString"
  value = var.db_password
}

locals {
  # 사용자가 미리 넣어 둔 파라미터의 ARN. data로 읽으면 값이 state에 남으므로 ARN만 조립한다
  user_managed_secrets = [
    "JWT_SECRET", "KAKAO_CLIENT_ID", "KAKAO_CLIENT_SECRET", "GOOGLE_CLIENT_ID", "GOOGLE_CLIENT_SECRET",
    "VAPID_PUBLIC_KEY", "VAPID_PRIVATE_KEY", "VAPID_SUBJECT"
  ]

  secret_arns = {
    for key in local.user_managed_secrets :
    key => "arn:aws:ssm:${var.region}:${local.account_id}:parameter${var.ssm_prefix}/${key}"
  }
}
