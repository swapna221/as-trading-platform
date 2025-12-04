package com.trading.manualorderservice.marketfeed;

import com.trading.manualorderservice.service.DhanAllApis;
import com.trading.manualorderservice.service.DhanCredentialService;
import com.trading.shareddto.entity.BrokerUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexPollingService {

    private final DhanAllApis dhanAllApis;
    private final DhanCredentialService dhanCredentialService;

    // Cache for all index LTPs
    private volatile Double nifty50 = null;
    private volatile Double bankNifty = null;
    private volatile Double nifty100 = null;

    /**
     * Poll all indexes every 1.5 seconds using ONE API request.
     */
    @Scheduled(fixedRate = 15000)
    public void pollAllIndexLTP() {

        try {
            BrokerUserDetails creds = dhanCredentialService.getSystemUser();
            if (creds == null) {
                log.error("❌ No system credentials — cannot poll index LTP");
                return;
            }

            var all = dhanAllApis.fetchAllIndexLtp(creds, creds.getUserId());

            nifty50 = all.get("NIFTY50");
            bankNifty = all.get("BANKNIFTY");
            nifty100 = all.get("NIFTY100");

            log.info("📊 INDEX LTP → NIFTY50={} | BANKNIFTY={} | NIFTY100={}",
                    nifty50, bankNifty, nifty100);

        } catch (Exception e) {
            log.error("❌ Index polling failure: {}", e.getMessage());
        }
    }

    public Double getNiftySpot()     { return nifty50; }
    public Double getBankNiftySpot() { return bankNifty; }
    public Double getNifty100Spot()  { return nifty100; }
}
