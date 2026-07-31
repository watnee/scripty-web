package com.scripty.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Domain metrics for the things Scripty actually does — exports, imports, email,
 * sign-ins, unhandled errors. The stock Micrometer meters cover the JVM and HTTP
 * layer; these answer "are users able to get their scripts out of the app", which
 * no JVM gauge can.
 *
 * <p>Every tag here is drawn from a bounded set (a format enum, a transport name,
 * an exception class). Nothing user-supplied is ever tagged: Prometheus keeps one
 * time series per tag combination, so an unbounded tag such as a project id or an
 * email address would grow the registry without limit.
 */
@Component
public class ScriptyMetrics {

    /** Tag value used when a format or transport cannot be determined. */
    static final String UNKNOWN = "unknown";

    public static final String OUTCOME_SUCCESS = "success";
    public static final String OUTCOME_FAILURE = "failure";

    private final MeterRegistry registry;

    /**
     * Backing values for the per-task "last succeeded at" gauges. Micrometer holds a
     * weak reference to whatever a gauge reads, so the {@link AtomicLong} has to be
     * kept alive here or the gauge starts reporting NaN once it is collected.
     */
    private final Map<String, AtomicLong> lastScheduledTaskSuccess = new ConcurrentHashMap<>();

    public ScriptyMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Records a completed project export, successful or not. */
    public void exportCompleted(String format, String outcome, long durationNanos) {
        Timer.builder("scripty.export")
                .description("Project exports by format and outcome")
                .tag("format", format)
                .tag("outcome", outcome)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /** Records a completed import, successful or not. */
    public void importCompleted(String format, String outcome, long durationNanos) {
        Timer.builder("scripty.import")
                .description("Project imports by format and outcome")
                .tag("format", format)
                .tag("outcome", outcome)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /**
     * Records an outbound email attempt. {@code transport} distinguishes the
     * Cloudflare Worker path from SMTP, because they fail independently and a
     * combined failure rate would hide one behind the other.
     */
    public void emailSent(String transport, String outcome) {
        Counter.builder("scripty.email.sent")
                .description("Outbound emails by transport and outcome")
                .tag("transport", transport)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    /** Records an authentication event: {@code success}, {@code failure}, or {@code logout}. */
    public void authEvent(String event) {
        Counter.builder("scripty.auth.events")
                .description("Authentication outcomes")
                .tag("event", event)
                .register(registry)
                .increment();
    }

    /**
     * Records a completed run of a {@code @Scheduled} task.
     *
     * <p>The nightly purge jobs have no user watching them: nothing calls them, they
     * return nothing anyone reads, and a run that stops happening looks exactly like
     * a run that found nothing to purge. The timer answers "did it run, and did it
     * work"; the last-success gauge answers "when did it last work", which is the one
     * a stale-task alert needs.
     *
     * <p>The tag is {@code task}, not {@code job}: Prometheus reserves {@code job} as
     * a target label, and a scraped metric carrying one gets renamed to
     * {@code exported_job}, which no dashboard query would think to ask for.
     *
     * <p>The gauge is only registered on the first success, so a task that has never
     * completed has no series rather than a misleading epoch-zero one. That also
     * means the series disappears on restart until the next successful run — an
     * alert on it has to tolerate the gap.
     */
    public void scheduledTaskCompleted(String task, String outcome, long durationNanos) {
        Timer.builder("scripty.scheduled.task")
                .description("Scheduled task runs by task name and outcome")
                .tag("task", task)
                .tag("outcome", outcome)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);

        if (OUTCOME_SUCCESS.equals(outcome)) {
            lastScheduledTaskSuccess.computeIfAbsent(task, name -> {
                AtomicLong epochSeconds = new AtomicLong();
                Gauge.builder("scripty.scheduled.task.last.success.timestamp", epochSeconds,
                                AtomicLong::doubleValue)
                        .description("Unix time at which each scheduled task last succeeded")
                        .baseUnit("seconds")
                        .tag("task", name)
                        .register(registry);
                return epochSeconds;
            }).set(Instant.now().getEpochSecond());
        }
    }

    /**
     * Records an exception that escaped a controller. Tagged by exception class
     * and the HTTP status the client ends up seeing, so a 500 spike is separable
     * from routine 404s.
     */
    public void unhandledException(String exceptionType, int status) {
        Counter.builder("scripty.errors.unhandled")
                .description("Exceptions escaping controllers, by type and response status")
                .tag("exception", exceptionType)
                .tag("status", String.valueOf(status))
                .register(registry)
                .increment();
    }
}
