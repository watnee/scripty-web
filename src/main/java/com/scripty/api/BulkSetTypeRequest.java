package com.scripty.api;

import java.util.List;

/** Retypes every named block to {@code type} (Scene, Action, Character, …). */
public record BulkSetTypeRequest(List<Integer> ids, Integer projectId, String type)
        implements BulkBlockRequest {
}
