package com.trading.manualorderservice.dhan;

import com.trading.manualorderservice.dto.DhanOrderBookResponse;
import com.trading.shareddto.entity.BrokerUserDetails;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class DhanOrderClient {

    private final OkHttpClient client = new OkHttpClient();
    private static final String BASE_URL = "https://api.dhan.co";
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1500;

    @Value
    @Builder
    public static class PlaceOrderResult {
        boolean ok;
        String orderId;
        String status;
        String raw;
    }

    @Value
    @Builder
    public static class OrderStatusResult {
        boolean ok;
        String status;
        double tradedPrice;
        String raw;
    }

    public PlaceOrderResult placeOrder(BrokerUserDetails creds,
                                       String exchangeSegment,
                                       String transactionType,
                                       String productType,
                                       String orderType,
                                       String securityId,
                                       int quantity,
                                       double price,
                                       double triggerPrice) {

        JSONObject body = new JSONObject();
        body.put("transactionType", transactionType);
        body.put("exchangeSegment", exchangeSegment);
        body.put("productType", productType);
        body.put("orderType", orderType);
        body.put("securityId", securityId);
        body.put("quantity", quantity);
        body.put("price", price);
        body.put("triggerPrice", triggerPrice);
        body.put("afterMarketOrder", false);
        body.put("validity", "DAY");

        int attempts = 0;
        while (attempts < MAX_RETRIES) {
            attempts++;
            try {
                Request request = new Request.Builder()
                        .url(BASE_URL + "/orders")
                        .post(RequestBody.create(body.toString(), MediaType.parse("application/json")))
                        .addHeader("access-token", creds.getAccessToken())
                        .addHeader("X-Api-Key", creds.getClientId())
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String respBody = response.body() != null ? response.body().string() : "";
                    log.info("Dhan placeOrder attempt {}: code={} body={}", attempts, response.code(), respBody);

                    if (response.isSuccessful()) {
                        JSONObject json = new JSONObject(respBody);
                        return PlaceOrderResult.builder()
                                .ok(true)
                                .orderId(json.optString("orderId", null))
                                .status(json.optString("orderStatus", "PENDING"))
                                .raw(respBody)
                                .build();
                    }
                    // retry on 5xx, fail fast on 4xx
                    if (response.code() >= 400 && response.code() < 500) {
                        return PlaceOrderResult.builder()
                                .ok(false)
                                .status("FAILED")
                                .raw(respBody)
                                .build();
                    }
                }
            } catch (Exception e) {
                log.error("Error calling Dhan placeOrder (attempt {}): {}", attempts, e.getMessage());
            }

            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException ignored) {}
        }

        return PlaceOrderResult.builder()
                .ok(false)
                .status("FAILED")
                .raw("Max retries reached")
                .build();
    }

    public OrderStatusResult getOrderStatus(BrokerUserDetails creds, String orderId) {
        try {
            Request request = new Request.Builder()
                    .url(BASE_URL + "/orders/" + orderId)
                    .get()
                    .addHeader("access-token", creds.getAccessToken())
                    .addHeader("X-Api-Key", creds.getClientId())
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String body = response.body() != null ? response.body().string() : "";
                log.info("Dhan getOrderStatus {} -> code={} body={}", orderId, response.code(), body);

                if (!response.isSuccessful()) {
                    return OrderStatusResult.builder()
                            .ok(false)
                            .status("UNKNOWN")
                            .tradedPrice(0.0)
                            .raw(body)
                            .build();
                }
                JSONObject json = new JSONObject(body);
                String status = json.optString("orderStatus", "UNKNOWN");
                double tradedPrice = json.optDouble("price", 0.0);

                return OrderStatusResult.builder()
                        .ok(true)
                        .status(status)
                        .tradedPrice(tradedPrice)
                        .raw(body)
                        .build();
            }
        } catch (Exception e) {
            log.error("Error calling Dhan getOrderStatus: {}", e.getMessage());
            return OrderStatusResult.builder()
                    .ok(false)
                    .status("UNKNOWN")
                    .tradedPrice(0.0)
                    .raw(e.getMessage())
                    .build();
        }
    }

    public PlaceOrderResult cancelOrder(BrokerUserDetails creds, String orderId) {
        int attempts = 0;

        while (attempts < MAX_RETRIES) {
            attempts++;
            try {
                Request request = new Request.Builder()
                        .url(BASE_URL + "/orders/" + orderId)
                        .delete()
                        .addHeader("access-token", creds.getAccessToken())
                        .addHeader("X-Api-Key", creds.getClientId())
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String respBody = response.body() != null ? response.body().string() : "";
                    log.info("Dhan cancelOrder attempt {}: code={} body={}",
                            attempts, response.code(), respBody);

                    if (response.isSuccessful()) {
                        JSONObject json = new JSONObject(respBody);

                        return PlaceOrderResult.builder()
                                .ok(true)
                                .orderId(orderId)
                                .status(json.optString("orderStatus", "CANCELLED"))
                                .raw(respBody)
                                .build();
                    }

                    // Fast fail for client-side errors
                    if (response.code() >= 400 && response.code() < 500) {
                        return PlaceOrderResult.builder()
                                .ok(false)
                                .orderId(orderId)
                                .status("FAILED")
                                .raw(respBody)
                                .build();
                    }
                }

            } catch (Exception e) {
                log.error("Error calling Dhan cancelOrder (attempt {}): {}", attempts, e.getMessage());
            }

            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException ignored) {}
        }

        return PlaceOrderResult.builder()
                .ok(false)
                .orderId(orderId)
                .status("FAILED")
                .raw("Max retries reached while cancelling order")
                .build();
    }




    public List<DhanOrderBookResponse> fetchOrderBook(BrokerUserDetails creds) {

        String url = BASE_URL + "/v2/orders";
        int attempts = 0;

        while (attempts < MAX_RETRIES) {
            attempts++;
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .get()
                        .addHeader("access-token", creds.getAccessToken())
                        .addHeader("X-Api-Key", creds.getClientId())
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response response = client.newCall(request).execute()) {

                    String body = response.body() != null ? response.body().string() : "";
                    log.info("Dhan fetchOrderBook attempt {}: code={} body={}",
                            attempts, response.code(), body);

                    if (!response.isSuccessful()) {

                        // 4xx → return immediately
                        if (response.code() >= 400 && response.code() < 500) {
                            log.error("❌ fetchOrderBook client error {}: {}", response.code(), body);
                            return Collections.emptyList();
                        }

                        // 5xx → retry
                        continue;
                    }

                    // Parse JSON Array → List<DhanOrderBookResponse>
                    JSONArray arr = new JSONArray(body);
                    List<DhanOrderBookResponse> list = new ArrayList<>();

                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject json = arr.getJSONObject(i);
                        DhanOrderBookResponse dto = new DhanOrderBookResponse();

                        dto.setOrderId(json.optString("orderId"));
                        dto.setDhanClientId(json.optString("dhanClientId"));
                        dto.setCorrelationId(json.optString("correlationId"));
                        dto.setOrderStatus(json.optString("orderStatus"));
                        dto.setTransactionType(json.optString("transactionType"));
                        dto.setExchangeSegment(json.optString("exchangeSegment"));
                        dto.setProductType(json.optString("productType"));
                        dto.setOrderType(json.optString("orderType"));
                        dto.setValidity(json.optString("validity"));
                        dto.setTradingSymbol(json.optString("tradingSymbol"));
                        dto.setSecurityId(json.optString("securityId"));
                        dto.setQuantity(json.optInt("quantity"));
                        dto.setDisclosedQuantity(json.optInt("disclosedQuantity"));
                        dto.setPrice(json.optDouble("price"));
                        dto.setTriggerPrice(json.optDouble("triggerPrice"));
                        dto.setAfterMarketOrder(json.optBoolean("afterMarketOrder"));
                        dto.setBoProfitValue(json.optDouble("boProfitValue"));
                        dto.setBoStopLossValue(json.optDouble("boStopLossValue"));
                        dto.setLegName(json.optString("legName"));
                        dto.setCreateTime(json.optString("createTime"));
                        dto.setUpdateTime(json.optString("updateTime"));
                        dto.setExchangeTime(json.optString("exchangeTime"));
                        dto.setDrvExpiryDate(json.optString("drvExpiryDate"));
                        dto.setDrvOptionType(json.optString("drvOptionType"));
                        dto.setDrvStrikePrice(json.optDouble("drvStrikePrice"));
                        dto.setOmsErrorCode(json.optString("omsErrorCode"));
                        dto.setOmsErrorDescription(json.optString("omsErrorDescription"));
                        dto.setRemainingQuantity(json.optInt("remainingQuantity"));
                        dto.setFilledQty(json.optInt("filledQty"));
                        dto.setAverageTradedPrice(json.optDouble("averageTradedPrice"));

                        list.add(dto);
                    }

                    return list;
                }

            } catch (Exception e) {
                log.error("Error calling fetchOrderBook (attempt {}): {}", attempts, e.getMessage());
            }

            try {
                Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException ignored) {}
        }

        return Collections.emptyList();
    }






}
