# 배포

## 흐름

| 언제 | 워크플로 | 하는 일 |
|---|---|---|
| PR, develop·main push | `CI` | PostGIS·Redis를 띄워 `./gradlew test`, Docker 이미지 빌드 확인 |
| main push (또는 Actions에서 직접 실행) | `CD` | 이미지를 `ghcr.io/11-pepero/nubi-be`에 올리고, EC2의 셀프 호스티드 러너가 새 이미지로 교체 |

배포는 EC2 안에 설치한 GitHub 러너가 GitHub로 먼저 연결해 받아 오므로 SSH 포트(22)를 GitHub에 열 필요가 없습니다. 보안 그룹은 SSH를 집 IP로만 막아 둔 그대로 둡니다.

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
| `JAVA_TOOL_OPTIONS` |  | 메모리 1GB 서버(t3.micro)에서는 `-Xmx384m -XX:MaxMetaspaceSize=192m -XX:+UseSerialGC -Xss512k -Dfile.encoding=UTF-8` |

- 메모리 1GB 서버(t3.micro)는 DB·Redis·러너만으로 450MB 가량을 써서, 앱까지 뜨면 서버가 멈춥니다. 스왑 2GB를 만들고 위 `JAVA_TOOL_OPTIONS`로 앱 메모리를 묶어 둡니다.
  ```bash
  sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
  ```
- 빈 DB에 처음 배포할 때는 `.env`에 `SPRING_JPA_HIBERNATE_DDL_AUTO=update`를 두어 테이블을 만들고, 첫 배포가 끝나면 그 줄을 지웁니다 (prod 기본값은 `validate`).
- PostGIS 확장은 postgres 컨테이너가 처음 뜰 때 `DB_NAME` DB에 자동으로 켜집니다.
- 행사·후기 사진은 `uploads` 볼륨에, DB는 `postgres-data` 볼륨에 남아서 이미지를 바꿔도 지워지지 않습니다.
- 서버 방화벽(보안 그룹)에서 `APP_PORT`(기본 8080)를 엽니다. HTTPS는 앞단에 Nginx나 로드밸런서를 두어 붙입니다.

### 셀프 호스티드 러너 설치

1. 저장소 Settings → Actions → Runners → **New self-hosted runner** → Linux, x64 를 고르면 나오는 명령어를 EC2에서 실행합니다 (Download, Configure).
2. `./config.sh` 를 실행할 때 라벨을 꼭 붙입니다. CD가 이 라벨이 있는 러너에서만 배포합니다.
   ```bash
   ./config.sh --url https://github.com/11-Pepero/Nubi-BE --token <화면의 토큰> --labels nubi-prod --unattended
   ```
3. 서버가 재부팅돼도 러너가 다시 뜨게 서비스로 등록합니다.
   ```bash
   sudo ./svc.sh install $USER
   sudo ./svc.sh start
   ```
4. 러너를 실행하는 계정이 `docker` 그룹에 있고 `/opt/nubi` 의 주인이어야 합니다 (위 서버 준비를 같은 계정으로 했다면 그대로 됩니다). 그룹을 나중에 추가했다면 `sudo ./svc.sh stop && sudo ./svc.sh start` 로 다시 띄웁니다.
5. Runners 목록에 `nubi-prod` 러너가 **Idle** 로 보이면 준비 끝입니다.

## GitHub 설정

저장소 Settings → Secrets and variables → Actions → **Variables** 탭에 넣습니다. 비밀값은 GitHub에 넣지 않고 서버 `.env`에만 둡니다.

| 이름 | 필수 | 설명 |
|---|---|---|
| `DEPLOY_ENABLED` | O | `true` 로 두면 배포합니다. 러너를 설치하기 전에는 비워 두세요 (비어 있으면 CD는 이미지만 올립니다) |
| `DEPLOY_PATH` |  | 기본 `/opt/nubi` |

러너가 GHCR에서 이미지를 받을 때는 Actions가 자동으로 주는 `GITHUB_TOKEN`으로 로그인하므로 따로 토큰을 만들 필요가 없습니다.

## 서버에서 직접 다루기

```bash
cd /opt/nubi
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f app

# 앱 다시 켜기 (마지막으로 배포한 nubi-be:current 이미지를 씀)
docker compose -f docker-compose.prod.yml up -d app

# 이전 버전으로 되돌리기: Actions의 CD에서 예전 실행을 Re-run 하거나, 서버에 남아 있는 이미지로 띄운다
docker images ghcr.io/11-pepero/nubi-be
APP_IMAGE=ghcr.io/11-pepero/nubi-be:sha-abc1234 docker compose -f docker-compose.prod.yml up -d app
```
