package com.scripty.observability;

import java.util.Locale;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Times every export and import without touching the individual services.
 *
 * <p>There are seven exporter/importer implementations and more will follow; the
 * pointcut matches them by bean name so a new {@code FooExportServiceImpl} is
 * instrumented the moment it is added, with no annotation to remember. The
 * {@code execution} clause keeps the advice on the {@code export*}/{@code import*}
 * entry points rather than every public helper on those beans.
 */
@Aspect
@Component
public class ExportMetricsAspect {

    private final ScriptyMetrics metrics;

    public ExportMetricsAspect(ScriptyMetrics metrics) {
        this.metrics = metrics;
    }

    @Around("bean(*ExportServiceImpl) && execution(* export*(..))")
    public Object timeExport(ProceedingJoinPoint joinPoint) throws Throwable {
        return record(joinPoint, true);
    }

    @Around("bean(*ImportServiceImpl) && execution(* import*(..))")
    public Object timeImport(ProceedingJoinPoint joinPoint) throws Throwable {
        return record(joinPoint, false);
    }

    private Object record(ProceedingJoinPoint joinPoint, boolean isExport) throws Throwable {
        long startedAt = System.nanoTime();
        String outcome = ScriptyMetrics.OUTCOME_FAILURE;
        try {
            Object result = joinPoint.proceed();
            outcome = outcomeOf(result);
            return result;
        } finally {
            long elapsed = System.nanoTime() - startedAt;
            String format = formatOf(joinPoint);
            if (isExport) {
                metrics.exportCompleted(format, outcome, elapsed);
            } else {
                metrics.importCompleted(format, outcome, elapsed);
            }
        }
    }

    /**
     * Imports report failure in their return value rather than by throwing — a
     * corrupt upload is a user error, not an exception — so a returned
     * {@code ImportOutcome(success=false)} must still count as a failure or the
     * failure-rate alert would never fire for the most common kind of breakage.
     */
    private static String outcomeOf(Object result) {
        if (result instanceof com.scripty.service.FountainImportService.ImportOutcome outcome
                && !outcome.success()) {
            return ScriptyMetrics.OUTCOME_FAILURE;
        }
        return ScriptyMetrics.OUTCOME_SUCCESS;
    }

    /**
     * Prefers an explicit format argument (song exports pick one of TXT/PDF/DOCX/EPUB
     * at call time) and otherwise derives it from the bean's class name, so
     * {@code PdfExportServiceImpl} tags as {@code pdf}. Both sources are bounded.
     */
    private static String formatOf(ProceedingJoinPoint joinPoint) {
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof Enum<?> value) {
                return value.name().toLowerCase(Locale.ROOT);
            }
        }
        String simpleName = joinPoint.getTarget().getClass().getSimpleName();
        int suffix = simpleName.indexOf("ExportServiceImpl");
        if (suffix < 0) {
            suffix = simpleName.indexOf("ImportServiceImpl");
        }
        return suffix > 0
                ? simpleName.substring(0, suffix).toLowerCase(Locale.ROOT)
                : ScriptyMetrics.UNKNOWN;
    }
}
