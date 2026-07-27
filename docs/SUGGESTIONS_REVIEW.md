# App suggestions: current state and review

This is a periodic review of "app suggestion" quality — ranking, filtering,
and whether suggestions shown to users are actually useful. It exists so the
findings and recommendations below survive past a single scheduled run.

## What "suggestions" actually means in this codebase today

There is no general-purpose app/action recommendation engine in Scripty.
Searching the whole repo (Java, JS, SQL migrations, docs, git history) for
suggestion/recommendation logic turns up exactly one feature:

**`ContactSuggestion`** — email/name autocomplete for the project invite
form.

- [`ContactSuggestionServiceImpl`](../src/main/java/com/scripty/service/ContactSuggestionServiceImpl.java) —
  ranks candidates (cast members + users with project access) by
  prefix-match-first then alphabetical, caps results at
  `MAX_SUGGESTIONS = 8` (line 21), dedupes by email preferring cast over
  user.
- [`ContactSuggestionRestController`](../src/main/java/com/scripty/controller/ContactSuggestionRestController.java) —
  `GET /api/project/{projectId}/contact-suggestions?q=...`.
- [`contact-autofill.js`](../src/main/resources/static/js/contact-autofill.js) —
  debounced (150ms) client, renders the dropdown, fills the field on click.

A second, unrelated "suggestion" mechanism exists for spellcheck word
corrections (`spellcheck.js`, Typo.js-based) — not an app/action suggestion
feature and out of scope for this review.

There is no persisted `Suggestion` entity and no migration for one — the
list is computed fresh from `Actor`/`User` data on every request.

## Feedback / usage data

**None exists.** There is no tracking of which suggestions were shown,
clicked, ignored, or dismissed — no DB fields, no client-side reporting, no
analytics/metrics (the `observability/` Grafana dashboards are generic
JVM/Spring Boot dashboards only). `contact-autofill.js` fills the field on
click and reports nothing back to the server.

This means step 2 of a normal suggestion-quality review — "analyze patterns
in suggestions that are not being used or marked irrelevant" — **cannot be
done from data that exists today**. There is nothing to mine.

## Recent related history

- `b2e3875` (2026-07-18) — fixed a real suggestion-quality bug: the curie
  provider namespaced the HAL response to `scripty:contactSuggestions`, but
  `contact-autofill.js` was still reading the bare `contactSuggestions` key.
  The request succeeded and the dropdown silently stayed empty — no error,
  no visible failure, just suggestions that never appeared. This is exactly
  the kind of defect a feedback loop (see below) would have surfaced in
  metrics rather than requiring someone to notice the UI felt broken.
- `55c3552` (2026-07-19) — follow-up hardening of HAL embed key naming
  across resources, referencing this fix as precedent.

No other suggestion-relevance complaints, TODOs, or issues were found in
code comments, docs, or git history.

## Recommendations

Ranked by leverage relative to effort:

1. **Add a minimal feedback signal before touching the ranking algorithm.**
   Without knowing which suggestions get chosen vs. ignored, any change to
   the prefix/alphabetical ranking in `ContactSuggestionServiceImpl` is a
   guess. Lowest-effort version: log (or emit a metric for) `query length`,
   `result count`, and `chosen index` when a suggestion is selected
   client-side, via a small `POST` or a query param on the next request.
   This alone would have caught the `b2e3875` regression as a drop in
   selection rate to zero, rather than relying on someone noticing.
2. **Do not change the ranking heuristic yet.** Prefix-match-then-alpha over
   a cast-and-access-list of ≤8 results is a reasonable default for a small,
   bounded candidate set, and there's no evidence (because there's no data)
   that it's currently wrong. Changing it without a way to measure the
   effect risks trading one guess for another.
3. **If/when this review is asked to cover a broader "app suggestion"
   engine** (e.g., suggesting scripts, actions, or templates rather than
   contacts), that would be new product surface, not a tuning pass on
   existing logic — worth confirming scope with the requester before
   building anything, since no such system exists to improve today.

## Bottom line

No new suggestion-relevance feedback or usage data exists to act on this
cycle. The one concrete, already-fixed defect (`b2e3875`) predates this
review. The actionable item is instrumentation (recommendation 1) so a
future review has real data instead of code inspection alone.
