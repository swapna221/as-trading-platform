package com.trading.manualorderservice.service;

import com.trading.manualorderservice.marketfeed.IndexLtpCache;
import com.trading.shareddto.entity.BrokerUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.json.JSONObject;
import org.json.JSONArray;

import java.net.http.*;
import java.net.URI;

@Slf4j
@Service
@RequiredArgsConstructor
public class DhanIndexLtpService {

    private final IndexLtpCache cache;
    private final DhanCredentialService credentialProvider;

    private static final String LTP_URL = "https://api.dhan.co/v2/marketfeed/ltp";

    public double getIndexLtp(String index) throws Exception {
        String key = index.toUpperCase();

        // 1️⃣ First try WebSocket cache (instant)
        if (cache.get(key).isPresent()) {
            return cache.get(key).get();
        }

        // 2️⃣ REST fallback
        log.warn("⚠ Index LTP not available from WS. Fetching via REST… {}", key);

        BrokerUserDetails creds = credentialProvider.getSystemUser();

        String secId = switch (key) {
            case "NIFTY" -> "7";
            case "BANKNIFTY" -> "25";
            default -> throw new IllegalArgumentException("Unknown index: " + index);
        };

        JSONObject body = new JSONObject()
                .put("IDX_I", new JSONArray().put(Integer.parseInt(secId)));

        HttpClient http = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(LTP_URL))
                .header("access-token", creds.getAccessToken())
                .header("client-id", creds.getClientId())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> res =
                http.send(request, HttpResponse.BodyHandlers.ofString());

        JSONObject json = new JSONObject(res.body());

        double ltp = json.getJSONObject("data")
                .getJSONObject("IDX_I")
                .getJSONObject(secId)
                .getDouble("last_price");

        // update cache
        cache.update(key, ltp);

        return ltp;
    }
}

