package com.sgd_hc.tenants.service;

import com.sgd_hc.tenants.dto.PlanDto;
import com.sgd_hc.tenants.dto.PlanUpdateDto;
import com.sgd_hc.tenants.entity.Plan;
import com.sgd_hc.tenants.entity.PlanFeature;
import com.sgd_hc.tenants.entity.PlanLimit;
import com.sgd_hc.tenants.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    public static final int DEFAULT_GRACE_PERIOD_DAYS = 3;

    private final PlanRepository planRepository;

    @Transactional(readOnly = true)
    public List<PlanDto> getAllActivePlans() {
        return planRepository.findAllByIsActiveTrueOrderBySortOrderAsc()
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanDto getPlanByName(String name) {
        Plan plan = planRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Plan no encontrado: " + name));
        return toDto(plan);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getLimitsForPlan(String planName) {
        Plan plan = planRepository.findByName(planName)
                .orElseThrow(() -> new IllegalArgumentException("Plan no encontrado: " + planName));
        return plan.getLimits().stream()
                .collect(Collectors.toMap(
                        l -> l.getResourceKey(),
                        l -> l.getResourceValue()
                ));
    }

    @Transactional(readOnly = true)
    public Map<String, Boolean> getFeaturesForPlan(String planName) {
        Plan plan = planRepository.findByName(planName)
                .orElseThrow(() -> new IllegalArgumentException("Plan no encontrado: " + planName));
        return plan.getFeatures().stream()
                .collect(Collectors.toMap(
                        f -> f.getFeatureKey(),
                        f -> f.getIsEnabled()
                ));
    }

    @Transactional(readOnly = true)
    public Plan getPlanEntity(String name) {
        return planRepository.findByName(name)
                .orElseThrow(() -> new IllegalArgumentException("Plan no encontrado: " + name));
    }

    @Transactional(readOnly = true)
    public Long getLimitValue(String planName, String resourceKey) {
        Map<String, Long> limits = getLimitsForPlan(planName);
        return limits.getOrDefault(resourceKey, 0L);
    }

    @Transactional(readOnly = true)
    public Long getLimitOrDefault(String planName, String resourceKey, Long defaultValue) {
        try {
            Map<String, Long> limits = getLimitsForPlan(planName);
            Long value = limits.get(resourceKey);
            return (value != null && value != -1L) ? value : defaultValue;
        } catch (IllegalArgumentException e) {
            return defaultValue;
        }
    }

    @Transactional(readOnly = true)
    public int getGracePeriodDaysOrDefault(String planName, int defaultValue) {
        try {
            Plan plan = planRepository.findByName(planName)
                    .orElse(null);
            return (plan != null && plan.getGracePeriodDays() > 0)
                    ? plan.getGracePeriodDays()
                    : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @Transactional(readOnly = true)
    public boolean isFeatureEnabled(String planName, String featureKey) {
        Map<String, Boolean> features = getFeaturesForPlan(planName);
        return features.getOrDefault(featureKey, false);
    }

    @Transactional
    public PlanDto updatePlan(UUID planId, PlanUpdateDto dto) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("Plan no encontrado: " + planId));

        if (dto.displayName() != null) plan.setDisplayName(dto.displayName());
        if (dto.description() != null) plan.setDescription(dto.description());
        if (dto.priceMonthly() != null) plan.setPriceMonthly(dto.priceMonthly());
        if (dto.priceYearly() != null) plan.setPriceYearly(dto.priceYearly());
        if (dto.cycleDays() != null) plan.setCycleDays(dto.cycleDays());
        if (dto.gracePeriodDays() != null) plan.setGracePeriodDays(dto.gracePeriodDays());

        if (dto.limits() != null) {
            dto.limits().forEach((key, value) -> {
                plan.getLimits().stream()
                        .filter(l -> l.getResourceKey().equals(key))
                        .findFirst()
                        .ifPresent(l -> l.setResourceValue(value));
            });
        }

        if (dto.features() != null) {
            dto.features().forEach((key, enabled) -> {
                plan.getFeatures().stream()
                        .filter(f -> f.getFeatureKey().equals(key))
                        .findFirst()
                        .ifPresent(f -> f.setIsEnabled(enabled));
            });
        }

        planRepository.save(plan);
        log.info("Plan {} actualizado: displayName={}, limits={}, features={}",
                plan.getName(), dto.displayName(), dto.limits(), dto.features());

        return toDto(plan);
    }

    public PlanDto toDto(Plan plan) {
        Map<String, Long> limits = new LinkedHashMap<>();
        plan.getLimits().forEach(l -> limits.put(l.getResourceKey(), l.getResourceValue()));

        Map<String, Boolean> features = new LinkedHashMap<>();
        plan.getFeatures().forEach(f -> features.put(f.getFeatureKey(), f.getIsEnabled()));

        return new PlanDto(
                plan.getId(),
                plan.getName(),
                plan.getDisplayName(),
                plan.getDescription(),
                plan.getPriceMonthly(),
                plan.getPriceYearly(),
                plan.getCycleDays(),
                plan.getGracePeriodDays(),
                plan.getSortOrder(),
                limits,
                features
        );
    }
}
