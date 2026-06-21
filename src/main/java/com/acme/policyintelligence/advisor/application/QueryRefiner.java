package com.acme.policyintelligence.advisor.application;

import org.springframework.stereotype.Component;

@Component
public class QueryRefiner {

    public String refine(String question) {
        return question == null ? "" : question.strip();
    }
}
