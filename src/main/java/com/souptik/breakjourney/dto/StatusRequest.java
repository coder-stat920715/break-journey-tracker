package com.souptik.breakjourney.dto;

import com.souptik.breakjourney.enums.LegStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record StatusRequest(
        @Schema(example = "IN_TRANSIT")
        @NotNull(message = "status is required")
        LegStatus status) {
}
