package com.souptik.breakjourney.dto;

import com.souptik.breakjourney.enums.RiskStatus;

import java.time.OffsetDateTime;

/** Payload of the "alert" SSE event. level is INFO, WARNING or DANGER. */
public record AlertDto(String level, String title, String message, RiskStatus riskStatus, OffsetDateTime timestamp) {

    public static AlertDto info(String title, String message) {
        return new AlertDto("INFO", title, message, null, OffsetDateTime.now());
    }

    public static AlertDto fromRisk(String title, RiskAnalysisDto risk) {
        String level = switch (risk.riskStatus()) {
            case SAFE -> "INFO";
            case TIGHT_CONNECTION -> "WARNING";
            case CRITICAL_MISSED_CONNECTION -> "DANGER";
        };
        return new AlertDto(level, title, risk.summary(), risk.riskStatus(), OffsetDateTime.now());
    }
}
