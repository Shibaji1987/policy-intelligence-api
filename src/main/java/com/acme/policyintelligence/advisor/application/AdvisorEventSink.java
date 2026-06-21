package com.acme.policyintelligence.advisor.application;

@FunctionalInterface
public interface AdvisorEventSink {

    void emit(AdvisorEvent event);
}
