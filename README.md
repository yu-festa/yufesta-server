# yufesta-server

## 개발 환경 실행

Docker가 설치되어 있다면 아래 명령으로 앱과 MySQL을 함께 실행할 수 있습니다.

```bash
docker compose up --build
```

8080 포트가 이미 사용 중이면 `APP_PORT=8081 docker compose up --build`처럼 변경할 수 있습니다.

- API 서버: `http://localhost:8080`
- Swagger UI(dev): `http://localhost:8080/swagger-ui/index.html`
- Health check: `http://localhost:8080/actuator/health`

개발용 MySQL 비밀번호는 기본값으로만 제공됩니다. 실제 개발 환경에서 변경하려면 실행 전에 `MYSQL_ROOT_PASSWORD` 환경 변수를 설정하세요.
