package com.souptik.breakjourney;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class JourneyApiIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @BeforeEach
    void reset() throws Exception {
        mvc.perform(post("/api/v1/journeys/1/simulate/reset")).andExpect(status().isOk());
    }

    @Test
    void seededJourneyHasSouptikAndFourLegs() throws Exception {
        mvc.perform(get("/api/v1/journeys/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passenger.name").value("Souptik Tat"))
                .andExpect(jsonPath("$.legs.length()").value(4))
                .andExpect(jsonPath("$.legs[1].bookingReference").value("6951647716"))
                .andExpect(jsonPath("$.legs[3].ticketNumber").value("TVBQ22662970"))
                .andExpect(jsonPath("$.riskAnalysis.bufferMinutes").value(50));
    }

    @Test
    void thirtyMinuteDelayStaysSafeAndNinetyMinuteDelayIsCritical() throws Exception {
        mvc.perform(patch("/api/v1/journeys/1/legs/2/delay")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"delayMinutes\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskAnalysis.riskStatus").value("SAFE"))
                .andExpect(jsonPath("$.legs[1].status").value("DELAYED"))
                .andExpect(jsonPath("$.status").value("DELAYED"));

        mvc.perform(patch("/api/v1/journeys/1/legs/2/delay")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"delayMinutes\":90}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.riskAnalysis.bufferMinutes").value(-8))
                .andExpect(jsonPath("$.riskAnalysis.riskStatus").value("CRITICAL_MISSED_CONNECTION"));
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        mvc.perform(patch("/api/v1/journeys/1/legs/2/delay")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"delayMinutes\":-5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/v1/journeys/1/legs/9/delay")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"delayMinutes\":10}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/v1/journeys/1/legs/2/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"FLYING\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void legStatusCanBeUpdated() throws Exception {
        mvc.perform(patch("/api/v1/journeys/1/legs/1/status")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_TRANSIT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legs[0].status").value("IN_TRANSIT"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    }
}
