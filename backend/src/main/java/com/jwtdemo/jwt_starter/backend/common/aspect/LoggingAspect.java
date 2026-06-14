package com.jwtdemo.jwt_starter.backend.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;


@Aspect
@Component
@Slf4j
public class LoggingAspect {

    // intercetta tutto ciò che è in controller o service
    @Around("within(com.jwtdemo.jwt_starter.backend..*..*Controller) || within(com.jwtdemo.jwt_starter.backend..*..*Service)")
    public Object logMethodCall(ProceedingJoinPoint joinPoint) throws Throwable {
        String className  = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args     = joinPoint.getArgs();

        log.debug("→ {}.{}() | INPUT: {}", className, methodName, Arrays.toString(args));

        long start  = System.currentTimeMillis();
        Object result = joinPoint.proceed(); // esegue il metodo originale
        long elapsed = System.currentTimeMillis() - start;

        log.debug("← {}.{}() | OUTPUT: {} | {}ms", className, methodName, result, elapsed);

        return result;
    }
}