package com.souptik.breakjourney.dto;

import com.souptik.breakjourney.enums.LegStatus;
import com.souptik.breakjourney.enums.TransportMode;

import java.time.OffsetDateTime;

public record LiveLegDto(
        int legOrder,
        TransportMode mode,
        LegStatus status,
        String origin,
        String destination,
        OffsetDateTime effectiveDeparture,
        OffsetDateTime effectiveArrival,
        int delayMinutes) {
}
