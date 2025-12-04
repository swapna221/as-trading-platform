package com.trading.manualorderservice.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RetryUtils {

    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1500;

    @FunctionalInterface
    public interface Retryable<T> {
        T call() throws Exception;
    }

    /**
     * Generic retry executor.
     *
     * Usage:
     * RetryUtils.execute(() -> api.call(), "LTP API");
     */
    public static <T> T execute(Retryable<T> task, String taskName) throws Exception {
        int attempt = 1;

        while (attempt <= MAX_RETRIES) {
            try {
                return task.call();
            } catch (Exception e) {

                log.warn("Attempt {}/{} failed for [{}]: {}",
                        attempt, MAX_RETRIES, taskName, e.getMessage());

                if (attempt == MAX_RETRIES) {
                    throw e;
                }

                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException ignored) {}

                attempt++;
            }
        }

        throw new RuntimeException("Retries exhausted for task: " + taskName);
    }
}

