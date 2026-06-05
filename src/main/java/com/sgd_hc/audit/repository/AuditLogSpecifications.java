package com.sgd_hc.audit.repository;

import com.sgd_hc.audit.dto.AuditFilterDto;
import com.sgd_hc.audit.entity.AuditLog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class AuditLogSpecifications {

    private AuditLogSpecifications() {}

    public static Specification<AuditLog> build(AuditFilterDto filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.tenantId() != null) {
                predicates.add(cb.equal(root.get("tenantId"), filter.tenantId()));
            }
            if (filter.userIdentifier() != null && !filter.userIdentifier().isBlank()) {
                String term = "%" + filter.userIdentifier().toLowerCase() + "%";
                Predicate matchUserName = cb.like(cb.lower(root.get("userName")), term);
                Predicate matchUserEmail = cb.like(cb.lower(root.get("userEmail")), term);
                
                // If it looks like a UUID, we can optionally match userId too. But for simplicity let's match string fields
                try {
                    java.util.UUID uuid = java.util.UUID.fromString(filter.userIdentifier());
                    predicates.add(cb.or(matchUserName, matchUserEmail, cb.equal(root.get("userId"), uuid)));
                } catch (IllegalArgumentException e) {
                    predicates.add(cb.or(matchUserName, matchUserEmail));
                }
            }
            if (filter.actionType() != null) {
                predicates.add(cb.equal(root.get("actionType"), filter.actionType()));
            }
            if (filter.resourceType() != null && !filter.resourceType().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("resourceType")), "%" + filter.resourceType().toLowerCase() + "%"));
            }
            if (filter.dateFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.dateFrom()));
            }
            if (filter.dateTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), filter.dateTo()));
            }

            query.orderBy(cb.desc(root.get("createdAt")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
