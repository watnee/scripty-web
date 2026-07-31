package com.scripty.observability;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Times every {@code @Scheduled} method and records whether it succeeded.
 *
 * <p>Background jobs are the least observable code in the app. A request that
 * breaks produces a 5xx, an angry user, and a line in
 * {@code scripty_errors_unhandled_total}; a nightly purge that breaks produces
 * nothing at all, and a job that has silently not run for a month is
 * indistinguishable from one that ran and found nothing to do. Every meter here
 * exists to tell those two apart.
 *
 * <p>Matched by the annotation rather than by bean name, following
 * {@link ExportMetricsAspect}'s convention-over-configuration approach: a new
 * scheduled job is instrumented the moment it is written, with nothing to
 * remember. Spring's {@code ScheduledAnnotationBeanPostProcessor} runs at
 * {@code LOWEST_PRECEDENCE}, after the auto-proxy creator, so the scheduler
 * invokes the proxy and this advice does apply to scheduler-driven calls.
 *
 * <p>The task name also goes into the MDC, so the ECS log lines a run emits carry
 * {@code task} and one Railway search
 * (`@task:BlockTrashPurgeJob.purgeExpiredBlocks`) returns that job's history.
 * Requests get {@code request_id} from {@link com.scripty.config.RequestIdFilter}
 * for the same reason; scheduled work had no equivalent.
 */
@Aspect
@Component
public class ScheduledJobMetricsAspect {

    /**
     * MDC key carrying the task name on log lines emitted during a run. Matches the
     * metric tag rather than saying "job", so the same name works in a log search
     * and a PromQL selector — and {@code job} is a reserved Prometheus label anyway.
     */
    public static final String MDC_KEY = "task";

    private final ScriptyMetrics metrics;

    public ScheduledJobMetricsAspect(ScriptyMetrics metrics) {
        this.metrics = metrics;
    }

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object timeScheduledJob(ProceedingJoinPoint joinPoint) throws Throwable {
        String task = taskNameOf(joinPoint);
        long startedAt = System.nanoTime();
        String outcome = ScriptyMetrics.OUTCOME_FAILURE;
        MDC.put(MDC_KEY, task);
        try {
            Object result = joinPoint.proceed();
            outcome = ScriptyMetrics.OUTCOME_SUCCESS;
            return result;
        } finally {
            metrics.scheduledTaskCompleted(task, outcome, System.nanoTime() - startedAt);
            MDC.remove(MDC_KEY);
        }
    }

    /**
     * {@code ClassName.methodName} — bounded by the number of scheduled methods in
     * the source, so it is safe as a Prometheus tag, and readable enough to use as
     * a Railway log filter without a lookup table.
     */
    private static String taskNameOf(ProceedingJoinPoint joinPoint) {
        return joinPoint.getSignature().getDeclaringType().getSimpleName()
                + "." + joinPoint.getSignature().getName();
    }
}
