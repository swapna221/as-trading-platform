package com.trading.manualorderservice.market;
import com.trading.manualorderservice.service.DhanAllApis;
import com.trading.shareddto.entity.BrokerUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LtpService {

    private final LtpCacheService ltpCache;
    private final BatchLtpService batchLtpService;
    private final DhanAllApis dhanAllApis;

    /**
     * Universal LTP fetcher used by OrderBuildService
     */
    public double getLtpForTrading(String secId,
                                   String segment,
                                   BrokerUserDetails creds,
                                   Long userId) throws Exception {

        // 1️⃣ Try fresh cache first
        Double ltp = ltpCache.getFresh(segment,secId);
        if (ltp != null) {
            return ltp;
        }

        // 2️⃣ Ask batch engine to fetch it on next cycle
        batchLtpService.request(segment, secId);

        // 3️⃣ Wait 300ms for batch update
        Thread.sleep(300);
        ltp = ltpCache.getFresh(segment,secId);
        if (ltp != null) {
            return ltp;
        }

        // 4️⃣ LAST RESORT fallback — direct API call
        log.warn("⚠ LTP cache empty for {} ({}). Using fallback API.", secId, segment);

        return switch (segment) {
            case "NSE_FNO" -> dhanAllApis.fetchLtpOption(secId, creds, userId);
            case "NSE_EQ"  -> dhanAllApis.fetchLtpEquity(secId, creds, userId);
            case "NSE_IDX" -> throw new RuntimeException("Index LTP should not be fetched here");
            default -> throw new IllegalArgumentException("Unknown segment: " + segment);
        };
    }
}
