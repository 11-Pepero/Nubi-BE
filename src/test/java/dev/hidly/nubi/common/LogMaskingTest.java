package dev.hidly.nubi.common;

import dev.hidly.nubi.common.logging.LogMasking;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskingTest {
    @Test
    void 이메일은_앞_두_글자와_도메인만_남긴다() {
        assertThat(LogMasking.email("admin@center.kr")).isEqualTo("ad***@center.kr");
        assertThat(LogMasking.email("a@b.kr")).isEqualTo("a***@b.kr");
        assertThat(LogMasking.email("no-at-sign")).isEqualTo("***");
        assertThat(LogMasking.email(null)).isNull();
    }
}
