package com.shibajide.policyintelligence.advisor.application;

@FunctionalInterface
public interface AdvisorEventSink {

    void emit(AdvisorEvent event);
}
