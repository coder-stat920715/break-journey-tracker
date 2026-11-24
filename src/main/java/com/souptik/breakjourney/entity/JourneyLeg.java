package com.souptik.breakjourney.entity;

import com.souptik.breakjourney.enums.LegStatus;
import com.souptik.breakjourney.enums.TransportMode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "journey_legs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"journey_id", "leg_order"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JourneyLeg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "journey_id")
    private Journey journey;

    @Column(name = "leg_order", nullable = false)
    private int legOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "transport_mode", nullable = false)
    private TransportMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private LegStatus status = LegStatus.SCHEDULED;

    @Column(length = 300)
    private String origin;
    @Column(length = 300)
    private String destination;
    private Double distanceKm;

    @Column(length = 200)
    private String operatorName;
    @Column(length = 200)
    private String vehicleType;
    private String bookingReference;
    private String transactionId;
    private String ticketNumber;
    @Column(length = 200)
    private String seatInfo;
    private String seatNumber;
    private String mealPlan;

    @Column(precision = 10, scale = 2)
    private BigDecimal fare;
    private String currency;

    @Column(length = 1000)
    private String notes;

    private OffsetDateTime recommendedBookingTime;
    private OffsetDateTime scheduledDeparture;
    private OffsetDateTime scheduledArrival;

    /** Informational: typical lateness for this service (the "realistic ETA" allowance). */
    private Integer expectedDelayBufferMinutes;
    /** Informational: planned layover before the next leg. */
    private Integer layoverBufferMinutes;

    /** Reported delay versus the timetable, in minutes. Set (not added) by the delay endpoint. */
    @Builder.Default
    private int delayMinutes = 0;
}
