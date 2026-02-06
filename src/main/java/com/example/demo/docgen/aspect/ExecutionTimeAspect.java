package com.example.demo.docgen.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

/**
 * Aspect for logging execution time of methods annotated with @LogExecutionTime.
 */
@Aspect
@Component
@ConditionalOnProperty(name = "docgen.profiling.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class ExecutionTimeAspect {

    @Around("@annotation(logExecutionTime)")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint, LogExecutionTime logExecutionTime) throws Throwable {
        String description = logExecutionTime.value();
        if (description.isEmpty()) {
            description = joinPoint.getSignature().toShortString();
        }

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        try {
            return joinPoint.proceed();
        } finally {
            stopWatch.stop();
            log.info("PROFILING: {} took {} ms", description, stopWatch.getTotalTimeMillis());
        }
    }
}
