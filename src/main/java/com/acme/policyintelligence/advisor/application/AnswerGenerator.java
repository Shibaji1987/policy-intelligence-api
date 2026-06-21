package com.acme.policyintelligence.advisor.application;

import com.acme.policyintelligence.context.application.BuiltContext;

public interface AnswerGenerator {

    String answer(String question, BuiltContext context);
}
