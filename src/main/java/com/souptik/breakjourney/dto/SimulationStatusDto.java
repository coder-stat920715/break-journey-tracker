package com.souptik.breakjourney.dto;

import java.time.OffsetDateTime;

public record SimulationStatusDto(
        Long journeyId,
        boolean running,
        boolean finished,
        OffsetDateTime virtualTime,
        int speedFactor,
        String message) {
}
