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

## prod 프로필

`SPRING_PROFILES_ACTIVE=prod`는 기본값이 없어서 위 환경변수 전체와 `JWT_SECRET_KEY`(Base64), `CORS_ALLOWED_ORIGINS`가 필요합니다. `ddl-auto: validate`라서 스키마를 미리 만들어야 하고, DB에 `CREATE EXTENSION postgis;`가 되어 있어야 합니다.
