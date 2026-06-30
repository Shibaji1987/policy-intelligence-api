package com.shibajide.policyintelligence.security;

import com.shibajide.policyintelligence.retrieval.application.RetrievalFilters;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RetrievalAccessPolicy {

    private final boolean enforceHeaderScopes;

    public RetrievalAccessPolicy(@Value("${app.security.enforce-header-scopes:true}") boolean enforceHeaderScopes) {
        this.enforceHeaderScopes = enforceHeaderScopes;
    }

    public RetrievalFilters filters(
            String tenantId,
            String department,
            String region,
            String documentType,
            String classification
    ) {
        GovernanceContext context = GovernanceContextHolder.current();
        String effectiveTenant = firstNonBlank(context.tenantId(), tenantId, "default");
        String effectiveDepartment = firstNonBlank(department, null, null);
        String effectiveRegion = firstNonBlank(region, null, null);
        String effectiveClassification = firstNonBlank(classification, null, null);

        if (enforceHeaderScopes) {
            requireAllowed("department", effectiveDepartment, context.allowedDepartments());
            requireAllowed("region", effectiveRegion, context.allowedRegions());
            requireAllowed("classification", effectiveClassification, context.allowedClassifications());
        }

        return new RetrievalFilters(effectiveTenant, effectiveDepartment, effectiveRegion, documentType, effectiveClassification);
    }

    public String ingestionTenant(String requestedTenantId) {
        GovernanceContext context = GovernanceContextHolder.current();
        return firstNonBlank(context.tenantId(), requestedTenantId, "default");
    }

    private void requireAllowed(String field, String value, java.util.Set<String> allowedValues) {
        if (value == null) {
            return;
        }
        if (allowedValues.isEmpty()) {
            throw new IllegalArgumentException("Access scope missing for " + field);
        }
        if (!allowedValues.contains(value)) {
            throw new IllegalArgumentException("Access denied for " + field + ": " + value);
        }
    }

    private String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first.strip();
        }
        if (second != null && !second.isBlank()) {
            return second.strip();
        }
        return fallback;
    }
}
