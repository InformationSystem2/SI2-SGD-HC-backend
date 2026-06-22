package com.sgd_hc.security.exception;

public class PlanFeatureDisabledException extends RuntimeException {

    private final String featureName;
    private final String planName;

    public PlanFeatureDisabledException(String featureName, String planName) {
        super(String.format(
                "Esta función (%s) no está disponible en el plan %s. Actualice su plan para acceder a esta funcionalidad.",
                featureName, planName));
        this.featureName = featureName;
        this.planName = planName;
    }

    public String getFeatureName() {
        return featureName;
    }

    public String getPlanName() {
        return planName;
    }
}
