package com.scripty.observability;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

/**
 * Pins the scraped names of the scheduled-task meters.
 *
 * <p>Every alert in {@code observability/prometheus/alerts.yml} and every panel on
 * the Scripty domain dashboard queries these by name. Micrometer derives the
 * scraped name from the meter name, the meter type, and the base unit, so a
 * seemingly cosmetic change here — dropping the {@code seconds} base unit,
 * renaming the tag — silently empties a dashboard and stops an alert from ever
 * firing. Nothing else in the build would notice, so assert it against the real
 * Prometheus registry rather than trusting the derivation.
 */
class ScriptyMetricsExpositionTest {

    private static String scrape() {
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        ScriptyMetrics metrics = new ScriptyMetrics(registry);
        metrics.scheduledTaskCompleted("BlockTrashPurgeJob.purgeExpiredBlocks",
                ScriptyMetrics.OUTCOME_SUCCESS, 1_500_000L);
        return registry.scrape();
    }

    @Test
    void scheduledTaskTimerIsScrapedUnderTheNameTheAlertsQuery() {
        String scraped = scrape();

        assertTrue(scraped.contains("scripty_scheduled_task_seconds_count"),
                () -> "ScriptyScheduledTaskFailing queries this name:\n" + scraped);
        assertTrue(scraped.contains("task=\"BlockTrashPurgeJob.purgeExpiredBlocks\""),
                () -> "expected a task tag:\n" + scraped);
    }

    @Test
    void lastSuccessGaugeIsScrapedUnderTheNameTheStaleAlertQueries() {
        assertTrue(scrape().contains("scripty_scheduled_task_last_success_timestamp_seconds"),
                "ScriptyScheduledTaskStale subtracts this series from time()");
    }

    @Test
    void noMeterCarriesAJobTagThatPrometheusWouldRename() {
        // job is a reserved target label: a scraped metric carrying one gets renamed to
        // exported_job on ingest, so `{job="scripty"}` in a rule would match the target
        // rather than the task and the tag the query wanted would be gone.
        assertFalse(scrape().contains("job=\""),
                "no scripty_* meter may tag anything `job` — Prometheus owns that label");
    }
}
