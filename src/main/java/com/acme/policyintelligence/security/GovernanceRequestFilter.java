package com.acme.policyintelligence.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class GovernanceRequestFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            GovernanceContextHolder.set(new GovernanceContext(
                    headerOrDefault(request, "X-User-Id", "anonymous"),
                    headerOrDefault(request, "X-Tenant-Id", "default"),
                    headerSet(request, "X-Allowed-Departments"),
                    headerSet(request, "X-Allowed-Regions"),
                    headerSet(request, "X-Allowed-Classifications")
            ));
            filterChain.doFilter(request, response);
        } finally {
            GovernanceContextHolder.clear();
        }
    }

    private String headerOrDefault(HttpServletRequest request, String name, String fallback) {
        String value = request.getHeader(name);
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private Set<String> headerSet(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(item -> !item.isBlank())
                .collect(Collectors.toUnmodifiableSet());
    }
}
