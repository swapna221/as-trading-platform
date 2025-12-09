package com.trading.manualorderservice.client;

import com.trading.shareddto.entity.BrokerUserDetails;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Component
public class DhanApiHttpClient {

    private final HttpClient client = HttpClient.newHttpClient();

    // ======================================================
    //                 GENERIC POST
    // ======================================================
    public String post(String url, String jsonBody, BrokerUserDetails creds) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("access-token", creds.getAccessToken())
                .header("client-id", creds.getClientId())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            log.error("❌ POST {} failed: {}", url, response.body());
        }

        return response.body();
    }

    // ======================================================
    //                 GENERIC GET
    // ======================================================
    public HttpResponse<String> get(String url, BrokerUserDetails creds) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("access-token", creds.getAccessToken())
                .header("client-id", creds.getClientId())
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 400) {
            log.error("❌ GET {} failed: {}", url, response.body());
        }

        return response;
    }
}
