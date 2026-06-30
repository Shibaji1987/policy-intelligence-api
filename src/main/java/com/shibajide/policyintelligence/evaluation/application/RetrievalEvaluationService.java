package com.shibajide.policyintelligence.evaluation.application;

import com.shibajide.policyintelligence.advisor.application.AdvisorAnswer;
import com.shibajide.policyintelligence.advisor.application.AdvisorService;
import com.shibajide.policyintelligence.retrieval.application.RetrievalFilters;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class RetrievalEvaluationService {

    private final AdvisorService advisorService;
    private final GoldenQuestionRepository goldenQuestionRepository;
    private final EvaluationMetricCalculator metricCalculator = new EvaluationMetricCalculator();

    public RetrievalEvaluationService(
            AdvisorService advisorService,
            GoldenQuestionRepository goldenQuestionRepository
    ) {
        this.advisorService = advisorService;
        this.goldenQuestionRepository = goldenQuestionRepository;
    }

    public List<GoldenQuestion> goldenQuestions() {
        return goldenQuestionRepository.findAll();
    }

    public EvaluationRun run(RetrievalFilters filters) {
        List<EvaluationResult> results = goldenQuestions().stream()
                .map(question -> evaluate(question, filters))
                .toList();
        double averageLatency = results.stream().mapToLong(EvaluationResult::latencyMs).average().orElse(0);
        double averageTokens = results.stream().mapToInt(EvaluationResult::tokenCount).average().orElse(0);
        return new EvaluationRun(UUID.randomUUID(), results, averageLatency, averageTokens);
    }

    private EvaluationResult evaluate(GoldenQuestion goldenQuestion, RetrievalFilters filters) {
        long started = System.currentTimeMillis();
        AdvisorAnswer answer = advisorService.answer(goldenQuestion.question(), filters);
        long latency = System.currentTimeMillis() - started;
        String answerText = answer.answer().toLowerCase(Locale.ROOT);
        long keywordMatches = goldenQuestion.expectedAnswerKeywords().stream()
                .filter(keyword -> answerText.contains(keyword.toLowerCase(Locale.ROOT)))
                .count();
        double answerGroundedness = goldenQuestion.expectedAnswerKeywords().isEmpty()
                ? 0
                : (double) keywordMatches / goldenQuestion.expectedAnswerKeywords().size();
        double citationAccuracy = answer.sources().isEmpty() ? 0 : 1;
        if (citationAccuracy > 0 && (!goldenQuestion.expectedDocumentIds().isEmpty() || !goldenQuestion.expectedSourceHints().isEmpty())) {
            citationAccuracy = answer.sources().stream()
                    .anyMatch(source -> metricCalculator.matches(
                            source,
                            goldenQuestion.expectedDocumentIds(),
                            goldenQuestion.expectedSourceHints()
                    )) ? 1 : 0;
        }
        return new EvaluationResult(
                goldenQuestion.id(),
                answer.traceId(),
                metricCalculator.recallAt(answer.sources(), goldenQuestion.expectedDocumentIds(), goldenQuestion.expectedSourceHints(), 5),
                metricCalculator.recallAt(answer.sources(), goldenQuestion.expectedDocumentIds(), goldenQuestion.expectedSourceHints(), 10),
                metricCalculator.mrr(answer.sources(), goldenQuestion.expectedDocumentIds(), goldenQuestion.expectedSourceHints()),
                metricCalculator.precisionAt(answer.sources(), goldenQuestion.expectedDocumentIds(), goldenQuestion.expectedSourceHints(), 5),
                citationAccuracy,
                answerGroundedness,
                Math.min(citationAccuracy, answerGroundedness),
                latency,
                answer.contextMetrics().estimatedTokens()
        );
    }
}
