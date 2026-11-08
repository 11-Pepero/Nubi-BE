package dev.hidly.nubi.collect;

import lombok.extern.slf4j.Slf4j;

import java.security.Security;
import java.util.Arrays;
import java.util.stream.Collectors;

@Slf4j
public final class LegacyTlsSupport {
    public static final String SWITCH = "COLLECT_LEGACY_TLS";

    private static final String PROPERTY = "jdk.tls.disabledAlgorithms";
    private static final String RSA_KEY_EXCHANGE = "TLS_RSA_*";

    private LegacyTlsSupport() {
    }

    public static void applyIfEnabled() {
        String value = System.getProperty(SWITCH, System.getenv(SWITCH));
        if (Boolean.parseBoolean(value)) {
            allowRsaKeyExchange();
            log.warn("[LegacyTlsSupport] {}=true: TLS_RSA 키 교환을 허용합니다 (용인시청 문화달력 수집용)", SWITCH);
        }
    }

    static synchronized void allowRsaKeyExchange() {
        String disabled = Security.getProperty(PROPERTY);
        if (disabled == null || !disabled.contains(RSA_KEY_EXCHANGE)) {
            return;
        }
        Security.setProperty(PROPERTY, Arrays.stream(disabled.split(","))
                .map(String::trim)
                .filter(algorithm -> !algorithm.equals(RSA_KEY_EXCHANGE))
                .collect(Collectors.joining(", ")));
    }
}
