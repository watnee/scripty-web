package com.scripty.api;

import java.util.List;

/** Deletes every named block. Deleted blocks are recoverable from the trash. */
public record BulkDeleteRequest(List<Integer> ids, Integer projectId)
        implements BulkBlockRequest {
}
