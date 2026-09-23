# 인프라 (AWS, Terraform)

소마 계정 서울 리전에 **yufesta 전용 리소스만** 만든다. 설계도: Figma "YU 가을 축제" > AWS 아키텍처 페이지.
기존 운영 인프라는 조회도 변경도 하지 않는다. 전용 IAM 사용자의 정책(`iam/yufesta-deployer-allow.json`·`iam/yufesta-deployer-deny.json`)이
`Project=yufesta` 태그가 없는 리소스의 수정·삭제를 거부하고, Terraform은 자기 state에 있는 것만 관리한다.

## 0. 한 번만 하는 준비 (콘솔·CLI, 사람이 직접)

| 순서 | 할 일 | 어디서 |
|---|---|---|
| 1 | 콘솔 우상단 계정 ID가 소마 계정인지 확인 | 콘솔 |
| 2 | 도메인 `yufesta.com` 구매(호스팅 존 자동 생성). 승인 메일 확인. 존의 **Hosted zone ID** 메모 | Route 53 > Registered domains |
| 3 | `iam/yufesta-deployer-allow.json`·`iam/yufesta-deployer-deny.json`의 `<HOSTED_ZONE_ID>`를 2의 값으로 바꾼다 | 편집기 |
| 4 | IAM > Policies에서 고객 관리형 정책 2개 생성(`yufesta-deployer-allow`, `yufesta-deployer-deny`, 각 JSON 붙여넣기. 인라인 정책은 2,048자 제한이라 안 됨) → IAM 사용자 `yufesta-deployer` 생성 시 두 정책 연결 → 액세스 키(CLI용) 발급 | IAM > Policies, Users |
| 5 | `aws configure --profile yufesta` 로 키 입력(리전 `ap-northeast-2`, 출력 `json`). 기존 프로필은 건드리지 않는다 | 터미널 |
| 6 | `export AWS_PROFILE=yufesta && aws sts get-caller-identity` → Account가 소마 계정, Arn이 `user/yufesta-deployer` | 터미널 |
| 7 | S3 버킷 `yufesta-tfstate`(서울, 버전 관리 켬, 퍼블릭 차단). 이름이 이미 쓰이면 `yufesta-tfstate-<임의>`로 만들고 `terraform/versions.tf`·정책 JSON의 버킷 이름을 같이 바꾼다 | S3 |
| 8 | 비밀 5개를 SSM에 넣는다(아래 명령). 값은 로컬 `.env`의 것과 같다 | 터미널 |
| 9 | 카카오·구글 개발자 콘솔에 Redirect URI `https://api.yufesta.com/login/oauth2/code/kakao`, `.../google` 추가 | 각 콘솔 |
| 10 | 팀원에게 Vercel 커스텀 도메인 `yufesta.com` 연결 요청(Route 53에 Vercel이 알려주는 A/CNAME 레코드 추가) | Vercel |

```bash
export AWS_PROFILE=yufesta
for KEY in JWT_SECRET KAKAO_CLIENT_ID KAKAO_CLIENT_SECRET GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET; do
  read -rs "VALUE?$KEY: "; echo
  aws ssm put-parameter --name "/yufesta/prod/$KEY" --type SecureString --value "$VALUE" --overwrite >/dev/null
done
aws ssm describe-parameters --parameter-filters Key=Name,Option=BeginsWith,Values=/yufesta/prod/ --query 'Parameters[].Name'
```

## 1. 첫 배포

```bash
cd infra/terraform
cp terraform.tfvars.example terraform.tfvars   # alarm_email 채우기
export AWS_PROFILE=yufesta
export TF_VAR_db_password='<RDS 비밀번호, 16자 이상>'

terraform init                                  # S3 backend 연결, 프로바이더 설치
terraform apply -target=aws_ecr_repository.api  # 1) 이미지 저장소부터
```

이미지를 올린다(Apple Silicon의 `docker build`는 ARM64 이미지라 Fargate ARM64와 맞다).

```bash
ECR=$(terraform output -raw ecr_repository_url)
aws ecr get-login-password | docker login --username AWS --password-stdin "${ECR%%/*}"
docker build -t yufesta-api ../..            # 빌드 컨텍스트는 저장소 루트
docker tag yufesta-api:latest "${ECR}:latest"   # zsh에서 "$ECR:latest"는 :l 수식어로 읽히므로 중괄호 필수
docker push "${ECR}:latest"
```

나머지 전부(VPC → ALB·인증서 → RDS → ECS). RDS 생성 때문에 10~15분 걸린다.

```bash
terraform plan -out plan.tfplan   # 생성 N개, 변경 0, 삭제 0 인지 확인. 기존 리소스가 계획에 나오면 중단
terraform apply plan.tfplan
```

## 2. 확인

```bash
terraform output
curl -i https://api.yufesta.com/actuator/health      # {"status":"UP"}
curl -i http://api.yufesta.com/actuator/health       # 301 → https
aws logs tail /ecs/yufesta-api --follow              # Flyway "Successfully applied 2 migrations", Started
aws ecs describe-services --cluster yufesta-cluster --services yufesta-api --query 'services[0].[runningCount,desiredCount]'
```

- 브라우저: `https://api.yufesta.com/oauth2/authorization/kakao?redirect=/` → 카카오 로그인 → `https://yufesta.com/`로 복귀, `access_token` 쿠키 domain `.yufesta.com`
- 알람 구독: 이메일로 온 "AWS Notification - Subscription Confirmation"의 링크 클릭
- 운영자 지정: RDS는 프라이빗이라 밖에서 접속이 안 된다. `admin.allowlist`는 ECS Exec 또는 임시 배스천 없이 하려면
  로컬에서 `docker compose`로 같은 SQL을 만든 뒤, 운영자 후보가 첫 로그인을 하기 **전에** 다음 PR에서 붙일 운영자 API로 넣는다(TODO)

## 3. 배포 갱신·운영

```bash
docker build -t yufesta-api ../.. && docker tag yufesta-api:latest "${ECR}:latest" && docker push "${ECR}:latest"
aws ecs update-service --cluster yufesta-cluster --service yufesta-api --force-new-deployment
```
롤링 배포(새 2개 → 건강 확인 → 옛 2개 종료). 새 버전이 헬스체크에 실패하면 서킷 브레이커가 자동 롤백한다.
GitHub Actions(OIDC) 자동화는 다음 이슈.

- 설정값(FRONTEND_URL 등) 변경: `variables.tf`/tfvars 수정 → `terraform apply` → 새 태스크 정의로 롤링 배포
- 운영 Swagger(`https://api.yufesta.com/swagger-ui/index.html`)는 `swagger_enabled`로 켜고 끈다. 10/1 배포 전 `terraform.tfvars`에 `swagger_enabled = false`를 넣고 apply
- 축제 당일(10/2)에는 `apply`·`push` 금지(NFR-AV-01)

## 4. 정리 (축제 후)

```bash
terraform destroy      # RDS는 yufesta-mysql-final 스냅샷을 남기고 삭제
```
그 뒤 콘솔에서 S3 state 버킷, IAM 사용자, RDS 최종 스냅샷(보관 불필요 시), 도메인(자동 갱신 해제)을 정리한다.

## 5. 이 정책이 기존 인프라를 못 건드리는 이유

- **Deny 문**: `Project=yufesta` 태그가 없는 리소스에 대한 삭제·수정 계열 액션을 거부한다. Terraform은 모든 리소스를 태그와 함께 만들므로(`default_tags`) 우리 것은 통과하고, 기존 리소스는 태그가 없어 거부된다. 예외 둘: 보안 그룹 규칙 추가(`ec2:Authorize*`)는 아직 없는 규칙 리소스에 평가돼 태그 조건을 쓸 수 없어 제외했고, CloudWatch Logs는 태그 조건이 즉시 반영되지 않아 로그 그룹 이름(`/ecs/yufesta-*`) 기준 `NotResource` Deny로 대신한다
- **이름 제한**: IAM 역할은 `yufesta-*`, SSM은 `/yufesta/*`, Route 53은 우리 존 하나, S3는 state 버킷 하나만 허용
- **Terraform state**: 우리가 만든 것만 들어 있어 `destroy`도 그 범위를 넘지 않는다
- 만약 `AccessDenied`가 우리 리소스에서 나면 액션 이름을 확인해 Deny 목록을 조정한다. 기존 인프라 쪽으로 권한을 넓히지 않는다
