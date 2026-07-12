package com.cuenti.app.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "demo")
class ForecastApiControllerTest {

    @Autowired MockMvc mockMvc;

    @Test
    void defaultsToCurrentYearWithTwelveMonths() throws Exception {
        mockMvc.perform(get("/api/forecasts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(Year.now().getValue()))
                .andExpect(jsonPath("$.months.length()").value(12))
                .andExpect(jsonPath("$.currency").isNotEmpty());
    }

    @Test
    void explicitYearRespected() throws Exception {
        mockMvc.perform(get("/api/forecasts").param("year", "2030"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2030))
                .andExpect(jsonPath("$.months[0].month").value("2030-01"));
    }
}
