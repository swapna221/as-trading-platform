package com.trading.manualorderservice.limits;

import java.util.concurrent.ThreadLocalRandom;

public class RetryEngine {

    public static <T> T callWithRetry(ApiCall<T> call, String tag) throws Exception {

        int maxAttempts = 5;
        int baseDelay = 300;  // 300ms
        int attempt = 1;

        while (true) {
            try {
                return call.run();   // execute API
            } catch (Exception e) {

                if (attempt == maxAttempts) {
                    throw new RuntimeException("❌ Max retries exceeded for " + tag + ": " + e.getMessage(), e);
                }

                int jitter = ThreadLocalRandom.current().nextInt(50, 200);
                int sleep = baseDelay * attempt + jitter;

                System.err.println("⚠ Retry " + attempt + "/" + maxAttempts +
                        " for " + tag + " after " + sleep + " ms | error: " + e.getMessage());

                Thread.sleep(sleep);
                attempt++;
            }
        }
    }

    @FunctionalInterface
    public interface ApiCall<T> {
        T run() throws Exception;
    }
}
