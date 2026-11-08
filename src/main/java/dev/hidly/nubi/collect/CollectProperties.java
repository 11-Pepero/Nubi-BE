package dev.hidly.nubi.collect;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("collect")
public record CollectProperties(boolean enabled, boolean runOnStartup, String cron, int calendarMonths, long requestDelayMs) {
}
