package com.trading.manualorderservice.marketfeed;

import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DhanIndexWebSocket {

    private final IndexLtpCache cache;

    private static final String FEED_URL = "wss://api-feed.dhan.co";

    @Autowired
    public DhanIndexWebSocket(IndexLtpCache cache) {
        this.cache = cache;
        connect();
    }

    private void connect() {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder().url(FEED_URL).build();

        client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response resp) {
                log.info("WebSocket connected.");

                // Subscribe to index LTP
                ws.send("""
                {
                  "channel": "subscribe",
                  "tokenList": [
                    {"exchangeSegment": "IDX_I", "securityId": "NIFTY_50"},
                    {"exchangeSegment": "IDX_I", "securityId": "NIFTY_BANK"}
                  ]
                }
                """);
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                try {
                    JSONObject json = new JSONObject(text);

                    if (!json.has("last_price")) return;

                    String secId = json.getString("securityId");
                    double ltp = json.getDouble("last_price");

                    if ("NIFTY_50".equals(secId))
                        cache.update("NIFTY", ltp);
                    else if ("NIFTY_BANK".equals(secId))
                        cache.update("BANKNIFTY", ltp);

                } catch (Exception ignored) {}
            }
        });
    }
}

