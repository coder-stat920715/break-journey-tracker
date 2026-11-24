package com.souptik.breakjourney.service;

import com.souptik.breakjourney.dto.LegTimingDto;
import com.souptik.breakjourney.dto.RiskAnalysisDto;

import java.util.List;

/** Result of the delay ripple calculation: per-leg timings plus the connection risk summary. */
public record JourneyAnalysis(List<LegTimingDto> timings, RiskAnalysisDto risk) {
}
