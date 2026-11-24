package com.souptik.breakjourney.service;

import com.souptik.breakjourney.config.TrackerProperties;
import com.souptik.breakjourney.dto.ConnectionBufferDto;
import com.souptik.breakjourney.dto.LegTimingDto;
import com.souptik.breakjourney.dto.RiskAnalysisDto;
import com.souptik.breakjourney.entity.Journey;
import com.souptik.breakjourney.entity.JourneyLeg;
import com.souptik.breakjourney.enums.RiskStatus;
import com.souptik.breakjourney.enums.TransportMode;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.souptik.breakjourney.service.TimeUtil.hhmm;
import static com.souptik.breakjourney.service.TimeUtil.ist;
import static com.souptik.breakjourney.service.TimeUtil.minutesBetween;

/**
 * Smart layover and risk analyzer.
 *
 * <p>Model:
 * <ul>
 *   <li>Fixed-departure legs (TRAIN, BUS) run to the timetable plus their own reported delay. They never wait.</li>
 *   <li>Flexible legs (CAB, AUTO_CAB) leave at the later of their planned time (plus own delay) and the previous
 *       leg's effective arrival plus the transfer handoff, and then take the planned travel duration.</li>
 *   <li>The connection buffer into a fixed leg is its effective departure minus the previous leg's effective arrival.</li>
 *   <li>Risk: buffer &gt;= safe threshold is SAFE, &gt;= tight threshold is TIGHT_CONNECTION, otherwise
 *       CRITICAL_MISSED_CONNECTION.</li>
 * </ul>
 */
@Service
public class RiskAnalyzerService {

    private final TrackerProperties props;

    public RiskAnalyzerService(TrackerProperties props) {
        this.props = props;
    }

    public JourneyAnalysis analyze(Journey journey) {
        List<JourneyLeg> legs = journey.getLegs().stream()
                .sorted(Comparator.comparingInt(JourneyLeg::getLegOrder))
                .toList();

        List<LegTimingDto> timings = new ArrayList<>();
        List<ConnectionBufferDto> connections = new ArrayList<>();
        OffsetDateTime prevArrival = null;
        JourneyLeg prevLeg = null;

        for (JourneyLeg leg : legs) {
            OffsetDateTime schedDep = ist(leg.getScheduledDeparture());
            OffsetDateTime schedArr = ist(leg.getScheduledArrival());
            int own = leg.getDelayMinutes();
            OffsetDateTime effDep;
            OffsetDateTime effArr;

            if (isFlexible(leg.getMode())) {
                effDep = schedDep.plusMinutes(own);
                if (prevArrival != null) {
                    OffsetDateTime earliest = prevArrival.plusMinutes(props.transferHandoffMinutes());
                    if (earliest.isAfter(effDep)) {
                        effDep = earliest;
                    }
                }
                effArr = effDep.plus(Duration.between(schedDep, schedArr));
            } else {
                effDep = schedDep.plusMinutes(own);
                effArr = schedArr.plusMinutes(own);
                if (prevArrival != null) {
                    long buffer = minutesBetween(prevArrival, effDep);
                    connections.add(new ConnectionBufferDto(prevLeg.getLegOrder(), leg.getLegOrder(),
                            prevLeg.getDestination(), prevArrival, effDep, buffer, classify(buffer)));
                }
            }

            timings.add(new LegTimingDto(leg.getLegOrder(), schedDep, schedArr, effDep, effArr, own,
                    minutesBetween(schedArr, effArr)));
            prevArrival = effArr;
            prevLeg = leg;
        }
        return new JourneyAnalysis(timings, buildRisk(legs, timings, connections));
    }

    public RiskStatus classify(long bufferMinutes) {
        if (bufferMinutes >= props.safeBufferMinutes()) {
            return RiskStatus.SAFE;
        }
        if (bufferMinutes >= props.tightBufferMinutes()) {
            return RiskStatus.TIGHT_CONNECTION;
        }
        return RiskStatus.CRITICAL_MISSED_CONNECTION;
    }

    private static boolean isFlexible(TransportMode mode) {
        return mode == TransportMode.CAB || mode == TransportMode.AUTO_CAB;
    }

    private RiskAnalysisDto buildRisk(List<JourneyLeg> legs, List<LegTimingDto> timings,
                                      List<ConnectionBufferDto> connections) {
        Integer trainOrder = null;
        int trainDelay = 0;
        OffsetDateTime bbsArrival = null;
        OffsetDateTime transferDeparture = null;
        OffsetDateTime transferArrival = null;
        for (int i = 0; i < legs.size(); i++) {
            if (legs.get(i).getMode() == TransportMode.TRAIN) {
                trainOrder = legs.get(i).getLegOrder();
                trainDelay = legs.get(i).getDelayMinutes();
                bbsArrival = timings.get(i).effectiveArrival();
                if (i + 1 < legs.size() && isFlexible(legs.get(i + 1).getMode())) {
                    transferDeparture = timings.get(i + 1).effectiveDeparture();
                    transferArrival = timings.get(i + 1).effectiveArrival();
                }
                break;
            }
        }

        if (connections.isEmpty()) {
            return new RiskAnalysisDto(trainOrder, trainDelay, bbsArrival, transferDeparture, transferArrival,
                    null, null, RiskStatus.SAFE, false,
                    "No fixed-departure connections to monitor.", List.of(), connections);
        }

        ConnectionBufferDto headline = connections.get(connections.size() - 1);
        RiskStatus overall = connections.stream()
                .map(ConnectionBufferDto::riskStatus)
                .max(Comparator.naturalOrder())
                .orElse(RiskStatus.SAFE);
        boolean missed = connections.stream().anyMatch(c -> c.bufferMinutes() < 0);

        JourneyLeg busLeg = legs.stream()
                .filter(l -> l.getLegOrder() == headline.toLegOrder())
                .findFirst()
                .orElse(legs.get(legs.size() - 1));
        String operator = busLeg.getOperatorName() != null ? busLeg.getOperatorName() : "next service";
        String dep = hhmm(headline.departure());

        String summary;
        if (trainOrder != null) {
            summary = "Train running %d min late: reaches Bhubaneswar at %s and %s at %s. %d min before the %s %s departure (%s)."
                    .formatted(trainDelay, hhmm(bbsArrival), shortPlace(headline.location()),
                            hhmm(transferArrival != null ? transferArrival : headline.arrival()),
                            headline.bufferMinutes(), dep, operator, label(headline.riskStatus()));
        } else {
            summary = "Buffer before the %s %s departure: %d min (%s)."
                    .formatted(dep, operator, headline.bufferMinutes(), label(headline.riskStatus()));
        }

        List<String> recs = new ArrayList<>();
        switch (headline.riskStatus()) {
            case SAFE -> {
                recs.add("No action needed: the connection to %s is comfortable (%d min spare).".formatted(operator, headline.bufferMinutes()));
                recs.add("Use the spare time for lunch near the bus stand and be at the boarding bay by %s."
                        .formatted(hhmm(headline.departure().minusMinutes(20))));
            }
            case TIGHT_CONNECTION -> {
                recs.add("Pre-book the auto/cab from the station while the train is still approaching Bhubaneswar.");
                recs.add("Skip the lunch stop and go straight to the boarding bay.");
                recs.add("Message %s with your live ETA (%s) and confirm seat %s is held."
                        .formatted(operator, hhmm(headline.arrival()), busLeg.getSeatNumber() != null ? busLeg.getSeatNumber() : "your seat"));
                recs.add("Fallback: ask for pickup at the Khandagiri NH-16 bypass if the transfer slows down.");
            }
            case CRITICAL_MISSED_CONNECTION -> {
                recs.add("Alert %s operator and request boarding at Khandagiri NH-16 bypass.".formatted(shortOperator(operator)));
                recs.add("Consider heading straight to the Khandagiri NH-16 bypass point if the operator agrees to wait there.");
                recs.add("If the bus is missed, ask the operator for rebooking or a refund (Ticket %s / PNR %s) and check later Bhubaneswar to Hyderabad departures."
                        .formatted(orDash(busLeg.getTicketNumber()), orDash(busLeg.getBookingReference())));
            }
        }
        for (ConnectionBufferDto c : connections) {
            if (c != headline && c.riskStatus() != RiskStatus.SAFE) {
                recs.add("Connection into leg %d at %s has only %d min spare (%s). Leave earlier or switch to a faster transfer."
                        .formatted(c.toLegOrder(), shortPlace(c.location()), c.bufferMinutes(), label(c.riskStatus())));
            }
        }

        return new RiskAnalysisDto(trainOrder, trainDelay, bbsArrival, transferDeparture, transferArrival,
                headline.departure(), headline.bufferMinutes(), overall, missed, summary, recs, connections);
    }

    private static String label(RiskStatus status) {
        return switch (status) {
            case SAFE -> "safe";
            case TIGHT_CONNECTION -> "tight connection";
            case CRITICAL_MISSED_CONNECTION -> "critical, connection likely missed";
        };
    }

    private static String shortPlace(String place) {
        if (place == null) {
            return "the next stop";
        }
        int paren = place.indexOf('(');
        return (paren > 0 ? place.substring(0, paren) : place).trim();
    }

    private static String shortOperator(String operator) {
        return operator.replace(" Pvt Ltd", "");
    }

    private static String orDash(String s) {
        return s == null ? "-" : s;
    }
}
