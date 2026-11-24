package com.souptik.breakjourney.dto;

import com.souptik.breakjourney.enums.RiskStatus;

import java.time.OffsetDateTime;

/** Time between arriving at a fixed-departure service (train/bus) and that service leaving. */
public record ConnectionBufferDto(
        int fromLegOrder,
        int toLegOrder,
        String location,
        OffsetDateTime arrival,
        OffsetDateTime departure,
        long bufferMinutes,
        RiskStatus riskStatus) {
}
