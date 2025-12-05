package com.trading.manualorderservice.marketfeed;

import com.trading.manualorderservice.service.DhanAllApis;
import com.trading.manualorderservice.service.DhanCredentialService;
import com.trading.shareddto.entity.BrokerUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexPollingService {

    private final DhanAllApis dhanAllApis;
    private final DhanCredentialService credentialService;
    private final IndexLtpCache indexCache;

    // REST fallback values
    private volatile Double restNifty50 = null;
    private volatile Double restBankNifty = null;
    private volatile Double restNifty100 = null;

    // Block REST calls for 30 seconds after 429
    private volatile long nextAllowedCallTime = 0;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /**
     * Poll every 20 sec as BACKUP only.
     * WS is the primary source.
     */
    @Scheduled(fixedRate = 20_000)
    public void pollIndexBackup() {

        // ================================
        // 1️⃣ Block polling outside market hours
        // ================================
        LocalTime now = LocalTime.now(IST);
        if (now.isBefore(LocalTime.of(9, 15)) || now.isAfter(LocalTime.of(15, 25))) {
            return; // Do NOT hit Dhan API at night
        }

        // ================================
        // 2️⃣ 429 cooldown handling
        // ================================
        long currentTime = System.currentTimeMillis();
        if (currentTime < nextAllowedCallTime) {
            return;
        }

        try {
            BrokerUserDetails creds = credentialService.getSystemUser();
            if (creds == null) {
                log.error("❌ No system credentials for REST index polling");
                return;
            }

            var all = dhanAllApis.fetchAllIndexLtp(creds, creds.getUserId());

            restNifty50   = all.get("NIFTY50");
            restBankNifty = all.get("BANKNIFTY");
            restNifty100  = all.get("NIFTY100");

            log.info("📊 BACKUP REST INDEX LTP → NIFTY50={} | BANKNIFTY={} | NIFTY100={}",
                    restNifty50, restBankNifty, restNifty100);

        } catch (Exception e) {

            String msg = e.getMessage();
            if (msg != null && msg.contains("429")) {
                log.warn("⚠ REST index polling rate-limited. Cooling down for 30 sec.");
                nextAllowedCallTime = System.currentTimeMillis() + 30_000; // 30 sec cooldown
            }

            log.error("❌ REST fallback polling failed: {}", e.getMessage());
        }
    }


    // ------------------------------------------------------------------------
    // PUBLIC ACCESSORS — prefer WS → fallback REST
    // ------------------------------------------------------------------------

    public Double getNiftySpot() {
        return indexCache.get("NIFTY50").orElse(restNifty50);
    }

    public Double getBankNiftySpot() {
        return indexCache.get("BANKNIFTY").orElse(restBankNifty);
    }

    public Double getNifty100Spot() {
        return indexCache.get("NIFTY100").orElse(restNifty100);
    }
}
