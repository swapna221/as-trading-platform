package com.trading.manualorderservice.market;

import org.springframework.stereotype.Component;

/**
 * A static holder for LtpService to avoid circular dependency issues.
 *
 * Spring will inject LtpService here ONCE during startup.
 * Then any class (like OptionOrderHelper) can call:
 *      LtpServiceHolder.get().getLtpForTrading(...)
 */
@Component
public class LtpServiceHolder {

    private static LtpService INSTANCE;

    public LtpServiceHolder(LtpService ltpService) {
        INSTANCE = ltpService;
    }

    public static LtpService get() {
        return INSTANCE;
    }
}
