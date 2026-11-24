package com.souptik.breakjourney.dto;

import com.souptik.breakjourney.enums.LegStatus;
import com.souptik.breakjourney.enums.TransportMode;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record LegDto(
        Long id,
        int legOrder,
        TransportMode mode,
        LegStatus status,
        String origin,
        String destination,
        Double distanceKm,
        String operatorName,
        String vehicleType,
        String bookingReference,
        String transactionId,
        String ticketNumber,
        String seatInfo,
        String seatNumber,
        String mealPlan,
        BigDecimal fare,
        String currency,
        String notes,
        OffsetDateTime recommendedBookingTime,
        OffsetDateTime scheduledDeparture,
        OffsetDateTime scheduledArrival,
        Integer expectedDelayBufferMinutes,
        OffsetDateTime realisticArrival,
        Integer layoverBufferMinutes,
        int delayMinutes,
        OffsetDateTime effectiveDeparture,
        OffsetDateTime effectiveArrival,
        Long connectionBufferMinutes) {
}
