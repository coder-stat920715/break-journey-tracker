package com.souptik.breakjourney.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Total delay versus the timetable. The value replaces any earlier delay on the leg; 0 clears it.")
public record DelayRequest(
        @Schema(example = "30")
        @NotNull(message = "delayMinutes is required")
        @Min(value = 0, message = "delayMinutes must be 0 or more")
        @Max(value = 1440, message = "delayMinutes must be 1440 or less")
        Integer delayMinutes) {
}
