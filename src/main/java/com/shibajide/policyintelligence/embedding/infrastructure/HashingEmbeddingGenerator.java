package com.shibajide.policyintelligence.embedding.infrastructure;

import com.shibajide.policyintelligence.embedding.application.EmbeddingGenerator;
import com.shibajide.policyintelligence.embedding.application.EmbeddingVector;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "app.embeddings.provider", havingValue = "local", matchIfMissing = true)
public class HashingEmbeddingGenerator implements EmbeddingGenerator {

    public static final int DIMENSION = 1536;
    private static final String MODEL = "local-hashing-embedding-v2";
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9]+");

    @Override
    public EmbeddingVector embed(String text) {
        var vector = new float[DIMENSION];
        var normalized = normalize(text);
        if (normalized.isBlank()) {
            return new EmbeddingVector(MODEL, DIMENSION, vector);
        }

        for (String token : normalized.split("\\s+")) {
            addFeature(vector, token, 1.0f);
            for (int size = 3; size <= Math.min(5, token.length()); size++) {
                for (int index = 0; index <= token.length() - size; index++) {
                    addFeature(vector, token.substring(index, index + size), 0.35f);
                }
            }
        }

        normalizeLength(vector);
        return new EmbeddingVector(MODEL, DIMENSION, vector);
    }

    private String normalize(String text) {
        var decomposed = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKD);
        return NON_WORD.matcher(decomposed.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    private void addFeature(float[] vector, String feature, float weight) {
        int hash = murmurLikeHash(feature);
        int index = Math.floorMod(hash, vector.length);
        int sign = (hash & 1) == 0 ? 1 : -1;
        vector[index] += sign * weight;
    }

    private int murmurLikeHash(String value) {
        int hash = 0x9747b28c;
        for (byte current : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= current;
            hash *= 0x5bd1e995;
            hash ^= hash >>> 15;
        }
        return hash;
    }

    private void normalizeLength(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += value * value;
        }
        if (sum == 0) {
            return;
        }
        float length = (float) Math.sqrt(sum);
        for (int index = 0; index < vector.length; index++) {
            vector[index] = vector[index] / length;
        }
    }
}
