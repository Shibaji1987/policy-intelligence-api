package com.shibajide.policyintelligence.advisor.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedQueryRouterTest {

    private final RuleBasedQueryRouter router = new RuleBasedQueryRouter();

    @Test
    void routesGreetingAwayFromRetrieval() {
        QueryRoute route = router.route("Hi");

        assertThat(route.retrievalRequired()).isFalse();
        assertThat(route.intent()).isEqualTo(IntentType.CHITCHAT);
        assertThat(route.response()).contains("retrieved policy sources");
    }

    @Test
    void routesTypoGreetingAwayFromRetrieval() {
        QueryRoute route = router.route("hHello");

        assertThat(route.retrievalRequired()).isFalse();
        assertThat(route.intent()).isEqualTo(IntentType.CHITCHAT);
    }

    @Test
    void routesConversationalQuestionAwayFromRetrieval() {
        QueryRoute route = router.route("hey how you are doing?");

        assertThat(route.retrievalRequired()).isFalse();
        assertThat(route.intent()).isEqualTo(IntentType.CHITCHAT);
    }

    @Test
    void routesBlankQuestionAwayFromRetrieval() {
        QueryRoute route = router.route("   ");

        assertThat(route.retrievalRequired()).isFalse();
        assertThat(route.intent()).isEqualTo(IntentType.EMPTY);
    }

    @Test
    void routesVeryShortNonQuestionAwayFromRetrieval() {
        QueryRoute route = router.route("yo");

        assertThat(route.retrievalRequired()).isFalse();
        assertThat(route.intent()).isEqualTo(IntentType.TOO_SHORT);
    }

    @Test
    void routesWeatherQuestionOutOfScope() {
        QueryRoute route = router.route("How is the weather today?");

        assertThat(route.retrievalRequired()).isFalse();
        assertThat(route.intent()).isEqualTo(IntentType.OUT_OF_SCOPE);
    }

    @Test
    void routesGeneralKnowledgeQuestionOutOfScope() {
        QueryRoute route = router.route("What is the capital of France?");

        assertThat(route.retrievalRequired()).isFalse();
        assertThat(route.intent()).isEqualTo(IntentType.OUT_OF_SCOPE);
    }

    @Test
    void routesAmbiguousFollowUpAsClarification() {
        QueryRoute route = router.route("What about that?");

        assertThat(route.retrievalRequired()).isFalse();
        assertThat(route.intent()).isEqualTo(IntentType.CLARIFICATION);
    }

    @Test
    void routesPolicyQuestionIntoRetrieval() {
        QueryRoute route = router.route("Can contractors access production customer data?");

        assertThat(route.retrievalRequired()).isTrue();
        assertThat(route.intent()).isEqualTo(IntentType.POLICY_KNOWLEDGE);
    }
}
