# 1단계: Gradle로 실행 jar를 만든다. 의존성을 먼저 받아 두면 소스만 바뀔 때 그 층을 다시 쓴다.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q > /dev/null
COPY src src
RUN ./gradlew bootJar --no-daemon -x test \
    && find build/libs -name '*.jar' ! -name '*-plain.jar' -exec cp {} app.jar \;

# 2단계: JRE만 있는 가벼운 이미지에서 실행한다.
FROM eclipse-temurin:21-jre
ENV TZ=Asia/Seoul \
    SPRING_PROFILES_ACTIVE=prod \
    FILE_UPLOAD_DIR=/app/uploads \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Dfile.encoding=UTF-8"
WORKDIR /app
RUN groupadd --system nubi && useradd --system --gid nubi --home /app nubi \
    && mkdir -p /app/uploads && chown -R nubi:nubi /app
COPY --from=build --chown=nubi:nubi /workspace/app.jar app.jar
USER nubi
EXPOSE 8080
VOLUME ["/app/uploads"]
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
