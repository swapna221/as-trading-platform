package com.trading.manualorderservice.limits;

import com.trading.shareddto.entity.BrokerUserDetails;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONObject;

@Slf4j
public class ThrottledHttpClient {

    private static final OkHttpClient client = new OkHttpClient();

    public static JSONObject postJson(
            String url,
            String jsonBody,
            BrokerUserDetails creds,
            Long userId,
            boolean isLtpCall
    ) throws Exception {

        // 🔥 Global LTP throttling — REQUIRED
        if (isLtpCall) {
            GlobalRateLimitRegistry.awaitTurn(userId);
        }

        RequestBody body = RequestBody.create(
                jsonBody,
                MediaType.parse("application/json")
        );

        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .addHeader("access-token", creds.getAccessToken())
                .addHeader("client-id", creds.getClientId())
                .build();

        Response response = client.newCall(request).execute();
        String raw = response.body().string();

        if (!response.isSuccessful()) {
            log.warn("HTTP {} from {} → {}", response.code(), url, raw);
            throw new RuntimeException("HTTP " + response.code() + ": " + raw);
        }

        return new JSONObject(raw);
    }
}
