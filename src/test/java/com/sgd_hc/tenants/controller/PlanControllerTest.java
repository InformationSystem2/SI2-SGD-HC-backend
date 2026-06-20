package com.sgd_hc.tenants.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sgd_hc.tenants.dto.PlanDto;
import com.sgd_hc.tenants.dto.PlanUpdateDto;
import com.sgd_hc.tenants.service.PlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class PlanControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PlanService planService;

    @InjectMocks
    private PlanController planController;

    private ObjectMapper objectMapper = new ObjectMapper();

    private PlanDto basicPlanDto;
    private UUID planId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(planController).build();

        planId = UUID.randomUUID();
        basicPlanDto = new PlanDto(
                planId,
                "BASIC",
                "Básico",
                "Plan gratuito",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                30,
                3,
                1,
                Map.of("maxUsers", 10L),
                Map.of("dicom_imaging", false)
        );
    }

    @Test
    @DisplayName("GET /api/plans debe retornar lista de planes")
    void getAllPlans() throws Exception {
        when(planService.getAllActivePlans()).thenReturn(List.of(basicPlanDto));

        mockMvc.perform(get("/api/plans")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("BASIC"))
                .andExpect(jsonPath("$[0].displayName").value("Básico"))
                .andExpect(jsonPath("$[0].limits.maxUsers").value(10));
    }

    @Test
    @DisplayName("PUT /api/plans/admin/{id} debe actualizar el plan")
    void updatePlan() throws Exception {
        PlanUpdateDto updateDto = new PlanUpdateDto(
                "Básico Pro", null, null, null, null, null, null, null
        );

        PlanDto updatedDto = new PlanDto(
                planId, "BASIC", "Básico Pro", "Plan gratuito",
                BigDecimal.ZERO, BigDecimal.ZERO, 30, 3, 1,
                Map.of("maxUsers", 10L), Map.of("dicom_imaging", false)
        );

        when(planService.updatePlan(eq(planId), any(PlanUpdateDto.class))).thenReturn(updatedDto);

        mockMvc.perform(put("/api/plans/admin/{id}", planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Básico Pro"));
    }
}
