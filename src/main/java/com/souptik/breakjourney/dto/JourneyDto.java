package com.souptik.breakjourney.dto;

import com.souptik.breakjourney.enums.JourneyStatus;

import java.util.List;

public record JourneyDto(
        Long id,
        PassengerDto passenger,
        String title,
        JourneyStatus status,
        List<LegDto> legs,
        RiskAnalysisDto riskAnalysis) {
}
