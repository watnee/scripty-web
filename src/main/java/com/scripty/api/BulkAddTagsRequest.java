package com.scripty.api;

import java.util.List;

/**
 * Adds {@code tags} (comma-separated) to every named block.
 *
 * <p>Purely additive, matching the service: a tag already present on a block —
 * compared case-insensitively — is not added again, and the existing casing
 * wins. There is no bulk remove; clearing tags is an ordinary block edit.
 */
public record BulkAddTagsRequest(List<Integer> ids, Integer projectId, String tags)
        implements BulkBlockRequest {
}
