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
| 11 | `iam/*.json`이 바뀐 PR을 병합할 때마다 IAM > Policies > 각 정책 > **새 버전 생성**으로 JSON을 다시 붙여넣고 기본 버전으로 설정(예: 이미지 저장소 #93에서 S3·CloudFront 권한 추가) | IAM > Policies |

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
terraform output image_cdn_url                       # https://dxxxx.cloudfront.net (사진 업로드 후 그 URL로 200, 버킷 직접 URL은 403)
```

- 브라우저: `https://api.yufesta.com/oauth2/authorization/kakao?redirect=/` → 카카오 로그인 → `https://yufesta.com/`로 복귀, `access_token`·`XSRF-TOKEN` 쿠키 domain `yufesta.com`
- 알람 구독: 이메일로 온 "AWS Notification - Subscription Confirmation"의 링크 클릭
- 운영자 지정: RDS는 프라이빗이라 밖에서 접속이 안 된다. `admin.allowlist`는 ECS Exec 또는 임시 배스천 없이 하려면
  로컬에서 `docker compose`로 같은 SQL을 만든 뒤, 운영자 후보가 첫 로그인을 하기 **전에** 다음 PR에서 붙일 운영자 API로 넣는다(TODO)

## 3. 배포 갱신·운영 (GitHub Actions)

**브랜치**: `develop`(통합) → `main`(운영). 배포하려면 `develop → main` PR을 병합한다. `main` 푸시마다 `.github/workflows/deploy.yml`이
테스트 → ARM 러너에서 이미지 빌드 → ECR에 `latest`·`<sha>` push → `update-service --force-new-deployment` → `services-stable` 대기 → 헬스 확인을 한다(약 6~8분).
PR과 `develop` 푸시는 `ci.yml`이 테스트만 돌린다.

**최초 1회 연결**
1. `terraform apply` (`github.tf`: 역할 `yufesta-github-deploy`). 소마 계정에는 기존 프로젝트가 만든 GitHub OIDC 공급자가 이미 있어
   `terraform.tfvars`에 `create_github_oidc_provider = false`를 두고 읽기만 한다(배포자 정책도 공급자 읽기 권한만). 공급자는 계정당 하나이며 역할별 신뢰 조건이 저장소·브랜치를 가르므로 기존 프로젝트와 간섭이 없다
2. `terraform output -raw github_deploy_role_arn` 값을 GitHub 저장소 → Settings → Secrets and variables → Actions → **Variables** → `AWS_DEPLOY_ROLE_ARN`으로 등록(비밀이 아니라 Variables)
3. GitHub에서 `main` 브랜치를 `develop`에서 생성 → `develop → main` PR 병합 → Actions 탭에서 Deploy 진행 확인
4. `Not authorized to perform sts:AssumeRoleWithWebIdentity`가 나오면 Deploy 로그의 `OIDC 토큰 주체 확인` 단계에 찍힌 `sub=`를 `github.tf`의 `local.github_sub`와 대조한다. 이 저장소는 불변 주체 형식(`repo:owner@ID/repo@ID:ref:…`)이라 조직·저장소 숫자 ID가 변수로 들어가 있다

**롤백**: 이전 커밋의 sha 태그를 `latest`로 되돌리고 재배포한다(이미지를 다시 받지 않는다).
```bash
export AWS_PROFILE=yufesta
aws ecr describe-images --repository-name yufesta-api --query 'sort_by(imageDetails,&imagePushedAt)[-5:].[imageTags,imagePushedAt]' --output table
MANIFEST=$(aws ecr batch-get-image --repository-name yufesta-api --image-ids imageTag=<이전 sha> --query 'images[0].imageManifest' --output text)
aws ecr put-image --repository-name yufesta-api --image-tag latest --image-manifest "$MANIFEST" >/dev/null
aws ecs update-service --cluster yufesta-cluster --service yufesta-api --force-new-deployment
```
새 버전이 헬스체크에 계속 실패하면 서킷 브레이커가 자동으로 이전 태스크로 되돌리고 워크플로는 `rolloutState`가 COMPLETED가 아니라서 실패로 표시된다.

**수동 배포**(워크플로를 못 쓸 때): 1절의 build·push 명령 후 `aws ecs update-service ... --force-new-deployment`.

- 설정값(FRONTEND_URL 등) 변경: `variables.tf`/tfvars 수정 → `terraform apply` → 새 태스크 정의로 롤링 배포
- **apply는 항상 `git pull`한 develop에서.** 오래된 브랜치에서 apply하면 그 브랜치의 `.tf`대로 되돌린다. 2026-09-24에 `chore/frontend-dns`(불변 주체 PR #68 이전 분기)에서 dbshell을 apply해 `github_deploy` 신뢰 정책이 옛 sub로 돌아가 배포가 실패했다. plan의 `change`·`destroy` 줄을 읽고, 의도하지 않은 리소스(특히 `aws_iam_role.github_deploy`)가 보이면 중단한다
- 운영 Swagger(`https://api.yufesta.com/swagger-ui/index.html`)는 `swagger_enabled`로 켜고 끈다. 10/1 배포 전 `terraform.tfvars`에 `swagger_enabled = false`를 넣고 apply
- 축제 당일(10/2)에는 `apply`·`push` 금지(NFR-AV-01)

## 3-1. 운영 DB 접속 (dbshell)

RDS는 밖에서 붙을 수 없다. `ops.tf`의 `yufesta-dbshell` 태스크(mysql 클라이언트)를 띄워 ECS Exec으로 셸을 연다.
최초 1회 `brew install --cask session-manager-plugin`.

```bash
export AWS_PROFILE=yufesta
NET=$(terraform -chdir="$(git rev-parse --show-toplevel)/infra/terraform" output -raw dbshell_network)  # 저장소 어느 디렉터리에서든
TASK=$(aws ecs run-task --cluster yufesta-cluster --task-definition yufesta-dbshell --launch-type FARGATE \
  --enable-execute-command --propagate-tags TASK_DEFINITION --network-configuration "$NET" \
  --query 'tasks[0].taskArn' --output text)
aws ecs wait tasks-running --cluster yufesta-cluster --tasks "$TASK"
# 태스크가 RUNNING이어도 ECS Exec 에이전트는 몇십 초 뒤에 뜬다. 뜰 때까지 기다린다
until [ "$(aws ecs describe-tasks --cluster yufesta-cluster --tasks "$TASK" \
  --query 'tasks[0].containers[0].managedAgents[?name==`ExecuteCommandAgent`].lastStatus | [0]' --output text)" = "RUNNING" ]; do
  printf .; sleep 5; done; echo " exec ready"
aws ecs execute-command --cluster yufesta-cluster --task "$TASK" --container dbshell --interactive \
  --command 'sh -c "mysql -h $DB_HOST -u$DB_USER -p$DB_PASSWORD $DB_NAME"'
# mysql> 프롬프트에서 작업. 나가면(exit) 아래로 태스크 종료
aws ecs stop-task --cluster yufesta-cluster --task "$TASK" >/dev/null
```
그래도 `execute-command`가 "not enabled"로 실패하면 같은 `$TASK`로 그 줄만 다시 실행한다(`run-task`를 또 하지 말 것. 태스크가 하나 더 뜬다).

자주 쓰는 SQL
```sql
-- 회원 목록(개인정보는 provider_user_id뿐). 특정인은 가입 시각으로 찾는다
SELECT id, provider, role, created_at, last_login_at FROM users ORDER BY id;
-- 회원 삭제: 신청·태그·매칭·신고가 FK CASCADE로 함께 지워진다
DELETE FROM users WHERE id = <id>;
-- 운영자 지정: 이미 가입한 회원은 role 직접 변경(재로그인 불필요), 아직이면 allowlist에 넣어 첫 로그인 때 STAFF 부여.
-- 팀원(프론트 운영자 페이지 테스트)은 운영에서 한 번 로그인시킨 뒤 STAFF로. 회차 open·close·rerun·시각 수정까지 되고 publish는 OWNER만
UPDATE users SET role = 'OWNER' WHERE id = <내 id>;
UPDATE users SET role = 'STAFF' WHERE id = <팀원 id>;
UPDATE app_settings SET setting_value = 'KAKAO:<providerUserId>' WHERE setting_key = 'admin.allowlist';
-- 회차 상태·시각 확인(변경은 운영자 API로)
SELECT seq, status, open_at, close_at, publish_at, executed_at, published_at FROM match_rounds;
-- 리허설 뒤 초기화: 결과·신고·신청을 지우고 회차를 처음 상태로(회원은 남긴다). 시각은 운영자 API로 실제 값을 다시 넣는다
DELETE FROM matches;
DELETE FROM blocks;
DELETE FROM applications;
UPDATE match_rounds SET status = 'SCHEDULED', executed_at = NULL, published_at = NULL;
```
리허설 초기화 뒤 1회차 `open_at`이 이미 지났으면 스케줄러가 10초 안에 다시 OPEN으로 연다.

## 3-2. 이미지 저장소 (S3 · CloudFront)

`storage.tf`: 비공개 버킷 `yufesta-images` + CloudFront 배포(OAC). 앱(태스크 역할)은 버킷에 쓰기만 하고, 읽기는 CloudFront만 허용된다(버킷 정책의 `AWS:SourceArn`).
컨테이너 env `IMAGE_STORAGE=s3`, `IMAGE_BUCKET`, `IMAGE_CDN_URL`은 Terraform이 넣는다. 로컬은 `IMAGE_STORAGE=local`로 `./uploads`에 저장한다.

- 첫 apply 전 0절 11번(정책 새 버전)을 먼저. CloudFront 배포 생성·삭제는 5~10분 걸린다
- 사진 업로드는 운영 Swagger admin `POST /api/v1/admin/clubs/{clubId}/photo`(multipart `file`, 10MB 이하 jpeg·png·webp). 서버가 긴 변 1600px·썸네일 400px JPEG로 줄여 `clubs/{clubId}/{uuid}-1600.jpg`·`-thumb.jpg`로 올린다
- 키에 uuid가 있어 CloudFront 무효화가 필요 없다(교체하면 새 키). 캐시는 관리형 CachingOptimized(기본 1일)
- 비용: S3 수백 MB + CloudFront 수 GB 전송이면 월 $1 미만
- 커스텀 도메인(img.yufesta.com)은 CloudFront가 us-east-1 인증서를 요구해 보류. 필요해지면 provider alias 추가
- `terraform destroy`는 버킷에 객체가 있으면 실패한다. 축제 후 `aws s3 rm s3://yufesta-images --recursive` 뒤 destroy

## 4. 정리 (축제 후)

```bash
terraform destroy      # RDS는 yufesta-mysql-final 스냅샷을 남기고 삭제
```
그 뒤 콘솔에서 S3 state 버킷, IAM 사용자, RDS 최종 스냅샷(보관 불필요 시), 도메인(자동 갱신 해제)을 정리한다.

## 5. 이 정책이 기존 인프라를 못 건드리는 이유

- **Deny 문**: `Project=yufesta` 태그가 없는 리소스에 대한 삭제·수정 계열 액션을 거부한다. Terraform은 모든 리소스를 태그와 함께 만들므로(`default_tags`) 우리 것은 통과하고, 기존 리소스는 태그가 없어 거부된다. 예외 둘: 보안 그룹 규칙 추가(`ec2:Authorize*`)는 아직 없는 규칙 리소스에 평가돼 태그 조건을 쓸 수 없어 제외했고, CloudWatch Logs는 태그 조건이 즉시 반영되지 않아 로그 그룹 이름(`/ecs/yufesta-*`) 기준 `NotResource` Deny로 대신한다
- **이름 제한**: IAM 역할은 `yufesta-*`, SSM은 `/yufesta/*`, Route 53은 우리 존 하나, S3는 state 버킷과 `yufesta-images*`만 allow. 거기에 `yufesta-*` 밖의 버킷·객체 변경은 `NotResource` Deny로 한 번 더 막는다
- **CloudFront**: 태그 없는 배포의 수정·삭제·무효화는 태그 Deny로 거부되고, 생성(`CreateDistribution`)은 요청에 `Project=yufesta` 태그가 있을 때만 allow(`aws:RequestTag` 조건. Terraform은 default_tags를 붙여 만든다). OAC는 태그를 지원하지 않아 `Update`를 허용하지 않고 `Delete`만 허용한다. 사용 중인 OAC는 CloudFront가 삭제를 거부하므로 기존 배포가 영향받을 길이 없다
- **Terraform state**: 우리가 만든 것만 들어 있어 `destroy`도 그 범위를 넘지 않는다
- 만약 `AccessDenied`가 우리 리소스에서 나면 액션 이름을 확인해 Deny 목록을 조정한다. 기존 인프라 쪽으로 권한을 넓히지 않는다
