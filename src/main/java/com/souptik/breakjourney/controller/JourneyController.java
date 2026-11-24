package com.souptik.breakjourney.controller;

import com.souptik.breakjourney.dto.AlertDto;
import com.souptik.breakjourney.dto.DelayRequest;
import com.souptik.breakjourney.dto.JourneyDto;
import com.souptik.breakjourney.dto.RiskAnalysisDto;
import com.souptik.breakjourney.dto.SimulationStatusDto;
import com.souptik.breakjourney.dto.StatusRequest;
import com.souptik.breakjourney.service.JourneyService;
import com.souptik.breakjourney.service.LiveTrackingService;
import com.souptik.breakjourney.simulator.JourneySimulator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/journeys")
@Tag(name = "Journeys", description = "Break journey details, delays, simulation and live stream")
public class JourneyController {

    private final JourneyService journeyService;
    private final JourneySimulator simulator;
    private final LiveTrackingService liveTracking;

    public JourneyController(JourneyService journeyService, JourneySimulator simulator,
                             LiveTrackingService liveTracking) {
        this.journeyService = journeyService;
        this.simulator = simulator;
        this.liveTracking = liveTracking;
    }

    @Operation(summary = "List all journeys with legs, PNRs and calculated buffer metrics")
    @GetMapping
    public List<JourneyDto> all() {
        return journeyService.findAll();
    }

    @Operation(summary = "Get the full break journey (legs, bookings, delay-adjusted times, risk analysis)")
    @GetMapping("/{id}")
    public JourneyDto one(@PathVariable Long id) {
        return journeyService.findById(id);
    }

    @Operation(summary = "Get only the connection buffer and risk analysis")
    @GetMapping("/{id}/risk")
    public RiskAnalysisDto risk(@PathVariable Long id) {
        return journeyService.riskAnalysis(id);
    }

    @Operation(summary = "Report a delay on a leg and recalculate downstream buffers",
            description = "Sets the total delay (minutes versus timetable) on the leg. Delay on leg 2 (Vande Bharat) "
                    + "ripples to the Bhubaneswar transfer and the Baramunda connection for the 13:30 bus.")
    @PatchMapping("/{id}/legs/{legOrder}/delay")
    public JourneyDto delay(@PathVariable Long id, @PathVariable int legOrder,
                            @Valid @RequestBody DelayRequest request) {
        JourneyDto dto = journeyService.reportDelay(id, legOrder, request.delayMinutes());
        String title = request.delayMinutes() == 0
                ? "Delay cleared on leg " + legOrder
                : "Delay of %d min reported on leg %d".formatted(request.delayMinutes(), legOrder);
        liveTracking.publishAlert(id, AlertDto.fromRisk(title, dto.riskAnalysis()));
        liveTracking.publishSnapshot(id);
        return dto;
    }

    @Operation(summary = "Update a leg's status (SCHEDULED, IN_TRANSIT, DELAYED, COMPLETED)",
            description = "While a simulation is running, the simulator derives statuses from its clock and will overwrite manual changes.")
    @PatchMapping("/{id}/legs/{legOrder}/status")
    public JourneyDto status(@PathVariable Long id, @PathVariable int legOrder,
                             @Valid @RequestBody StatusRequest request) {
        JourneyDto dto = journeyService.updateLegStatus(id, legOrder, request.status());
        liveTracking.publishAlert(id, AlertDto.info("Leg " + legOrder + " status changed", "Status is now " + request.status() + "."));
        liveTracking.publishSnapshot(id);
        return dto;
    }

    @Operation(summary = "Start (or restart) the accelerated simulation of the whole Nov 23-24 journey")
    @PostMapping("/{id}/simulate/start")
    public SimulationStatusDto startSimulation(@PathVariable Long id) {
        return simulator.start(id);
    }

    @Operation(summary = "Stop the simulation and reset delays and statuses")
    @PostMapping("/{id}/simulate/reset")
    public SimulationStatusDto resetSimulation(@PathVariable Long id) {
        return simulator.reset(id);
    }

    @Operation(summary = "Current simulation state")
    @GetMapping("/{id}/simulate/status")
    public SimulationStatusDto simulationStatus(@PathVariable Long id) {
        return simulator.status(id, "Current simulation state.");
    }

    @Operation(summary = "Server-Sent Events stream of live progress",
            description = "Events: 'snapshot' (progress, active leg, ETAs, buffer, risk) and 'alert' (human readable notices).")
    @GetMapping(path = "/{id}/live-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter liveStream(@PathVariable Long id) {
        return liveTracking.subscribe(id);
    }
}
