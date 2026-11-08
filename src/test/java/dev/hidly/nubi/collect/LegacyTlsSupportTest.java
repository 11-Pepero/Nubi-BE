package dev.hidly.nubi.collect;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Security;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyTlsSupportTest {
    private static final String PROPERTY = "jdk.tls.disabledAlgorithms";
    private String original;

    @BeforeEach
    void setUp() {
        original = Security.getProperty(PROPERTY);
        Security.setProperty(PROPERTY, "SSLv3, TLSv1, RC4, TLS_RSA_*, anon, NULL");
    }

    @AfterEach
    void tearDown() {
        Security.setProperty(PROPERTY, original);
        System.clearProperty(LegacyTlsSupport.SWITCH);
    }

    @Test
    void 스위치가_꺼져_있으면_차단_목록을_건드리지_않는다() {
        LegacyTlsSupport.applyIfEnabled();

        assertThat(Security.getProperty(PROPERTY)).contains("TLS_RSA_*");
    }

    @Test
    void 스위치를_켜면_TLS_RSA만_허용하고_나머지_차단_목록은_그대로_둔다() {
        System.setProperty(LegacyTlsSupport.SWITCH, "true");

        LegacyTlsSupport.applyIfEnabled();
        LegacyTlsSupport.applyIfEnabled();

        assertThat(Security.getProperty(PROPERTY)).isEqualTo("SSLv3, TLSv1, RC4, anon, NULL");
    }
}
