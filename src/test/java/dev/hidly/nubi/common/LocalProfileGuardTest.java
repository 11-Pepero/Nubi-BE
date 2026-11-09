package dev.hidly.nubi.common;

import dev.hidly.nubi.common.config.LocalProfileGuard;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalProfileGuardTest {
    @Test
    void 기본_JWT_키로_외부_DB에_붙으면_기동을_막는다() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.example.com:5432/nubi");

        assertThatThrownBy(() -> new LocalProfileGuard(environment).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void 로컬_DB이거나_JWT_키를_설정하면_통과한다() {
        assertThatCode(() -> new LocalProfileGuard(new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/nubi")).afterPropertiesSet())
                .doesNotThrowAnyException();
        assertThatCode(() -> new LocalProfileGuard(new MockEnvironment()
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.example.com:5432/nubi")
                .withProperty("JWT_SECRET_KEY", "c2VjcmV0")).afterPropertiesSet())
                .doesNotThrowAnyException();
    }
}
