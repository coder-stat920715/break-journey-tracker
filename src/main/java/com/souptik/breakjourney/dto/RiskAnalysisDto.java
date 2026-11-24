package com.souptik.breakjourney.dto;

import com.souptik.breakjourney.enums.RiskStatus;

import java.time.OffsetDateTime;
import java.util.List;

public record RiskAnalysisDto(
        Integer trainLegOrder,
        int trainDelayMinutes,
        OffsetDateTime adjustedBhubaneswarArrival,
        OffsetDateTime adjustedTransferDeparture,
        OffsetDateTime adjustedBaramundaArrival,
        OffsetDateTime busDeparture,
        Long bufferMinutes,
        RiskStatus riskStatus,
        boolean connectionMissed,
        String summary,
        List<String> recommendations,
        List<ConnectionBufferDto> connections) {
}
