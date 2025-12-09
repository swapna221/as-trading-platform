package com.trading.manualorderservice.service;

import com.trading.manualorderservice.client.DhanApiHttpClient;
import com.trading.manualorderservice.config.DhanAuthConfig;
import com.trading.manualorderservice.limits.ThrottledHttpClient;
import com.trading.shareddto.entity.BrokerUserDetails;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;
import com.trading.manualorderservice.marketfeed.IndexLtpCache;

import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;


@Component
@RequiredArgsConstructor
@Slf4j
public class DhanAllApis {

    private final IndexLtpCache indexCache;
    private final DhanApiHttpClient httpClient;   // ✅ ADD THIS
    private final DhanAuthConfig authConfig;      // ✅ ADD THIS

    private String baseUrl;                       // ✅ ADD THIS

    @PostConstruct
    public void init() {
        this.baseUrl = authConfig.getBaseUrl();   // load from YAML
    }

    private static final String LTP_URL = "https://api.dhan.co/v2/marketfeed/ltp";

    // ============================================================
    //                 GENERIC LTP LOADER (Unified)
    // ============================================================
    private double loadLtp(String segment,
                           String identifier,       // securityId OR indexName
                           BrokerUserDetails creds,
                           Long userId,
                           boolean isIndex) throws Exception {

        JSONObject body = new JSONObject();
        JSONArray arr = new JSONArray();

        // INDEX uses names, EQ/FNO use integer IDs
        if (isIndex) {
            arr.put(identifier);            // "NIFTY_50"
        } else {
            arr.put(Integer.valueOf(identifier));   // 3045, 145490, etc
        }

        body.put(segment, arr);

        JSONObject json = ThrottledHttpClient.postJson(
                LTP_URL,
                body.toString(),
                creds,
                userId,
                true     // apply LTP throttling
        );

        if (!json.has("data"))
            throw new RuntimeException("Missing data section: " + json);

        JSONObject data = json.getJSONObject("data");

        if (!data.has(segment))
            throw new RuntimeException("Segment missing: " + segment + " => " + json);

        JSONObject segmentMap = data.getJSONObject(segment);

        if (!segmentMap.has(identifier))
            throw new RuntimeException("Missing identifier " + identifier + " inside " + segment + " => " + json);

        return segmentMap.getJSONObject(identifier).getDouble("last_price");
    }

    // ============================================================
    //                       EQUITY LTP (NSE_EQ)
    // ============================================================
    public double fetchLtpEquity(String secId, BrokerUserDetails creds, Long userId) throws Exception {
        return loadLtp("NSE_EQ", secId, creds, userId, false);
    }

    // ============================================================
    //                       OPTION LTP (NSE_FNO)
    // ============================================================
    public double fetchLtpOption(String secId, BrokerUserDetails creds, Long userId) throws Exception {
        return loadLtp("NSE_FNO", secId, creds, userId, false);
    }

    // ============================================================
    //                        INDEX LTP (IDX_I)
    // ============================================================
    public double fetchLtpIndex(String indexName,
                                BrokerUserDetails creds,
                                Long userId) throws Exception {

        String indexSecId;

        switch (indexName.toUpperCase()) {

            case "NIFTY":
            case "NIFTY50":
            case "NIFTY_50":
                indexSecId = "13";   // CONFIRMED
                break;

            case "BANKNIFTY":
            case "NIFTY_BANK":
            case "BANK_NIFTY":
            case "BANK NIFTY":
                indexSecId = "25";   // CONFIRMED
                break;

            case "NIFTY100":
            case "NIFTY_100":
                indexSecId = "17";   // CONFIRMED
                break;

            default:
                throw new IllegalArgumentException("Unknown index: " + indexName);
        }

        // Build request JSON
        JSONObject body = new JSONObject();
        JSONArray arr = new JSONArray();
        arr.put(Integer.parseInt(indexSecId));  // Must be integer, not string

        body.put("IDX_I", arr);

        // Send via throttled client
        JSONObject json = ThrottledHttpClient.postJson(
                LTP_URL,
                body.toString(),
                creds,
                userId,
                true
        );

        // Parse
        JSONObject data = json.getJSONObject("data");

        if (!data.has("IDX_I"))
            throw new RuntimeException("IDX_I segment missing: " + json);

        JSONObject idx = data.getJSONObject("IDX_I");

        if (!idx.has(indexSecId))
            throw new RuntimeException("Index " + indexName + " (" + indexSecId + ") missing: " + json);

        return idx.getJSONObject(indexSecId).getDouble("last_price");
    }







    // ============================================================
    //     BACKWARD COMPATIBILITY (OLD METHOD USED BY EQUITY)
    // ============================================================
    public double fetchLTPStock(int securityId, BrokerUserDetails creds) {
        try {
            Long sysUser = 9999L; // internal system user for throttling
            return fetchLtpEquity(String.valueOf(securityId), creds, sysUser);
        } catch (Exception e) {
            throw new RuntimeException("Legacy equity LTP fetch failed: " + e.getMessage(), e);
        }
    }

    public Map<String, Double> fetchAllIndexLtp(BrokerUserDetails creds, Long userId) throws Exception {

        JSONObject body = new JSONObject();
        JSONArray arr = new JSONArray();

        arr.put(13); // NIFTY50
        arr.put(25); // BANKNIFTY
        arr.put(17); // NIFTY100

        body.put("IDX_I", arr);

        JSONObject json = ThrottledHttpClient.postJson(
                LTP_URL,
                body.toString(),
                creds,
                userId,
                true
        );

        JSONObject data = json.getJSONObject("data").getJSONObject("IDX_I");

        Map<String, Double> out = new HashMap<>();
        out.put("NIFTY50", data.getJSONObject("13").getDouble("last_price"));
        out.put("BANKNIFTY", data.getJSONObject("25").getDouble("last_price"));
        out.put("NIFTY100", data.getJSONObject("17").getDouble("last_price"));

        return out;
    }

    public double fetchIv(String securityId, BrokerUserDetails creds) throws Exception {

        String url = baseUrl + "/v2/marketfeed/option/details/" + securityId;

        HttpResponse<String> resp = httpClient.get(url, creds);

        JSONObject json = new JSONObject(resp.body());
        JSONObject data = json.getJSONObject("data");

        if (!data.has("iv")) {
            throw new RuntimeException("IV not available for: " + securityId);
        }

        return data.getDouble("iv");
    }




}
