package com.trading.manualorderservice.market;

import com.trading.manualorderservice.util.SegmentMapper;
import com.trading.shareddto.entity.BrokerUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.json.JSONArray;
import org.springframework.stereotype.Service;

import java.net.http.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchLtpService {

    private static final String LTP_URL = "https://api.dhan.co/v2/marketfeed/ltp";

    private final LtpCacheService ltpCacheService;

    // NEW: pending symbols to batch-fetch
    private final Map<String, Set<String>> pending = new ConcurrentHashMap<>();

    /**
     * Allow anyone to request LTP for a security.
     * This will be fetched on the next batch cycle.
     */
    public void request(String segment, String secId) {
        pending.computeIfAbsent(segment, k -> ConcurrentHashMap.newKeySet())
                .add(secId);
    }

    /**
     * Called every 20 seconds from LtpBatchRefreshEngine,
     * OR manually if someone wants forced refresh.
     */
    public void fetchBatchLtp(Map<String, List<String>> segmentsMap,
                              BrokerUserDetails creds) {

        // 1️⃣ Merge pending requests into segmentsMap
        mergePendingRequests(segmentsMap);

        if (segmentsMap.isEmpty()) return;

        try {
            JSONObject body = new JSONObject();

            // Convert internal → Dhan API keys
            segmentsMap.forEach((segment, ids) -> {
                String apiSeg = SegmentMapper.toDhanApi(segment);

                JSONArray arr = new JSONArray();
                ids.forEach(id -> arr.put(Integer.parseInt(id)));

                body.put(apiSeg, arr);
            });

            HttpClient http = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(LTP_URL))
                    .header("access-token", creds.getAccessToken())
                    .header("client-id", creds.getClientId())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response =
                    http.send(request, HttpResponse.BodyHandlers.ofString());

            JSONObject json = new JSONObject(response.body());
            JSONObject data = json.getJSONObject("data");

            // Parse response
            for (String internalSeg : segmentsMap.keySet()) {
                String apiSeg = SegmentMapper.toDhanApi(internalSeg);

                if (!data.has(apiSeg)) continue;

                JSONObject segData = data.getJSONObject(apiSeg);

                for (String secId : segmentsMap.get(internalSeg)) {

                    if (segData.has(secId)) {
                        double ltp = segData.getJSONObject(secId)
                                .getDouble("last_price");

                        // store in cache
                        ltpCacheService.update(internalSeg, secId, ltp);
                    }
                }
            }

            log.info("Batch LTP updated for {} segments", segmentsMap.size());

        } catch (Exception e) {
            log.error("❌ Batch LTP failed: {}", e.getMessage());
        }
    }

    /**
     * Merge pending queued requests into the next batch cycle.
     */
    private void mergePendingRequests(Map<String, List<String>> segmentsMap) {

        pending.forEach((seg, set) -> {
            if (!segmentsMap.containsKey(seg)) {
                segmentsMap.put(seg, new ArrayList<>(set));
            } else {
                List<String> existing = segmentsMap.get(seg);
                for (String s : set) {
                    if (!existing.contains(s)) {
                        existing.add(s);
                    }
                }
            }
        });

        // clear queue after merging
        pending.clear();
    }
}
