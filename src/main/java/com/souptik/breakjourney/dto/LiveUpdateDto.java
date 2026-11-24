package com.souptik.breakjourney.dto;

import com.souptik.breakjourney.enums.JourneyStatus;

import java.time.OffsetDateTime;
import java.util.List;

/** Payload of the "snapshot" SSE event. */
public record LiveUpdateDto(
        Long journeyId,
        String passengerName,
        JourneyStatus journeyStatus,
        boolean simulationRunning,
        boolean simulated,
        int speedFactor,
        OffsetDateTime clock,
        double progressPercent,
        int currentLegOrder,
        String statusMessage,
        Long secondsToConnectionDeparture,
        OffsetDateTime connectionDeparture,
        RiskAnalysisDto riskAnalysis,
        List<LiveLegDto> legs) {
}
