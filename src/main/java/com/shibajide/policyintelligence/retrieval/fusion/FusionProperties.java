package com.shibajide.policyintelligence.retrieval.fusion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag.retrieval.fusion")
public record FusionProperties(
        int rrfK
) {
    public FusionProperties {
        if (rrfK <= 0) {
            rrfK = 60;
        }
    }
}
