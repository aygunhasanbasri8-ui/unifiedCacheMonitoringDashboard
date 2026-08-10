package com.nkolay.ucmd.monitor;

import com.nkolay.ucmd.model.entity.InstanceRef;
import com.nkolay.ucmd.monitor.prometheus.NodeMetrics;
import com.nkolay.ucmd.monitor.prometheus.PrometheusTextParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Hazelcast cluster üyelerinin jmx_exporter endpoint'lerinden metrik okur.
 * Her node ayrı bir {@link NodeMetrics} olarak döner; cluster geneli değerler
 * {@link #sum}, {@link #avg}, {@link #max} ile toplanır.
 */
@Component
public class HazelcastMetricsClient {

    private static final Logger logger = LoggerFactory.getLogger(HazelcastMetricsClient.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /** Instance'ın metricsUrl'indeki tüm node'lardan metrik çeker. */
    public List<NodeMetrics> fetchAllNodes(InstanceRef instance) {
        List<NodeMetrics> result = new ArrayList<>();

        String metricsUrl = (instance != null) ? instance.getMetricsUrl() : null;
        if (metricsUrl == null || metricsUrl.isBlank()) {
            logger.debug("Instance için metrics URL tanımlı değil: {}",
                    instance != null ? instance.getName() : "null");
            return result;
        }

        for (String url : metricsUrl.split(",")) {
            url = url.trim();
            if (url.isEmpty()) continue;

            NodeMetrics node = fetchFromUrl(url);
            if (node != null && !node.isEmpty()) result.add(node);
        }

        logger.debug("Fetched metrics from {} Hazelcast node(s)", result.size());
        return result;
    }

    /** Tek bir node'dan metrikleri çeker. Başarısızlıkta null. */
    private NodeMetrics fetchFromUrl(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET().build();

            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warn("Hazelcast metrics endpoint {} returned {}", url, response.statusCode());
                return null;
            }

            return PrometheusTextParser.parse(response.body());

        } catch (Exception e) {
            logger.warn("Could not fetch Hazelcast metrics from {}: {}", url, e.getMessage());
            return null;
        }
    }

    // ═══════════════ Cluster geneli toplayıcılar ═══════════════

    /** Tüm node'lardan toplar. Hiçbir node'da yoksa null. */
    public static Double sum(List<NodeMetrics> nodes, Function<NodeMetrics, Double> extractor) {
        double total = 0;
        boolean found = false;
        for (NodeMetrics node : nodes) {
            Double value = extractor.apply(node);
            if (value == null) continue;
            total += value;
            found = true;
        }
        return found ? total : null;
    }

    /** Node ortalaması (oransal değerler için). Hiçbir node'da yoksa null. */
    public static Double avg(List<NodeMetrics> nodes, Function<NodeMetrics, Double> extractor) {
        double total = 0;
        int count = 0;
        for (NodeMetrics node : nodes) {
            Double value = extractor.apply(node);
            if (value == null) continue;
            total += value;
            count++;
        }
        return count > 0 ? total / count : null;
    }

    /** En büyük değer (peak metrikleri için). Hiçbir node'da yoksa null. */
    public static Double max(List<NodeMetrics> nodes, Function<NodeMetrics, Double> extractor) {
        Double best = null;
        for (NodeMetrics node : nodes) {
            Double value = extractor.apply(node);
            if (value == null) continue;
            if (best == null || value > best) best = value;
        }
        return best;
    }
}
