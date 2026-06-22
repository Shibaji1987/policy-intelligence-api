package com.acme.policyintelligence.context.packing;

import com.acme.policyintelligence.context.compression.CompressedChunk;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

@Service
public class ContextPackingService {

    public PackedContext pack(List<CompressedChunk> chunks) {
        var edgeWeighted = new LinkedList<CompressedChunk>();
        var middle = new ArrayList<CompressedChunk>();
        for (int index = 0; index < chunks.size(); index++) {
            CompressedChunk chunk = chunks.get(index);
            if (index == 0 || index == 2) {
                edgeWeighted.addFirst(chunk);
            } else if (index == 1 || index == 3) {
                edgeWeighted.addLast(chunk);
            } else {
                middle.add(chunk);
            }
        }
        int insertionPoint = Math.max(1, edgeWeighted.size() / 2);
        edgeWeighted.addAll(insertionPoint, middle);
        return new PackedContext(List.copyOf(edgeWeighted), LostInMiddleMitigationStrategy.EDGE_WEIGHTED);
    }
}
