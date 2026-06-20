package com.sgd_hc.security.exception;

public class PlanLimitExceededException extends RuntimeException {

    private final String resourceType;
    private final long currentCount;
    private final long maxLimit;

    public PlanLimitExceededException(String resourceType, long currentCount, long maxLimit) {
        super(String.format(
                "Límite del plan alcanzado: ha alcanzado el máximo de %s (%d/%d). Actualice su plan para continuar.",
                resourceType, currentCount, maxLimit));
        this.resourceType = resourceType;
        this.currentCount = currentCount;
        this.maxLimit = maxLimit;
    }

    public String getResourceType() {
        return resourceType;
    }

    public long getCurrentCount() {
        return currentCount;
    }

    public long getMaxLimit() {
        return maxLimit;
    }
}
