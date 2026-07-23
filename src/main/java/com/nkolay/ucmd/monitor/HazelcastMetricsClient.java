package com.nkolay.ucmd.monitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Hazelcast'in jmx_exporter endpoint'inden (Prometheus text format)
 * metrikleri okur ve basit bir map'e çevirir.
 */
@Component
public class HazelcastMetricsClient {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastMetricsClient.class);

    @Value("${hazelcast.metrics-url:http://localhost:9404/metrics}")
    private String metricsUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /**
     * Metrikleri çeker. Anahtar: metrik adı (etiketsiz veya etiketli tam hali).
     * Dönen map hem "jvm_memory_bytes_used" gibi sade adları,
     * hem "jvm_memory_bytes_used{area=heap}" gibi etiketli halleri içerir.
     */
    public Map<String, Double> fetchMetrics() {
        Map<String, Double> metrics = new HashMap<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(metricsUrl))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warn("Hazelcast metrics endpoint returned {}", response.statusCode());
                return metrics;
            }

            for (String line : response.body().split("\n")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;   // yorum satırları

                // Format: metrik_adi{etiket="deger",...} 123.45
                int lastSpace = line.lastIndexOf(' ');
                if (lastSpace < 0) continue;

                String nameWithLabels = line.substring(0, lastSpace).trim();
                String valueStr = line.substring(lastSpace + 1).trim();

                double value;
                try {
                    value = Double.parseDouble(valueStr);
                } catch (NumberFormatException e) {
                    continue;   // NaN, +Inf gibi değerleri atla
                }

                metrics.put(nameWithLabels, value);
            }

            logger.debug("Fetched {} Hazelcast metrics from {}", metrics.size(), metricsUrl);

        } catch (Exception e) {
            logger.warn("Could not fetch Hazelcast metrics from {}: {}", metricsUrl, e.getMessage());
        }
        return metrics;
    }

    /**
     * Belirli bir metriği bulur. Tam eşleşme yoksa, adı içeren ilk satırı döner.
     * Örn: findMetric(m, "jvm_memory_bytes_used", "area=\"heap\"")
     */
    public double findMetric(Map<String, Double> metrics, String metricName, String labelFilter) {
        for (Map.Entry<String, Double> entry : metrics.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(metricName)) continue;
            if (labelFilter == null || labelFilter.isEmpty() || key.contains(labelFilter)) {
                return entry.getValue();
            }
        }
        return 0.0;
    }
}