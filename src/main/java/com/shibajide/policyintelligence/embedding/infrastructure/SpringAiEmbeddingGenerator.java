package com.shibajide.policyintelligence.embedding.infrastructure;

import com.shibajide.policyintelligence.embedding.application.EmbeddingGenerator;
import com.shibajide.policyintelligence.embedding.application.EmbeddingVector;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(EmbeddingModel.class)
public class SpringAiEmbeddingGenerator implements EmbeddingGenerator {

    private final EmbeddingModel embeddingModel;
    private final String modelName;
    private final int dimension;

    public SpringAiEmbeddingGenerator(
            EmbeddingModel embeddingModel,
            @Value("${app.embeddings.spring-ai-model:spring-ai-embedding-model}") String modelName,
            @Value("${app.embeddings.dimension:1536}") int dimension
    ) {
        this.embeddingModel = embeddingModel;
        this.modelName = modelName;
        this.dimension = dimension;
    }

    @Override
    public EmbeddingVector embed(String text) {
        float[] values = embeddingModel.embed(text);
        return new EmbeddingVector(modelName, values.length == 0 ? dimension : values.length, values);
    }
}
