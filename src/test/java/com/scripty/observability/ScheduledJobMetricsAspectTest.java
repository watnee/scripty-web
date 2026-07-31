package com.scripty.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.search.MeterNotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.scheduling.annotation.Scheduled;

class ScheduledJobMetricsAspectTest {

    /**
     * Stand-in for the real purge jobs: same shape (a {@code @Scheduled} no-arg
     * method), without dragging a datasource into a unit test.
     */
    static class FakeJob {

        boolean shouldFail;
        String jobNameSeenInMdc;

        @Scheduled(cron = "0 0 3 * * *")
        public void purgeSomething() {
            jobNameSeenInMdc = MDC.get(ScheduledJobMetricsAspect.MDC_KEY);
            if (shouldFail) {
                throw new IllegalStateException("purge blew up");
            }
        }

        /** No {@code @Scheduled}: the pointcut must leave it alone. */
        public void notAJob() {
        }
    }

    private static FakeJob proxied(FakeJob target, SimpleMeterRegistry registry) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new ScheduledJobMetricsAspect(new ScriptyMetrics(registry)));
        return factory.getProxy();
    }

    @Test
    void successfulRunIsTimedAndStampsTheLastSuccessGauge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        long before = Instant.now().getEpochSecond();

        proxied(new FakeJob(), registry).purgeSomething();

        assertEquals(1L, registry.get("scripty.scheduled.task")
                .tag("task", "FakeJob.purgeSomething")
                .tag("outcome", "success")
                .timer().count());
        assertTrue(registry.get("scripty.scheduled.task.last.success.timestamp")
                .tag("task", "FakeJob.purgeSomething")
                .gauge().value() >= before);
    }

    @Test
    void failedRunIsRecordedAsAFailureAndTheExceptionStillPropagates() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FakeJob job = new FakeJob();
        job.shouldFail = true;

        assertThrows(IllegalStateException.class, () -> proxied(job, registry).purgeSomething());

        assertEquals(1L, registry.get("scripty.scheduled.task")
                .tag("task", "FakeJob.purgeSomething")
                .tag("outcome", "failure")
                .timer().count());
        // A job that has never once succeeded must not report a last-success time —
        // an epoch-zero gauge would read as "last succeeded in 1970" on the dashboard.
        assertThrows(MeterNotFoundException.class,
                () -> registry.get("scripty.scheduled.task.last.success.timestamp").gauge());
    }

    @Test
    void jobNameIsInTheMdcDuringTheRunAndClearedAfter() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FakeJob job = new FakeJob();

        proxied(job, registry).purgeSomething();

        // The MDC is what makes a job's log lines findable in Railway; the scheduler
        // reuses its thread, so leaving the key set would mislabel the next job's logs.
        assertEquals("FakeJob.purgeSomething", job.jobNameSeenInMdc);
        assertNull(MDC.get(ScheduledJobMetricsAspect.MDC_KEY));
    }

    @Test
    void unannotatedMethodsAreNotInstrumented() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        proxied(new FakeJob(), registry).notAJob();

        assertNull(registry.find("scripty.scheduled.task").timer());
    }
}
