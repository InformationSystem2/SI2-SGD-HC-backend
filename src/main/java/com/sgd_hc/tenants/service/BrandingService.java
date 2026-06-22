package com.sgd_hc.tenants.service;

import com.sgd_hc.audit.annotation.Auditable;
import com.sgd_hc.audit.entity.enums.ActionType;
import com.sgd_hc.audit.service.AuditableService;

import com.sgd_hc.documents.service.FileStorageService;
import com.sgd_hc.tenants.entity.Tenant;
import com.sgd_hc.tenants.repository.TenantRepository;
import com.sgd_hc.tenants.utils.ColorUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class BrandingService implements AuditableService<String, Map<String, Object>> {

    private final TenantRepository tenantRepository;
    private final ObjectMapper objectMapper;
    private final FileStorageService fileStorageService;
    private final PlanFeatureValidator planFeatureValidator;

    @Transactional(readOnly = true)
    public Map<String, Object> getBrandingByTenantSlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return loadDefaultBranding();
        }
        Optional<Tenant> opt = tenantRepository.findBySlug(slug);
        if (opt.isEmpty()) return loadDefaultBranding();
        Tenant t = opt.get();
        Map<String, Object> settings = t.getSettings();

        Map<String, Object> brandingMap;
        if (settings != null && settings.containsKey("branding")) {
            Object branding = settings.get("branding");
            if (branding instanceof Map) {
                brandingMap = normalizeBrandingMap((Map<String, Object>) branding);
            } else {
                brandingMap = new HashMap<>(loadDefaultBranding());
            }
        } else {
            brandingMap = new HashMap<>(loadDefaultBranding());
        }

        String logoUrl = t.getLogoUrl();
        if (logoUrl != null && !logoUrl.isBlank()) {
            brandingMap.put("logo_url", logoUrl);
        }

        computeDerived(brandingMap);
        return brandingMap;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeBrandingMap(Map<String, Object> branding) {
        if (branding.containsKey("branding") && branding.get("branding") instanceof Map) {
            Map<String, Object> inner = (Map<String, Object>) branding.get("branding");
            if (inner.containsKey("tokens") || inner.containsKey("version")) {
                return new HashMap<>(inner);
            }
        }
        return new HashMap<>(branding);
    }

    private Map<String, Object> loadDefaultBranding() {
        try {
            ClassPathResource res = new ClassPathResource("default-branding.json");
            Map<String, Object> map = objectMapper.readValue(res.getInputStream(), Map.class);
            computeDerived(map);
            return map;
        } catch (IOException e) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private void computeDerived(Map<String, Object> branding) {
        try {
            Map<String, Object> tokens = (Map<String, Object>) branding.get("tokens");
            if (tokens == null) return;
            Object colorsObj = tokens.get("colors");
            if (colorsObj == null) return;

            if (colorsObj instanceof Map) {
                Map<String, Object> colors = (Map<String, Object>) colorsObj;
                if (colors.containsKey("light") && colors.containsKey("dark")) {
                    Map<String, Object> lightColors = (Map<String, Object>) colors.get("light");
                    Map<String, Object> darkColors = (Map<String, Object>) colors.get("dark");
                    computeColorDerived(lightColors);
                    computeColorDerived(darkColors);
                } else {
                    computeColorDerived(colors);
                }
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void computeColorDerived(Map<String, Object> colors) {
        String primary = (String) colors.get("primary");
        if (primary != null && primary.startsWith("#")) {
            colors.put("primaryHover", ColorUtils.darkenHex(primary, 0.12));
            colors.put("onPrimary", ColorUtils.determineTextColor(primary));
        }
    }

    @Transactional
    @Auditable(resourceType = "BRANDING", actionType = ActionType.UPDATE, idParamName = "slug")
    public Map<String, Object> updateBrandingBySlug(String slug, Map<String, Object> brandingPayload) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado: " + slug));

        planFeatureValidator.checkCustomBranding(tenant.getId());

        Map<String, Object> settings = tenant.getSettings();
        if (settings == null) settings = new HashMap<>();
        computeDerived(brandingPayload);
        settings.put("branding", brandingPayload);
        tenant.setSettings(settings);
        tenantRepository.saveAndFlush(tenant);
        return brandingPayload;
    }

    @Transactional
    @Auditable(resourceType = "BRANDING_LOGO", actionType = ActionType.UPDATE, idParamName = "slug")
    public Map<String, Object> uploadLogo(String slug, MultipartFile file) throws IOException {
        fileStorageService.validateLogo(file);
        Tenant t = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Tenant no encontrado: " + slug));

        planFeatureValidator.checkCustomBranding(t.getId());

        String relativePath = fileStorageService.upload(file, "branding/" + slug);
        String url = fileStorageService.getUrl(relativePath);

        t.setLogoUrl(url);
        tenantRepository.saveAndFlush(t);

        Map<String, Object> result = new HashMap<>();
        result.put("url", url);
        return result;
    }

    @Override
    public Map<String, Object> getEntity(String slug) {
        return getBrandingByTenantSlug(slug);
    }

    @Override
    public Map<String, Object> toAuditMap(Map<String, Object> entity) {
        return entity;
    }    
}