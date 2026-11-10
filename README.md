# Nubi-BE

함께가요 용인 백엔드 서버

- Spring Boot 4.1.2, Java 21
- PostgreSQL 17 + PostGIS 3.5, Redis
- 협업 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)

## 로컬 실행

1. DB와 Redis를 띄웁니다.

   ```bash
   docker compose up -d
   ```

   이미 로컬에 Redis가 떠 있으면 `docker compose up -d postgres`로 PostgreSQL만 띄웁니다.

2. 서버를 실행합니다.

   ```bash
   ./gradlew bootRun
   ```

3. Swagger: http://localhost:8080/swagger-ui/index.html

local 프로필 기본값은 DB `localhost:5432/nubi` `nubi`/`nubi`, Redis `localhost:6379`입니다. 다르면 `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`, `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` 환경변수로 바꿉니다.

## 테스트

```bash
./gradlew test
```

테스트는 개발 DB(`nubi`)가 아니라 테스트 전용 DB(`nubi_test`)와 Redis 1번 DB를 씁니다. `src/test/resources`의 설정이 `local,test` 프로필을 켜기 때문에 Gradle, IntelliJ 어느 쪽으로 실행해도 같습니다.

- `docker compose up -d`로 **처음** 띄우면 `nubi_test`가 자동으로 만들어집니다.
- 이미 띄워 둔 컨테이너라면 한 번만 직접 만듭니다.

  ```bash
  docker exec -i nubi-postgres psql -U nubi -d postgres < docker/postgres/20-create-test-db.sql
  ```

- 테스트 데이터를 비우고 싶으면 테스트 DB만 지웠다가 위 명령으로 다시 만듭니다.

  ```bash
  docker exec nubi-postgres psql -U nubi -d postgres -c "DROP DATABASE nubi_test"
  ```

- 테스트 DB 이름과 Redis 번호는 `TEST_DB_NAME`, `TEST_REDIS_DATABASE`로 바꿀 수 있습니다.

## 외부 API 키

`local-secret.properties.example`을 `local-secret.properties`로 복사해 값을 채웁니다. 이 파일은 `.gitignore`에 있어서 저장소에 올라가지 않습니다.

| 키 | 용도 |
|---|---|
| `DATA_GO_KR_SERVICE_KEY` | 공공데이터포털 인증키 (Encoding, Decoding 둘 다 됨) |
| `GG_DATA_KEY` | 경기데이터드림(data.gg.go.kr) 인증키, 장애인복지관 운영 프로그램 수집 |
| `KAKAO_REST_API_KEY` | 카카오 로컬 API 키, 장소 주소를 좌표로 변환. 카카오 개발자 콘솔에서 앱의 **카카오맵** 사용 설정을 켜야 함 |

운영에서는 같은 이름의 환경변수로 넣습니다.

## 공고 수집

| 출처 | 방식 |
|---|---|
| 용인시청 문화달력 | 이번 달부터 3개월 치 목록을 보고 새 행사만 상세 페이지 요청 (요청 사이 1초) |
| 공공데이터포털 용인시 문화행사공연 정보 | API |
| 공공데이터포털 용인시 보건교육관리 현황, 평생교육관련 정보, 예절교육관 프로그램 | API. 지난 공고라 끝난 상태로 저장해 접근성 정보 공개율 같은 지표 계산에 씀. 원문의 시험용 행("test", "테스트입니다.")은 뺌 |
| 경기데이터드림 장애인복지관 운영 프로그램 (`DspsnCmwelfctOpertProg`) | 용인시 복지관 3곳의 상시 프로그램. 일정 없이 공개 중으로 두고, 원문 기준일과 운영 여부 확인 안내를 설명에 붙임 |
| 공공데이터포털 한국사회보장정보원 장애인편의시설 현황 | 수집이 끝나면 주소가 같은 시설을 찾아 장소 편의시설(`venue_facility`, 출처 `PUBLIC_DATA`)을 채움. 설치됨만 YES로 넣고, 한 건물에 여러 곳이 있는 종류(일반업무시설, 상가 등)는 이름도 겹칠 때만 이음 |

수집한 원문은 `raw_notice`, 프로그램은 `program`(원문 링크는 `apply_url`)에 저장하고 장소는 `venue`에 연결합니다. 같은 공고는 다시 넣지 않고, 끝난 수집 공고는 `ENDED`로 바꿉니다.

| 환경변수 | 기본값 | 설명 |
|---|---|---|
| `COLLECT_ENABLED` | `false` | 매일 새벽 5시 정기 수집 (`COLLECT_CRON`으로 변경) |
| `COLLECT_RUN_ON_STARTUP` | `false` | 앱 시작 시 한 번 수집 |
| `COLLECT_LEGACY_TLS` | `false` | 용인시청 문화달력 수집에 필요. 아래 참고 |

한 번만 수집해 보기:

```bash
COLLECT_RUN_ON_STARTUP=true COLLECT_LEGACY_TLS=true ./gradlew bootRun
```

### `COLLECT_LEGACY_TLS`

용인시청 서버(www.yongin.go.kr)는 TLS 1.2의 RSA 키 교환 암호(`TLS_RSA_WITH_AES_256_GCM_SHA384` 등)만 지원합니다. Java 21 최신 패치는 이 방식을 `jdk.tls.disabledAlgorithms`로 막아서 그대로는 접속이 안 됩니다. 이 값을 `true`로 주면 앱 시작 시 차단 목록에서 `TLS_RSA_*` 하나만 뺍니다.

- 최신 방식을 지원하는 서버(공공데이터포털, 카카오 등)와는 계속 ECDHE로 연결되고, 약한 방식으로 연결되는 곳은 그것만 지원하는 서버뿐입니다
- 용인시청에서 받는 것은 공개 행사 정보이고 인증 정보는 보내지 않습니다
- 앱 전체 설정이라 공고를 수집하는 인스턴스에서만 켜고, 용인시청 서버가 개선되면 끕니다

## prod 프로필

`SPRING_PROFILES_ACTIVE=prod`는 기본값이 없어서 위 환경변수 전체와 `JWT_SECRET_KEY`(Base64), `CORS_ALLOWED_ORIGINS`가 필요합니다. `ddl-auto: validate`라서 스키마를 미리 만들어야 하고, DB에 `CREATE EXTENSION postgis;`가 되어 있어야 합니다.
