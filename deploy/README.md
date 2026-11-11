# 배포

## 흐름

| 언제 | 워크플로 | 하는 일 |
|---|---|---|
| PR, develop·main push | `CI` | PostGIS·Redis를 띄워 `./gradlew test`, Docker 이미지 빌드 확인 |
| main push (또는 Actions에서 직접 실행) | `CD` | 이미지를 `ghcr.io/11-pepero/nubi-be`에 올리고 SSH로 서버에서 새 이미지로 교체 |

이미지 태그는 `sha-<커밋 7자리>`와 `latest` 두 가지입니다. 되돌릴 때는 서버에서 이전 태그로 띄웁니다.

## 서버 준비 (한 번만)

Docker가 설치된 Linux 서버(amd64)를 기준으로 합니다.

```bash
# 1. Docker 설치 (Ubuntu)
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER   # 다시 로그인해야 적용

# 2. 배포 폴더와 .env
sudo mkdir -p /opt/nubi && sudo chown $USER /opt/nubi
cd /opt/nubi
# 아래 "서버 .env 항목"을 보고 .env 를 만든다
vi .env
chmod 600 .env
```

### 서버 .env 항목

| 이름 | 필수 | 설명 |
|---|---|---|
| `APP_PORT` |  | 서버 바깥 포트, 기본 8080 |
| `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | O | compose 안 PostGIS의 DB 이름·계정 |
| `REDIS_PASSWORD` | O | Redis 비밀번호 |
| `JWT_SECRET_KEY` | O | Base64 256비트 이상 (`openssl rand -base64 48`) |
| `CORS_ALLOWED_ORIGINS` | O | 프론트 주소, 여러 개면 쉼표로 |
| `DATA_GO_KR_SERVICE_KEY`, `BARRIER_FREE_SERVICE_KEY`, `KAKAO_REST_API_KEY`, `OPENAI_API_KEY`, `GG_DATA_KEY` |  | 외부 API 키. 비우면 그 기능만 꺼집니다 |
| `COLLECT_ENABLED`, `COLLECT_RUN_ON_STARTUP` |  | 공공데이터 수집 (매일 새벽 5시) |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` |  | 빈 DB 첫 배포 때만 `update` |

- 빈 DB에 처음 배포할 때는 `.env`에 `SPRING_JPA_HIBERNATE_DDL_AUTO=update`를 두어 테이블을 만들고, 첫 배포가 끝나면 그 줄을 지웁니다 (prod 기본값은 `validate`).
- PostGIS 확장은 postgres 컨테이너가 처음 뜰 때 `DB_NAME` DB에 자동으로 켜집니다.
- 행사·후기 사진은 `uploads` 볼륨에, DB는 `postgres-data` 볼륨에 남아서 이미지를 바꿔도 지워지지 않습니다.
- 서버 방화벽(보안 그룹)에서 `APP_PORT`(기본 8080)를 엽니다. HTTPS는 앞단에 Nginx나 로드밸런서를 두어 붙입니다.

## GitHub Secrets

저장소 Settings → Secrets and variables → Actions 에 넣습니다.

| 이름 | 필수 | 설명 |
|---|---|---|
| `SSH_HOST` | O | 서버 주소. 없으면 CD는 이미지만 올리고 배포는 건너뜁니다 |
| `SSH_USER` | O | 접속 계정 (docker 그룹에 속해야 함) |
| `SSH_PRIVATE_KEY` | O | 접속용 개인 키 전체 (`-----BEGIN ... KEY-----` 포함) |
| `SSH_PORT` |  | 기본 22 |
| `DEPLOY_PATH` |  | 기본 `/opt/nubi` |

서버가 GHCR에서 이미지를 받을 때는 배포 중에만 쓰는 `GITHUB_TOKEN`으로 로그인하므로 따로 토큰을 만들 필요가 없습니다.

## 서버에서 직접 다루기

```bash
cd /opt/nubi
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f app

# 이전 버전으로 되돌리기 (GHCR 로그인 필요: echo <PAT> | docker login ghcr.io -u <아이디> --password-stdin)
APP_IMAGE=ghcr.io/11-pepero/nubi-be:sha-abc1234 docker compose -f docker-compose.prod.yml up -d app
```
