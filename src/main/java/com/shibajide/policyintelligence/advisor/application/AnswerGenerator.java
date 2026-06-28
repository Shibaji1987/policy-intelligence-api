package com.shibajide.policyintelligence.advisor.application;

import com.shibajide.policyintelligence.context.application.BuiltContext;

public interface AnswerGenerator {

    String answer(String question, BuiltContext context);

    default String name() {
        return getClass().getSimpleName();
    }
}
