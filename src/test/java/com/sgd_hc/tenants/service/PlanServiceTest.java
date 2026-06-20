package com.sgd_hc.tenants.service;

import com.sgd_hc.tenants.dto.PlanDto;
import com.sgd_hc.tenants.dto.PlanUpdateDto;
import com.sgd_hc.tenants.entity.Plan;
import com.sgd_hc.tenants.entity.PlanFeature;
import com.sgd_hc.tenants.entity.PlanLimit;
import com.sgd_hc.tenants.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private PlanRepository planRepository;

    @InjectMocks
    private PlanService planService;

    private Plan basicPlan;
    private PlanLimit userLimit;
    private PlanFeature dicomFeature;

    @BeforeEach
    void setUp() {
        userLimit = PlanLimit.builder()
                .resourceKey("maxUsers")
                .resourceValue(10L)
                .build();

        dicomFeature = PlanFeature.builder()
                .featureKey("dicom_imaging")
                .isEnabled(true)
                .build();

        basicPlan = Plan.builder()
                .id(UUID.randomUUID())
                .name("BASIC")
                .displayName("Básico")
                .priceMonthly(BigDecimal.ZERO)
                .priceYearly(BigDecimal.ZERO)
                .cycleDays(30)
                .gracePeriodDays(3)
                .sortOrder(1)
                .isActive(true)
                .limits(List.of(userLimit))
                .features(List.of(dicomFeature))
                .build();

        userLimit.setPlan(basicPlan);
        dicomFeature.setPlan(basicPlan);
    }

    @Nested
    @DisplayName("getAllActivePlans")
    class GetAllActivePlans {
        @Test
        @DisplayName("Debe retornar los planes activos como DTOs")
        void shouldReturnActivePlans() {
            when(planRepository.findAllByIsActiveTrueOrderBySortOrderAsc())
                    .thenReturn(List.of(basicPlan));

            List<PlanDto> result = planService.getAllActivePlans();

            assertEquals(1, result.size());
            PlanDto dto = result.get(0);
            assertEquals("BASIC", dto.name());
            assertEquals("Básico", dto.displayName());
            assertEquals(10L, dto.limits().get("maxUsers"));
            assertTrue(dto.features().get("dicom_imaging"));
        }
    }

    @Nested
    @DisplayName("getPlanByName")
    class GetPlanByName {
        @Test
        @DisplayName("Debe retornar el plan si existe")
        void shouldReturnPlanIfExists() {
            when(planRepository.findByName("BASIC")).thenReturn(Optional.of(basicPlan));

            PlanDto result = planService.getPlanByName("BASIC");

            assertNotNull(result);
            assertEquals("BASIC", result.name());
        }

        @Test
        @DisplayName("Debe lanzar excepción si el plan no existe")
        void shouldThrowExceptionIfPlanNotFound() {
            when(planRepository.findByName("NON_EXISTENT")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class, () -> planService.getPlanByName("NON_EXISTENT"));
        }
    }

    @Nested
    @DisplayName("getLimitValue")
    class GetLimitValue {
        @Test
        @DisplayName("Debe retornar el valor del límite correctamente")
        void shouldReturnLimitValue() {
            when(planRepository.findByName("BASIC")).thenReturn(Optional.of(basicPlan));

            Long value = planService.getLimitValue("BASIC", "maxUsers");

            assertEquals(10L, value);
        }

        @Test
        @DisplayName("Debe retornar 0 si el recurso no existe")
        void shouldReturnZeroIfResourceNotExists() {
            when(planRepository.findByName("BASIC")).thenReturn(Optional.of(basicPlan));

            Long value = planService.getLimitValue("BASIC", "non_existent_limit");

            assertEquals(0L, value);
        }
    }

    @Nested
    @DisplayName("updatePlan")
    class UpdatePlan {
        @Test
        @DisplayName("Debe actualizar propiedades, límites y features del plan")
        void shouldUpdatePlanProperties() {
            when(planRepository.findById(basicPlan.getId())).thenReturn(Optional.of(basicPlan));
            when(planRepository.save(any(Plan.class))).thenReturn(basicPlan);

            PlanUpdateDto updateDto = new PlanUpdateDto(
                    "Básico Pro",
                    "New Desc",
                    new BigDecimal("10.00"),
                    new BigDecimal("100.00"),
                    30,
                    5,
                    Map.of("maxUsers", 20L),
                    Map.of("dicom_imaging", false)
            );

            PlanDto result = planService.updatePlan(basicPlan.getId(), updateDto);

            assertEquals("Básico Pro", result.displayName());
            assertEquals(20L, result.limits().get("maxUsers"));
            assertFalse(result.features().get("dicom_imaging"));
            verify(planRepository).save(basicPlan);
        }

        @Test
        @DisplayName("Debe lanzar excepción al actualizar un plan que no existe")
        void shouldThrowExceptionWhenUpdatingNonExistentPlan() {
            UUID unknownId = UUID.randomUUID();
            when(planRepository.findById(unknownId)).thenReturn(Optional.empty());

            PlanUpdateDto updateDto = new PlanUpdateDto(null, null, null, null, null, null, null, null);

            assertThrows(IllegalArgumentException.class, () -> planService.updatePlan(unknownId, updateDto));
            verify(planRepository, never()).save(any());
        }
    }
}
