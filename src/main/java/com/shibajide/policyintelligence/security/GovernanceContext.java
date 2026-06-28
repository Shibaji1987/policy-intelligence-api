package com.shibajide.policyintelligence.security;

import java.util.Set;

public record GovernanceContext(
        String userId,
        String tenantId,
        Set<String> allowedDepartments,
        Set<String> allowedRegions,
        Set<String> allowedClassifications
) {
    public static GovernanceContext anonymous() {
        return new GovernanceContext("anonymous", "default", Set.of(), Set.of(), Set.of());
    }
}
