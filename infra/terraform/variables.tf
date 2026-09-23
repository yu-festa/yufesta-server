variable "region" {
  description = "리전. 소마 계정의 기존 인프라와 같은 서울 리전이지만 VPC를 새로 만들어 분리한다"
  type        = string
  default     = "ap-northeast-2"
}

variable "availability_zones" {
  description = "사용할 가용 영역 2개. 2b는 일부 인스턴스 타입(t4g RDS 등)을 지원하지 않아 a·c를 쓴다"
  type        = list(string)
  default     = ["ap-northeast-2a", "ap-northeast-2c"]
}

variable "name_prefix" {
  description = "모든 리소스 이름의 접두사. IAM 정책이 yufesta-* 이름만 허용한다"
  type        = string
  default     = "yufesta"
}

variable "vpc_cidr" {
  description = "새 VPC 대역. 기존 VPC와 겹쳐도 피어링하지 않으므로 무관하다"
  type        = string
  default     = "10.20.0.0/16"
}

variable "domain_name" {
  description = "Route 53에 호스팅 존이 있는 등록 도메인"
  type        = string
  default     = "yufesta.com"
}

variable "api_subdomain" {
  description = "API 호스트명. api.yufesta.com"
  type        = string
  default     = "api"
}

variable "frontend_url" {
  description = "로그인 후 리다이렉트 기준 프론트 주소(FRONTEND_URL). 쿠키 공유를 위해 API와 같은 등록 도메인이어야 한다"
  type        = string
  default     = "https://yufesta.com"
}

variable "allowed_origins" {
  description = "CORS 허용 origin 목록(ALLOWED_ORIGINS). 운영 도메인과 Vercel 프리뷰"
  type        = list(string)
  default     = ["https://yufesta.com", "https://yufesta-web.vercel.app"]
}

variable "cookie_domain" {
  description = "access_token·XSRF-TOKEN 쿠키 domain(AUTH_COOKIE_DOMAIN). 프론트가 API 쿠키를 읽으려면 상위 도메인이어야 한다"
  type        = string
  default     = ".yufesta.com"
}

variable "ssm_prefix" {
  description = "비밀값을 두는 SSM Parameter Store 경로. 태스크 실행 역할은 이 경로만 읽을 수 있다"
  type        = string
  default     = "/yufesta/prod"
}

variable "image_tag" {
  description = "ECS가 실행할 이미지 태그. 배포는 같은 태그를 다시 push하고 서비스를 강제 재배포하는 방식"
  type        = string
  default     = "latest"
}

variable "cpu_architecture" {
  description = "Fargate CPU 아키텍처. Apple Silicon에서 docker build한 이미지는 ARM64이며 Graviton이 약 20% 저렴하다"
  type        = string
  default     = "ARM64"
}

variable "task_cpu" {
  description = "태스크 CPU 단위(1024 = 1 vCPU)"
  type        = number
  default     = 512
}

variable "task_memory" {
  description = "태스크 메모리(MB). JVM은 이 값의 70%를 힙 상한으로 쓴다(Dockerfile)"
  type        = number
  default     = 1024
}

variable "desired_count" {
  description = "동시에 유지할 태스크 수. 2면 한 대가 죽거나 배포 중이어도 서비스가 이어진다"
  type        = number
  default     = 2
}

variable "db_instance_class" {
  description = "RDS 인스턴스 클래스"
  type        = string
  default     = "db.t4g.micro"
}

variable "db_name" {
  type    = string
  default = "yufesta"
}

variable "db_username" {
  type    = string
  default = "yufesta"
}

variable "db_password" {
  description = "RDS 마스터 비밀번호. 파일에 쓰지 말고 `export TF_VAR_db_password=...`로 넘긴다. 같은 값이 SSM에도 저장돼 앱이 읽는다"
  type        = string
  sensitive   = true
}

variable "alarm_email" {
  description = "CloudWatch 알람을 받을 이메일. 첫 apply 후 확인 메일의 링크를 눌러야 구독이 활성화된다"
  type        = string
}
