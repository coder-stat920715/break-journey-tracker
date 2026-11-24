package com.souptik.breakjourney.dto;

import java.time.OffsetDateTime;

/** Scheduled versus effective (delay-adjusted) times for one leg. */
public record LegTimingDto(
        int legOrder,
        OffsetDateTime scheduledDeparture,
        OffsetDateTime scheduledArrival,
        OffsetDateTime effectiveDeparture,
        OffsetDateTime effectiveArrival,
        int delayMinutes,
        long arrivalShiftMinutes) {
}
