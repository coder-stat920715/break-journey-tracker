package com.souptik.breakjourney.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "tracker")
public record TrackerProperties(
        @DefaultValue("600") int simulationSpeedFactor,
        @DefaultValue("10") int transferHandoffMinutes,
        @DefaultValue("40") int safeBufferMinutes,
        @DefaultValue("15") int tightBufferMinutes,
        @DefaultValue("1800000") long sseTimeoutMs) {
}
