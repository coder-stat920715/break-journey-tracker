package com.souptik.breakjourney.service;

import com.souptik.breakjourney.dto.JourneyDto;
import com.souptik.breakjourney.dto.RiskAnalysisDto;
import com.souptik.breakjourney.entity.Journey;
import com.souptik.breakjourney.entity.JourneyLeg;
import com.souptik.breakjourney.enums.LegStatus;
import com.souptik.breakjourney.exception.ResourceNotFoundException;
import com.souptik.breakjourney.repository.JourneyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class JourneyService {

    private final JourneyRepository repository;
    private final JourneyMapper mapper;

    public JourneyService(JourneyRepository repository, JourneyMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public List<JourneyDto> findAll() {
        return repository.findAll().stream().map(mapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    public JourneyDto findById(Long id) {
        return mapper.toDto(load(id));
    }

    @Transactional(readOnly = true)
    public RiskAnalysisDto riskAnalysis(Long id) {
        return mapper.toDto(load(id)).riskAnalysis();
    }

    /** Sets the delay on a leg (replacing any previous value) and returns the recalculated journey. */
    public JourneyDto reportDelay(Long id, int legOrder, int delayMinutes) {
        Journey journey = load(id);
        JourneyLeg leg = findLeg(journey, legOrder);
        leg.setDelayMinutes(delayMinutes);
        if (delayMinutes > 0 && leg.getStatus() == LegStatus.SCHEDULED) {
            leg.setStatus(LegStatus.DELAYED);
        } else if (delayMinutes == 0 && leg.getStatus() == LegStatus.DELAYED) {
            leg.setStatus(LegStatus.SCHEDULED);
        }
        journey.refreshStatus();
        return mapper.toDto(repository.save(journey));
    }

    public JourneyDto updateLegStatus(Long id, int legOrder, LegStatus status) {
        Journey journey = load(id);
        findLeg(journey, legOrder).setStatus(status);
        journey.refreshStatus();
        return mapper.toDto(repository.save(journey));
    }

    private Journey load(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Journey " + id + " not found"));
    }

    private JourneyLeg findLeg(Journey journey, int legOrder) {
        return journey.getLegs().stream()
                .filter(l -> l.getLegOrder() == legOrder)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Leg " + legOrder + " not found on journey " + journey.getId()));
    }
}
