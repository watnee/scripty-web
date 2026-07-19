package com.scripty.api;

import java.util.List;

/**
 * The fields every bulk block operation shares.
 *
 * <p>Unlike the form-encoded MVC endpoints, which take {@code ids} as one
 * comma-separated string and silently drop anything non-numeric, the API takes
 * a real JSON array. A malformed id is a client bug worth a 400, not something
 * to quietly skip.
 *
 * <p>{@code projectId} is required rather than derived from the first block:
 * every bulk call is authorized against one project, and inferring it from the
 * payload would mean deciding access from data the caller chose.
 */
public interface BulkBlockRequest {

    List<Integer> ids();

    Integer projectId();
}
