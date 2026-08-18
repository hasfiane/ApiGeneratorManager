package com.api.generator.schema;

/**
 * Semantic role of a column, set via per-table hints in {@code application.yml}.
 *
 * <p>A column with a role is treated as a <b>plain SQL column</b> — no special clause
 * is generated. The role only tells the runtime which value to inject automatically
 * before building the query.</p>
 *
 * <ul>
 *   <li>{@link #NONE}             — ordinary column, no auto-injection</li>
 *   <li>{@link #CREATED_AT}       — injected with {@code NOW()} on INSERT</li>
 *   <li>{@link #UPDATED_AT}       — injected with {@code NOW()} on INSERT and UPDATE</li>
 *   <li>{@link #SOFT_DELETE}      — set to {@code NOW()} on DELETE (soft), filtered IS NULL on SELECT</li>
 *   <li>{@link #CREATED_BY}       — injected with the authenticated username on INSERT</li>
 *   <li>{@link #LAST_MODIFIED_BY} — injected with the authenticated username on INSERT and UPDATE</li>
 * </ul>
 */
public enum ColumnRole {
    NONE,
    CREATED_AT,
    UPDATED_AT,
    SOFT_DELETE,
    CREATED_BY,
    LAST_MODIFIED_BY
}

