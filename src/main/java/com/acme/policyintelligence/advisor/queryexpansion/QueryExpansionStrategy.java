package com.acme.policyintelligence.advisor.queryexpansion;

import java.util.List;

public interface QueryExpansionStrategy {

    List<GeneratedQuery> expand(String baseQuery);
}
