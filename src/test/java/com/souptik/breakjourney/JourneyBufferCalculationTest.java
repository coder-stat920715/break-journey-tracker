package com.souptik.breakjourney;

import com.souptik.breakjourney.config.JourneySeedData;
import com.souptik.breakjourney.config.TrackerProperties;
import com.souptik.breakjourney.dto.RiskAnalysisDto;
import com.souptik.breakjourney.entity.Journey;
import com.souptik.breakjourney.enums.RiskStatus;
import com.souptik.breakjourney.service.JourneyAnalysis;
import com.souptik.breakjourney.service.RiskAnalyzerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class JourneyBufferCalculationTest {

    private final RiskAnalyzerService analyzer =
            new RiskAnalyzerService(new TrackerProperties(600, 10, 40, 15, 1_800_000L));

    private JourneyAnalysis analyzeWithDelay(int legIndex, int minutes) {
        Journey journey = JourneySeedData.kolkataToHyderabad();
        journey.getLegs().get(legIndex).setDelayMinutes(minutes);
        return analyzer.analyze(journey);
    }

    private static Instant at(String hhmm) {
        return OffsetDateTime.parse("2026-11-23T" + hhmm + ":00+05:30").toInstant();
    }

    @Test
    void noDelayKeepsThePlannedFiftyMinuteBuffer() {
        RiskAnalysisDto risk = analyzeWithDelay(1, 0).risk();
        assertThat(risk.bufferMinutes()).isEqualTo(50L);
        assertThat(risk.riskStatus()).isEqualTo(RiskStatus.SAFE);
        assertThat(risk.busDeparture().toInstant()).isEqualTo(at("13:30"));
    }

    @Test
    void thirtyMinuteVandeBharatDelayIsAbsorbedAndStaysSafe() {
        RiskAnalysisDto risk = analyzeWithDelay(1, 30).risk();
        assertThat(risk.adjustedBhubaneswarArrival().toInstant()).isEqualTo(at("11:58"));
        assertThat(risk.adjustedTransferDeparture().toInstant()).isEqualTo(at("12:10"));
        assertThat(risk.adjustedBaramundaArrival().toInstant()).isEqualTo(at("12:40"));
        assertThat(risk.bufferMinutes()).isEqualTo(50L);
        assertThat(risk.riskStatus()).isEqualTo(RiskStatus.SAFE);
        assertThat(risk.connectionMissed()).isFalse();
    }

    @Test
    void ninetyMinuteDelayIsCriticalAndRecommendsKhandagiriBoarding() {
        RiskAnalysisDto risk = analyzeWithDelay(1, 90).risk();
        assertThat(risk.adjustedBhubaneswarArrival().toInstant()).isEqualTo(at("12:58"));
        assertThat(risk.adjustedTransferDeparture().toInstant()).isEqualTo(at("13:08"));
        assertThat(risk.adjustedBaramundaArrival().toInstant()).isEqualTo(at("13:38"));
        assertThat(risk.bufferMinutes()).isEqualTo(-8L);
        assertThat(risk.riskStatus()).isEqualTo(RiskStatus.CRITICAL_MISSED_CONNECTION);
        assertThat(risk.connectionMissed()).isTrue();
        assertThat(risk.recommendations())
                .anyMatch(r -> r.contains("Alert Shyamoli Paribahan operator and request boarding at Khandagiri NH-16 bypass"));
    }

    @ParameterizedTest(name = "train delay {0} min -> buffer {1} min -> {2}")
    @CsvSource({
            "30,50,SAFE",
            "42,40,SAFE",
            "43,39,TIGHT_CONNECTION",
            "50,32,TIGHT_CONNECTION",
            "65,17,TIGHT_CONNECTION",
            "67,15,TIGHT_CONNECTION",
            "68,14,CRITICAL_MISSED_CONNECTION",
            "70,12,CRITICAL_MISSED_CONNECTION",
            "90,-8,CRITICAL_MISSED_CONNECTION"
    })
    void riskThresholdsAtTheBoundaries(int delay, long expectedBuffer, RiskStatus expectedRisk) {
        RiskAnalysisDto risk = analyzeWithDelay(1, delay).risk();
        assertThat(risk.bufferMinutes()).isEqualTo(expectedBuffer);
        assertThat(risk.riskStatus()).isEqualTo(expectedRisk);
    }

    @Test
    void lateCabToHowrahEatsTheTrainBoardingBuffer() {
        RiskAnalysisDto risk = analyzeWithDelay(0, 70).risk();
        assertThat(risk.connections().get(0).bufferMinutes()).isEqualTo(0L);
        assertThat(risk.connections().get(0).riskStatus()).isEqualTo(RiskStatus.CRITICAL_MISSED_CONNECTION);
        assertThat(risk.riskStatus()).isEqualTo(RiskStatus.CRITICAL_MISSED_CONNECTION);
    }
}
