package dev.hidly.nubi.common.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;

@Component
@Profile("local")
public class LocalProfileGuard implements InitializingBean {
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1", "[::1]");

    private final Environment environment;

    public LocalProfileGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        boolean defaultJwtKey = environment.getProperty("JWT_SECRET_KEY") == null;
        String host = databaseHost(environment.getProperty("spring.datasource.url", ""));
        if (defaultJwtKey && !LOCAL_HOSTS.contains(host)) {
            throw new IllegalStateException("local 프로필의 공개 JWT 기본 키로 외부 DB(" + host + ")에 연결할 수 없습니다. "
                    + "운영 배포라면 SPRING_PROFILES_ACTIVE=prod와 JWT_SECRET_KEY를 설정하세요.");
        }
    }

    static String databaseHost(String jdbcUrl) {
        try {
            String host = URI.create(jdbcUrl.replaceFirst("^jdbc:", "")).getHost();
            return host == null ? "" : host;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }
}
