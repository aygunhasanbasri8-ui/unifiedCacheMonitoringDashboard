package com.ucmd.service.alert;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** JSON POST eden kanalların ortak kısmı; ek HTTP kütüphanesi gerekmiyor. */
final class HttpAlertSupport {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private HttpAlertSupport() {
    }

    static void postJson(ObjectMapper mapper, String url, Map<String, ?> body)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 300) {
            // Gövdede token ya da URL olabilir; log'a yalnızca durum kodu gidiyor
            throw new IOException("HTTP " + response.statusCode());
        }
    }
}
