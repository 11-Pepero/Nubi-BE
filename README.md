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

## prod 프로필

`SPRING_PROFILES_ACTIVE=prod`는 기본값이 없어서 위 환경변수 전체와 `JWT_SECRET_KEY`(Base64), `CORS_ALLOWED_ORIGINS`가 필요합니다. `ddl-auto: validate`라서 스키마를 미리 만들어야 하고, DB에 `CREATE EXTENSION postgis;`가 되어 있어야 합니다.
