package com.souptik.breakjourney.service;

import com.souptik.breakjourney.config.TrackerProperties;
import com.souptik.breakjourney.dto.AlertDto;
import com.souptik.breakjourney.dto.ConnectionBufferDto;
import com.souptik.breakjourney.dto.LegTimingDto;
import com.souptik.breakjourney.dto.LiveLegDto;
import com.souptik.breakjourney.dto.LiveUpdateDto;
import com.souptik.breakjourney.entity.Journey;
import com.souptik.breakjourney.entity.JourneyLeg;
import com.souptik.breakjourney.enums.LegStatus;
import com.souptik.breakjourney.exception.ResourceNotFoundException;
import com.souptik.breakjourney.repository.JourneyRepository;
import com.souptik.breakjourney.simulator.SimulationClock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.souptik.breakjourney.service.TimeUtil.hhmm;

/**
 * Builds live snapshots and pushes them to SSE subscribers.
 * Events: "snapshot" (LiveUpdateDto) and "alert" (AlertDto).
 */
@Slf4j
@Service
public class LiveTrackingService {

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final JourneyRepository repository;
    private final RiskAnalyzerService analyzer;
    private final SimulationClock clock;
    private final TrackerProperties props;

    public LiveTrackingService(JourneyRepository repository, RiskAnalyzerService analyzer,
                               SimulationClock clock, TrackerProperties props) {
        this.repository = repository;
        this.analyzer = analyzer;
        this.clock = clock;
        this.props = props;
    }

    public SseEmitter subscribe(Long journeyId) {
        if (!repository.existsById(journeyId)) {
            throw new ResourceNotFoundException("Journey " + journeyId + " not found");
        }
        SseEmitter emitter = new SseEmitter(props.sseTimeoutMs());
        List<SseEmitter> list = emitters.computeIfAbsent(journeyId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable cleanup = () -> list.remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());
        try {
            emitter.send(SseEmitter.event().name("snapshot").data(snapshot(journeyId), MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            cleanup.run();
        }
        return emitter;
    }

    public void publishSnapshot(Long journeyId) {
        if (hasSubscribers(journeyId)) {
            send(journeyId, "snapshot", snapshot(journeyId));
        }
    }

    public void publishAlert(Long journeyId, AlertDto alert) {
        if (hasSubscribers(journeyId)) {
            send(journeyId, "alert", alert);
        }
    }

    /** Keeps connected dashboards in sync (and connections alive) while no simulation is ticking. */
    @Scheduled(fixedRate = 5000)
    public void heartbeat() {
        emitters.forEach((id, list) -> {
            if (!list.isEmpty() && !clock.isRunning(id)) {
                try {
                    publishSnapshot(id);
                } catch (Exception e) {
                    log.warn("Heartbeat failed for journey {}: {}", id, e.getMessage());
                }
            }
        });
    }

    public LiveUpdateDto snapshot(Long journeyId) {
        Journey journey = repository.findById(journeyId)
                .orElseThrow(() -> new ResourceNotFoundException("Journey " + journeyId + " not found"));
        JourneyAnalysis analysis = analyzer.analyze(journey);
        List<JourneyLeg> legs = journey.getLegs().stream()
                .sorted(java.util.Comparator.comparingInt(JourneyLeg::getLegOrder)).toList();
        OffsetDateTime now = clock.now(journeyId);

        List<LiveLegDto> liveLegs = new ArrayList<>();
        for (int i = 0; i < legs.size(); i++) {
            JourneyLeg l = legs.get(i);
            LegTimingDto t = analysis.timings().get(i);
            liveLegs.add(new LiveLegDto(l.getLegOrder(), l.getMode(), l.getStatus(), l.getOrigin(), l.getDestination(),
                    t.effectiveDeparture(), t.effectiveArrival(), l.getDelayMinutes()));
        }

        int current = -1;
        for (int i = 0; i < legs.size(); i++) {
            if (legs.get(i).getStatus() == LegStatus.IN_TRANSIT) {
                current = i;
                break;
            }
        }
        if (current < 0) {
            for (int i = 0; i < legs.size(); i++) {
                if (legs.get(i).getStatus() != LegStatus.COMPLETED) {
                    current = i;
                    break;
                }
            }
        }
        boolean allDone = current < 0;
        if (allDone) {
            current = legs.size() - 1;
        }

        double progress = 0;
        if (!analysis.timings().isEmpty()) {
            OffsetDateTime start = analysis.timings().get(0).effectiveDeparture();
            OffsetDateTime end = analysis.timings().get(analysis.timings().size() - 1).effectiveArrival();
            double total = Duration.between(start, end).getSeconds();
            double done = Duration.between(start, now).getSeconds();
            progress = total <= 0 ? 0 : Math.max(0, Math.min(100, done / total * 100));
        }
        if (allDone) {
            progress = 100;
        }

        Long secondsToConnection = null;
        OffsetDateTime connectionDeparture = null;
        List<ConnectionBufferDto> conns = analysis.risk().connections();
        if (!conns.isEmpty()) {
            connectionDeparture = conns.get(conns.size() - 1).departure();
            secondsToConnection = Duration.between(now, connectionDeparture).getSeconds();
        }

        return new LiveUpdateDto(journeyId, journey.getPassengerName(), journey.getStatus(),
                clock.isRunning(journeyId), clock.isSimulated(journeyId), props.simulationSpeedFactor(),
                now, Math.round(progress * 10) / 10.0, legs.get(current).getLegOrder(),
                describe(legs.get(current), liveLegs.get(current), allDone),
                secondsToConnection, connectionDeparture, analysis.risk(), liveLegs);
    }

    private static String describe(JourneyLeg leg, LiveLegDto live, boolean allDone) {
        if (allDone) {
            return "Journey complete. Arrived at " + leg.getDestination() + ".";
        }
        String who = leg.getOperatorName() != null ? leg.getOperatorName() : leg.getMode().name().replace('_', '/');
        if (leg.getStatus() == LegStatus.IN_TRANSIT) {
            return "Leg %d in transit: %s, %s to %s. ETA %s%s.".formatted(leg.getLegOrder(), who, leg.getOrigin(),
                    leg.getDestination(), hhmm(live.effectiveArrival()),
                    leg.getDelayMinutes() > 0 ? " (running " + leg.getDelayMinutes() + " min late)" : "");
        }
        return "Up next: leg %d, %s from %s at %s%s.".formatted(leg.getLegOrder(), who, leg.getOrigin(),
                hhmm(live.effectiveDeparture()),
                leg.getDelayMinutes() > 0 ? " (delayed " + leg.getDelayMinutes() + " min)" : "");
    }

    private boolean hasSubscribers(Long journeyId) {
        List<SseEmitter> list = emitters.get(journeyId);
        return list != null && !list.isEmpty();
    }

    private void send(Long journeyId, String event, Object payload) {
        List<SseEmitter> list = emitters.get(journeyId);
        if (list == null) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(event).data(payload, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                list.remove(emitter);
            }
        }
    }
}
