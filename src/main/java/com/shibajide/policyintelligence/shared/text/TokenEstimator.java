package com.shibajide.policyintelligence.shared.text;

import org.springframework.stereotype.Component;

@Component
public class TokenEstimator {

    public static final String STRATEGY = "APPROX_CHARS_PER_TOKEN_4";

    public int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(text.length() / 4.0));
    }
}
