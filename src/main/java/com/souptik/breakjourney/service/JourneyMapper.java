package com.souptik.breakjourney.service;

import com.souptik.breakjourney.dto.ConnectionBufferDto;
import com.souptik.breakjourney.dto.JourneyDto;
import com.souptik.breakjourney.dto.LegDto;
import com.souptik.breakjourney.dto.LegTimingDto;
import com.souptik.breakjourney.dto.PassengerDto;
import com.souptik.breakjourney.entity.Journey;
import com.souptik.breakjourney.entity.JourneyLeg;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.souptik.breakjourney.service.TimeUtil.ist;

@Component
public class JourneyMapper {

    private final RiskAnalyzerService analyzer;

    public JourneyMapper(RiskAnalyzerService analyzer) {
        this.analyzer = analyzer;
    }

    public JourneyDto toDto(Journey journey) {
        JourneyAnalysis analysis = analyzer.analyze(journey);
        List<JourneyLeg> legs = journey.getLegs().stream()
                .sorted(java.util.Comparator.comparingInt(JourneyLeg::getLegOrder)).toList();
        List<LegDto> legDtos = new ArrayList<>();
        for (int i = 0; i < legs.size(); i++) {
            legDtos.add(toLegDto(legs.get(i), analysis.timings().get(i), analysis));
        }
        return new JourneyDto(
                journey.getId(),
                new PassengerDto(journey.getPassengerName(), journey.getPassengerAge(),
                        journey.getPassengerGender(), journey.getContactNumber()),
                journey.getTitle(),
                journey.getStatus(),
                legDtos,
                analysis.risk());
    }

    private LegDto toLegDto(JourneyLeg l, LegTimingDto timing, JourneyAnalysis analysis) {
        Long connectionBuffer = analysis.risk().connections().stream()
                .filter(c -> c.toLegOrder() == l.getLegOrder())
                .map(ConnectionBufferDto::bufferMinutes)
                .findFirst().orElse(null);
        var realistic = l.getExpectedDelayBufferMinutes() != null && l.getScheduledArrival() != null
                ? ist(l.getScheduledArrival()).plusMinutes(l.getExpectedDelayBufferMinutes()) : null;
        return new LegDto(l.getId(), l.getLegOrder(), l.getMode(), l.getStatus(), l.getOrigin(), l.getDestination(),
                l.getDistanceKm(), l.getOperatorName(), l.getVehicleType(), l.getBookingReference(),
                l.getTransactionId(), l.getTicketNumber(), l.getSeatInfo(), l.getSeatNumber(), l.getMealPlan(),
                l.getFare(), l.getCurrency(), l.getNotes(), ist(l.getRecommendedBookingTime()),
                ist(l.getScheduledDeparture()), ist(l.getScheduledArrival()), l.getExpectedDelayBufferMinutes(),
                realistic, l.getLayoverBufferMinutes(), l.getDelayMinutes(),
                timing.effectiveDeparture(), timing.effectiveArrival(), connectionBuffer);
    }
}
