package com.trading.manualorderservice.marketfeed;

import com.trading.manualorderservice.service.DhanCredentialService;
import com.trading.shareddto.entity.BrokerUserDetails;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.ByteString;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class DhanIndexWebSocket {

    private final IndexLtpCache cache;
    private final DhanCredentialService credentialService;

    private WebSocket webSocket;
    private final OkHttpClient client;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private static final String FEED_URL = "wss://api-feed.dhan.co";

    // Dhan index security IDs (per doc)
    private static final int NIFTY50_ID   = 13;
    private static final int BANKNIFTY_ID = 25;

    public DhanIndexWebSocket(IndexLtpCache cache,
                              DhanCredentialService credentialService) {
        this.cache = cache;
        this.credentialService = credentialService;
        this.client = new OkHttpClient.Builder()
                .pingInterval(10, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // keep streaming
                .build();

        connect();
    }

    private void connect() {
        if (!running.get()) return;

        try {
            BrokerUserDetails creds = credentialService.getSystemUser();

            // Build URL with query params as per docs
            String url = FEED_URL +
                    "?version=2" +
                    "&token=" + URLEncoder.encode(creds.getAccessToken(), StandardCharsets.UTF_8) +
                    "&clientId=" + URLEncoder.encode(creds.getClientId(), StandardCharsets.UTF_8) +
                    "&authType=2";

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            this.webSocket = client.newWebSocket(request, new WebSocketListener() {

                @Override
                public void onOpen(WebSocket ws, Response resp) {
                    log.info("✅ Dhan Index WebSocket connected. HTTP {}", resp.code());

                    // Subscribe to index instruments via JSON (RequestCode + InstrumentList)
                    // Using RequestCode = 15 as in docs (subscribe to chosen mode).
                    JSONObject sub = new JSONObject()
                            .put("RequestCode", 15)
                            .put("InstrumentCount", 2)
                            .put("InstrumentList", new org.json.JSONArray()
                                    .put(new JSONObject()
                                            .put("ExchangeSegment", "IDX_I")
                                            .put("SecurityId", String.valueOf(NIFTY50_ID)))
                                    .put(new JSONObject()
                                            .put("ExchangeSegment", "IDX_I")
                                            .put("SecurityId", String.valueOf(BANKNIFTY_ID)))
                            );

                    ws.send(sub.toString());
                    log.info("📡 Subscribed to NIFTY50 ({}) & BANKNIFTY ({}) via WebSocket",
                            NIFTY50_ID, BANKNIFTY_ID);
                }

                @Override
                public void onMessage(WebSocket ws, ByteString bytes) {
                    try {
                        parseBinaryPacket(bytes);
                    } catch (Exception e) {
                        log.error("❌ Failed to parse WS packet: {}", e.getMessage(), e);
                    }
                }

                @Override
                public void onMessage(WebSocket ws, String text) {
                    // Dhan docs say responses are binary; this is just for safety/logging
                    log.debug("⚠ Unexpected text frame from Dhan feed: {}", text);
                }

                @Override
                public void onFailure(WebSocket ws, Throwable t, Response resp) {
                    if (!running.get()) {
                        log.info("WebSocket failure after shutdown: {}", t.getMessage());
                        return;
                    }
                    String code = (resp != null) ? String.valueOf(resp.code()) : "no HTTP code";
                    log.error("❌ WebSocket failure: {} (HTTP {})", t.getMessage(), code);
                    scheduleReconnect();
                }

                @Override
                public void onClosing(WebSocket ws, int code, String reason) {
                    log.warn("⚠ WebSocket closing: code={} reason={}", code, reason);
                    ws.close(code, reason);
                    if (running.get()) {
                        scheduleReconnect();
                    }
                }
            });

        } catch (Exception e) {
            log.error("❌ Error creating Dhan Index WebSocket: {}", e.getMessage(), e);
            scheduleReconnect();
        }
    }

    /**
     * Parse Dhan binary packet:
     * Header (8 bytes, little-endian):
     *  - [0]   : feed response code
     *  - [1-2] : int16 message length
     *  - [3]   : exchange segment (byte)
     *  - [4-7] : int32 securityId
     *
     * For Ticker/Quote/Full packets:
     *  - Next 4 bytes after header = float32 LTP
     */
    private void parseBinaryPacket(ByteString bytes) {
        ByteBuffer buf = bytes.asByteBuffer().order(ByteOrder.LITTLE_ENDIAN);

        if (buf.remaining() < 12) {
            // 8 header + 4 LTP = 12 minimum
            return;
        }

        byte feedCode = buf.get();      // 0: feed response code
        short msgLen  = buf.getShort(); // 1-2: total length (not used)
        byte exchSeg  = buf.get();      // 3: exchange segment
        int securityId = buf.getInt();  // 4-7: security ID

        // Only care about price packets (Ticker / Quote / Full)
        if (feedCode != 2 && feedCode != 4 && feedCode != 8) {
            return;
        }

        float ltp = buf.getFloat(); // 8-11: LTP

        if (securityId == NIFTY50_ID) {
            cache.update("NIFTY50", ltp);
            log.debug("WS NIFTY50 LTP={}", ltp);
        } else if (securityId == BANKNIFTY_ID) {
            cache.update("BANKNIFTY", ltp);
            log.debug("WS BANKNIFTY LTP={}", ltp);
        }
    }

    private void scheduleReconnect() {
        if (!running.get()) return;

        log.info("🔄 Reconnecting Index WebSocket in 3 seconds...");
        new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {}
            if (running.get()) {
                connect();
            }
        }, "dhan-ws-reconnect").start();
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (webSocket != null) {
            log.info("🛑 Closing Dhan Index WebSocket");
            webSocket.close(1000, "Shutdown");
        }
    }
}
