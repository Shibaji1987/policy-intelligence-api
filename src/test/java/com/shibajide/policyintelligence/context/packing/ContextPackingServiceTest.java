package com.shibajide.policyintelligence.context.packing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ContextPackingServiceTest {

    private final ContextPackingService service = new ContextPackingService();

    @Test
    void emptyChunksReturnEmptyPackedContext() {
        PackedContext context = service.pack(List.of());

        assertThat(context.orderedChunks()).isEmpty();
        assertThat(context.strategy()).isEqualTo(LostInMiddleMitigationStrategy.EDGE_WEIGHTED);
    }
}
