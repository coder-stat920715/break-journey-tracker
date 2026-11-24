package com.souptik.breakjourney.simulator;

import com.souptik.breakjourney.config.TrackerProperties;
import com.souptik.breakjourney.dto.AlertDto;
import com.souptik.breakjourney.dto.SimulationStatusDto;
import com.souptik.breakjourney.entity.Journey;
import com.souptik.breakjourney.entity.JourneyLeg;
import com.souptik.breakjourney.enums.LegStatus;
import com.souptik.breakjourney.exception.ResourceNotFoundException;
import com.souptik.breakjourney.repository.JourneyRepository;
import com.souptik.breakjourney.service.JourneyAnalysis;
import com.souptik.breakjourney.service.LiveTrackingService;
import com.souptik.breakjourney.service.RiskAnalyzerService;
import com.souptik.breakjourney.service.TimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static com.souptik.breakjourney.service.TimeUtil.hhmm;

/**
 * Accelerated real-time simulation. Every second of real time advances the virtual clock by
 * {@code tracker.simulation-speed-factor} seconds; leg statuses are derived from the delay-adjusted
 * schedule, saved to the database and pushed to SSE subscribers.
 */
@Slf4j
@Component
public class JourneySimulator {

    private record ClockResult(List<String> events, boolean allCompleted) {
    }

    private final JourneyRepository repository;
    private final RiskAnalyzerService analyzer;
    private final SimulationClock clock;
    private final LiveTrackingService live;
    private final TrackerProperties props;
    private final TransactionTemplate tx;

    public JourneySimulator(JourneyRepository repository, RiskAnalyzerService analyzer, SimulationClock clock,
                            LiveTrackingService live, TrackerProperties props, TransactionTemplate tx) {
        this.repository = repository;
        this.analyzer = analyzer;
        this.clock = clock;
        this.live = live;
        this.props = props;
        this.tx = tx;
    }

    /** Starts (or restarts) the simulation from 15 minutes before the recommended cab booking time. */
    public synchronized SimulationStatusDto start(Long journeyId) {
        OffsetDateTime startTime = tx.execute(ts -> {
            Journey journey = load(journeyId);
            JourneyLeg first = journey.getLegs().stream()
                    .min(Comparator.comparingInt(JourneyLeg::getLegOrder)).orElseThrow();
            OffsetDateTime base = first.getRecommendedBookingTime() != null
                    ? first.getRecommendedBookingTime() : first.getScheduledDeparture();
            return TimeUtil.ist(base).minusMinutes(15);
        });
        clock.start(journeyId, startTime);
        applyClock(journeyId, startTime);
        live.publishAlert(journeyId, AlertDto.info("Simulation started",
                "Virtual clock starts at %s, running %dx faster than real time."
                        .formatted(hhmm(startTime), props.simulationSpeedFactor())));
        live.publishSnapshot(journeyId);
        return status(journeyId, "Simulation started.");
    }

    /** Stops the simulation and clears all delays and leg statuses. */
    public synchronized SimulationStatusDto reset(Long journeyId) {
        clock.clear(journeyId);
        tx.executeWithoutResult(ts -> {
            Journey journey = load(journeyId);
            for (JourneyLeg leg : journey.getLegs()) {
                leg.setDelayMinutes(0);
                leg.setStatus(LegStatus.SCHEDULED);
            }
            journey.refreshStatus();
            repository.save(journey);
        });
        live.publishAlert(journeyId, AlertDto.info("Journey reset", "Delays cleared and all legs set back to SCHEDULED."));
        live.publishSnapshot(journeyId);
        return status(journeyId, "Simulation reset.");
    }

    public SimulationStatusDto status(Long journeyId, String message) {
        if (!repository.existsById(journeyId)) {
            throw new ResourceNotFoundException("Journey " + journeyId + " not found");
        }
        return clock.find(journeyId)
                .map(s -> new SimulationStatusDto(journeyId, s.isRunning(), s.isFinished(), s.getVirtualTime(),
                        props.simulationSpeedFactor(), message))
                .orElse(new SimulationStatusDto(journeyId, false, false, null, props.simulationSpeedFactor(), message));
    }

    @Scheduled(fixedRate = 1000)
    public synchronized void tick() {
        for (Map.Entry<Long, SimulationClock.State> entry : clock.all().entrySet()) {
            Long id = entry.getKey();
            SimulationClock.State state = entry.getValue();
            if (!state.isRunning()) {
                continue;
            }
            try {
                state.setVirtualTime(state.getVirtualTime().plusSeconds(props.simulationSpeedFactor()));
                ClockResult result = applyClock(id, state.getVirtualTime());
                for (String event : result.events()) {
                    live.publishAlert(id, AlertDto.info("Progress update", event));
                }
                if (result.allCompleted()) {
                    state.setRunning(false);
                    state.setFinished(true);
                    live.publishAlert(id, AlertDto.info("Journey complete",
                            "Simulation finished at virtual time " + hhmm(state.getVirtualTime()) + "."));
                }
                live.publishSnapshot(id);
            } catch (Exception e) {
                log.error("Simulation tick failed for journey {}; stopping simulation", id, e);
                state.setRunning(false);
            }
        }
    }

    private ClockResult applyClock(Long journeyId, OffsetDateTime now) {
        return tx.execute(ts -> {
            Journey journey = load(journeyId);
            JourneyAnalysis analysis = analyzer.analyze(journey);
            List<JourneyLeg> legs = journey.getLegs().stream()
                    .sorted(Comparator.comparingInt(JourneyLeg::getLegOrder)).toList();
            List<String> events = new ArrayList<>();
            boolean all = true;
            for (int i = 0; i < legs.size(); i++) {
                JourneyLeg leg = legs.get(i);
                var timing = analysis.timings().get(i);
                LegStatus next;
                if (!now.isBefore(timing.effectiveArrival())) {
                    next = LegStatus.COMPLETED;
                } else if (!now.isBefore(timing.effectiveDeparture())) {
                    next = LegStatus.IN_TRANSIT;
                } else if (leg.getDelayMinutes() > 0 || timing.effectiveDeparture().isAfter(timing.scheduledDeparture())) {
                    next = LegStatus.DELAYED;
                } else {
                    next = LegStatus.SCHEDULED;
                }
                if (next != LegStatus.COMPLETED) {
                    all = false;
                }
                if (next != leg.getStatus()) {
                    if (next == LegStatus.IN_TRANSIT) {
                        events.add("Leg %d departed: %s to %s.".formatted(leg.getLegOrder(), leg.getOrigin(), leg.getDestination()));
                    } else if (next == LegStatus.COMPLETED) {
                        events.add("Leg %d arrived at %s (%s).".formatted(leg.getLegOrder(), leg.getDestination(),
                                hhmm(timing.effectiveArrival())));
                    }
                    leg.setStatus(next);
                }
            }
            journey.refreshStatus();
            repository.save(journey);
            return new ClockResult(events, all);
        });
    }

    private Journey load(Long journeyId) {
        return repository.findById(journeyId)
                .orElseThrow(() -> new ResourceNotFoundException("Journey " + journeyId + " not found"));
    }
}
