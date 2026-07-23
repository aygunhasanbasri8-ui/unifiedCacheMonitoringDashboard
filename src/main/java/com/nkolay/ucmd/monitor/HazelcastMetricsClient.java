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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hazelcast cluster üyelerinin jmx_exporter endpoint'lerinden metrik okur.
 * Birden çok node destekler — değerler toplanır (bellek) veya ortalanır (CPU).
 */
@Component
public class HazelcastMetricsClient {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastMetricsClient.class);

    @Value("${hazelcast.metrics-urls:http://localhost:9404/metrics}")
    private String metricsUrls;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /** Tek bir node'dan metrikleri çeker. */
    private Map<String, Double> fetchFromUrl(String url) {
        Map<String, Double> metrics = new HashMap<>();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warn("Hazelcast metrics endpoint {} returned {}", url, response.statusCode());
                return metrics;
            }

            for (String line : response.body().split("\n")) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;

                int lastSpace = line.lastIndexOf(' ');
                if (lastSpace < 0) continue;

                String nameWithLabels = line.substring(0, lastSpace).trim();
                String valueStr = line.substring(lastSpace + 1).trim();

                try {
                    metrics.put(nameWithLabels, Double.parseDouble(valueStr));
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            logger.warn("Could not fetch Hazelcast metrics from {}: {}", url, e.getMessage());
        }
        return metrics;
    }

    /** Tüm node'lardan metrikleri çeker (her node ayrı map). */
    public List<Map<String, Double>> fetchAllNodes() {
        List<Map<String, Double>> result = new ArrayList<>();
        for (String url : metricsUrls.split(",")) {
            url = url.trim();
            if (url.isEmpty()) continue;
            Map<String, Double> m = fetchFromUrl(url);
            if (!m.isEmpty()) result.add(m);
        }
        logger.debug("Fetched metrics from {} Hazelcast node(s)", result.size());
        return result;
    }

    /** Geriye dönük uyumluluk: ilk node'un metrikleri. */
    public Map<String, Double> fetchMetrics() {
        List<Map<String, Double>> all = fetchAllNodes();
        return all.isEmpty() ? new HashMap<>() : all.get(0);
    }

    /** Belirli bir metriği bulur (etiket filtresiyle). */
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

    /** Tüm node'lardan bir metriği toplar (bellek gibi toplanabilir değerler için). */
    public double sumAcrossNodes(List<Map<String, Double>> nodes, String metricName, String labelFilter) {
        double total = 0;
        for (Map<String, Double> node : nodes) {
            total += findMetric(node, metricName, labelFilter);
        }
        return total;
    }

    /** Tüm node'lardan bir metriğin ortalamasını alır (CPU gibi oransal değerler için). */
    public double avgAcrossNodes(List<Map<String, Double>> nodes, String metricName, String labelFilter) {
        if (nodes.isEmpty()) return 0;
        return sumAcrossNodes(nodes, metricName, labelFilter) / nodes.size();
    }
}