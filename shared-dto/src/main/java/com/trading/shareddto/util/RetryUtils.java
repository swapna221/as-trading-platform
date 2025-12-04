package com.trading.shareddto.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class RetryUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(RetryUtils.class);

    public static <T> T retry(Supplier<T> task, int maxAttempts, long delayMillis, String operationName) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return task.get();
            } catch (Exception e) {
                boolean isLast = attempt == maxAttempts;
                LOGGER.warn("Attempt {}/{} failed for [{}]: {}", attempt, maxAttempts, operationName, e.getMessage());

                if (isLast) {
                    LOGGER.error("❌ All {} attempts failed for [{}].", maxAttempts, operationName, e);
                    throw e;
                }

                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOGGER.warn("Retry interrupted for [{}]", operationName);
                    throw new RuntimeException("Retry interrupted", ie);
                }
            }
        }

        throw new IllegalStateException("Unreachable code in retry logic.");
    }
}

