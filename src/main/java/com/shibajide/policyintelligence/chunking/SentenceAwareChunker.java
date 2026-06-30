package com.shibajide.policyintelligence.chunking;

import com.shibajide.policyintelligence.document.domain.ChunkingStrategy;
import org.springframework.stereotype.Component;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class SentenceAwareChunker implements Chunker {

    @Override
    public ChunkingStrategy strategy() {
        return ChunkingStrategy.SENTENCE_AWARE;
    }

    @Override
    public List<String> chunk(String text, int chunkSize, int overlap) {
        ChunkingArguments.validate(text, chunkSize, Math.max(overlap, 0));
        var sentences = sentences(text);
        var chunks = new ArrayList<String>();
        var current = new StringBuilder();

        for (String sentence : sentences) {
            if (!current.isEmpty() && current.length() + 1 + sentence.length() > chunkSize) {
                chunks.add(current.toString());
                current = overlapTail(current, overlap);
            }
            append(current, sentence);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }
        return List.copyOf(chunks);
    }

    private List<String> sentences(String text) {
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.US);
        iterator.setText(text);
        var values = new ArrayList<String>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String sentence = text.substring(start, end).strip();
            if (!sentence.isBlank()) {
                values.add(sentence);
            }
        }
        return values;
    }

    private StringBuilder overlapTail(StringBuilder source, int overlap) {
        if (overlap <= 0 || source.length() <= overlap) {
            return new StringBuilder();
        }
        int start = Math.max(0, source.length() - overlap);
        return new StringBuilder(source.substring(start).strip());
    }

    private void append(StringBuilder builder, String sentence) {
        if (!builder.isEmpty()) {
            builder.append(' ');
        }
        builder.append(sentence);
    }
}
