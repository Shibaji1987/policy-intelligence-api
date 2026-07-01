package com.shibajide.policyintelligence.retrieval.rerank;

import com.shibajide.policyintelligence.retrieval.application.RetrievedChunk;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

@Primary
@Component
public class BgeRerankerClient implements Reranker {

    private static final Logger LOGGER = LoggerFactory.getLogger(BgeRerankerClient.class);

    private final RestClient restClient;
    private final HeuristicReranker fallback;
    private final boolean enabled;
    private final int maxCandidates;

    public BgeRerankerClient(
            RestClient.Builder builder,
            HeuristicReranker fallback,
            @Value("${app.reranker.base-url:http://127.0.0.1:8091}") String baseUrl,
            @Value("${app.reranker.enabled:true}") boolean enabled,
            @Value("${app.reranker.max-candidates:32}") int maxCandidates
    ) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory())
                .build();
        this.fallback = fallback;
        this.enabled = enabled;
        this.maxCandidates = Math.clamp(maxCandidates, 4, 100);
    }

    @Override
    @CircuitBreaker(name = "bgeReranker", fallbackMethod = "fallbackRerank")
    public List<RerankedChunk> rerank(String question, List<RetrievedChunk> chunks) {
        if (!enabled || chunks.isEmpty()) {
            return fallback.rerank(question, chunks);
        }
        List<RetrievedChunk> candidates = chunks.stream()
                .filter(chunk -> chunk.chunkText() != null && !chunk.chunkText().isBlank())
                .limit(maxCandidates)
                .toList();
        if (candidates.isEmpty()) {
            return fallback.rerank(question, chunks);
        }
        List<TeiRank> response = restClient.post()
                .uri("/rerank")
                .body(new TeiRerankRequest(
                        question,
                        candidates.stream().map(RetrievedChunk::chunkText).toList(),
                        false,
                        true
                ))
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });

        if (response == null || response.isEmpty()) {
            return fallback.rerank(question, chunks);
        }
        LOGGER.info("BGE reranking completed through TEI. candidates={}, returned={}",
                candidates.size(),
                response.size());
        List<RerankedChunk> reranked = IntStream.range(0, response.size())
                .mapToObj(index -> toRerankedChunk(response.get(index), candidates, index + 1))
                .filter(Objects::nonNull)
                .toList();
        return reranked.isEmpty() ? fallback.rerank(question, chunks) : reranked;
    }

    List<RerankedChunk> fallbackRerank(String question, List<RetrievedChunk> chunks, Throwable throwable) {
        LOGGER.warn("BGE reranker unavailable. Falling back to heuristic reranker. reason={}",
                throwable.getClass().getSimpleName());
        return fallback.rerank(question, chunks);
    }

    private RerankedChunk toRerankedChunk(TeiRank result, List<RetrievedChunk> candidates, int rank) {
        if (result.index() < 0 || result.index() >= candidates.size()) {
            LOGGER.warn("BGE reranker returned out-of-range index={}", result.index());
            return null;
        }
        RetrievedChunk chunk = candidates.get(result.index());
        return new RerankedChunk(
                chunk,
                chunk.rrfScore(),
                result.score(),
                rank,
                "BGE cross-encoder rerank score via Hugging Face TEI"
        );
    }

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(20));
        return factory;
    }

    private record TeiRerankRequest(String query, List<String> texts, boolean raw_scores, boolean truncate) {
    }

    private record TeiRank(int index, double score) {
    }
}
