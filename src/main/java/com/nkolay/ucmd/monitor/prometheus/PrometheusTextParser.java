package com.nkolay.ucmd.monitor.prometheus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prometheus text exposition format ayrıştırıcısı.
 *
 * Desteklenen kalıplar:
 * <pre>
 *   process_open_fds 77.0
 *   jvm_memory_bytes_used{area="heap",} 1.30423192E8
 *   com_hazelcast_metrics_ownedentrycount{instance="x",prefix="map",tag0="\"name=ucmd-merchants\"",} 24.0
 * </pre>
 *
 * {@code tag0} nesne adını escape'li tırnak içinde taşır; soyulup {@code __object}
 * anahtarıyla etiketlere konur. {@code tag1}/{@code tag2} varsa
 * {@code __object1}/{@code __object2} olur.
 */
public final class PrometheusTextParser {

    private PrometheusTextParser() {}

    /** Prometheus text format gövdesini ayrıştırır. */
    public static NodeMetrics parse(String body) {
        List<ParsedMetric> metrics = new ArrayList<>();
        if (body == null || body.isEmpty()) {
            return new NodeMetrics(metrics);
        }

        // BOM temizliği
        if (body.charAt(0) == '﻿') {
            body = body.substring(1);
        }

        for (String rawLine : body.split("\n")) {
            ParsedMetric metric = parseLine(rawLine);
            if (metric != null) metrics.add(metric);
        }
        return new NodeMetrics(metrics);
    }

    /** Tek satır ayrıştırır; ayrıştırılamayan satır için null döner (sessizce atlanır). */
    private static ParsedMetric parseLine(String rawLine) {
        try {
            String line = rawLine.trim();
            if (line.isEmpty() || line.charAt(0) == '#') return null;

            int brace = line.indexOf('{');
            int space = line.indexOf(' ');

            String name;
            Map<String, String> labels;
            String valuePart;

            if (brace >= 0 && (space < 0 || brace < space)) {
                // Etiketli satır
                int close = findClosingBrace(line, brace);
                if (close < 0) return null;

                name = line.substring(0, brace).trim();
                labels = parseLabels(line.substring(brace + 1, close));
                valuePart = line.substring(close + 1).trim();
            } else {
                // Etiketsiz satır
                if (space < 0) return null;
                name = line.substring(0, space).trim();
                labels = Map.of();
                valuePart = line.substring(space + 1).trim();
            }

            if (name.isEmpty() || valuePart.isEmpty()) return null;

            // Prometheus satırının sonunda opsiyonel timestamp olabilir
            int valueEnd = valuePart.indexOf(' ');
            if (valueEnd > 0) valuePart = valuePart.substring(0, valueEnd);

            double value = Double.parseDouble(valuePart);
            // NaN / +Inf / -Inf taşıyan satırlar atlanır
            if (Double.isNaN(value) || Double.isInfinite(value)) return null;

            return new ParsedMetric(name, labels, value);

        } catch (Exception e) {
            return null;
        }
    }

    /** Tırnak ve kaçış karakterlerini dikkate alarak kapanış süslü parantezini bulur. */
    private static int findClosingBrace(String line, int openIndex) {
        boolean inQuotes = false;
        for (int i = openIndex + 1; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\') { i++; continue; }          // kaçış: sonraki karakteri atla
            if (c == '"') { inQuotes = !inQuotes; continue; }
            if (c == '}' && !inQuotes) return i;
        }
        return -1;
    }

    /**
     * Etiket bloğunu ayrıştırır. Virgülle bölme tırnak dışında yapılır —
     * etiket değeri virgül içerebilir.
     */
    private static Map<String, String> parseLabels(String block) {
        Map<String, String> labels = new LinkedHashMap<>();

        for (String part : splitOutsideQuotes(block)) {
            String element = part.trim();
            if (element.isEmpty()) continue;           // sondaki ",}" boş eleman üretir

            int eq = element.indexOf('=');
            if (eq <= 0) continue;

            String key = element.substring(0, eq).trim();
            String value = unquote(element.substring(eq + 1).trim());

            labels.put(key, value);

            // tag0/tag1/tag2 nesne adını escape'li tırnak içinde taşır
            if (key.startsWith("tag") && key.length() == 4
                    && Character.isDigit(key.charAt(3))) {
                String objectName = extractObjectName(value);
                if (objectName != null) {
                    int index = key.charAt(3) - '0';
                    labels.put(index == 0 ? NodeMetrics.OBJECT_LABEL
                                          : NodeMetrics.OBJECT_LABEL + index, objectName);
                }
            }
        }
        return labels;
    }

    /** Tırnak dışındaki virgüllerden böler. */
    private static List<String> splitOutsideQuotes(String block) {
        List<String> parts = new ArrayList<>();
        boolean inQuotes = false;
        int start = 0;

        for (int i = 0; i < block.length(); i++) {
            char c = block.charAt(i);
            if (c == '\\') { i++; continue; }
            if (c == '"') { inQuotes = !inQuotes; continue; }
            if (c == ',' && !inQuotes) {
                parts.add(block.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(block.substring(start));
        return parts;
    }

    /** Dış tırnakları soyar ve Prometheus kaçışlarını çözer. */
    private static String unquote(String value) {
        String result = value;
        if (result.length() >= 2 && result.charAt(0) == '"'
                && result.charAt(result.length() - 1) == '"') {
            result = result.substring(1, result.length() - 1);
        }
        return unescape(result);
    }

    /** {@code \" → "}, {@code \\ → \}, {@code \n → newline} */
    private static String unescape(String value) {
        if (value.indexOf('\\') < 0) return value;

        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'n'  -> sb.append('\n');
                    default   -> sb.append('\\').append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * {@code "name=ucmd-merchants"} biçimindeki tag değerinden nesne adını çıkarır.
     * İçteki tırnaklar soyulur, ilk '=' işaretinden bölünür.
     */
    private static String extractObjectName(String tagValue) {
        if (tagValue == null || tagValue.isEmpty()) return null;

        String inner = tagValue;
        if (inner.length() >= 2 && inner.charAt(0) == '"'
                && inner.charAt(inner.length() - 1) == '"') {
            inner = inner.substring(1, inner.length() - 1);
        }

        int eq = inner.indexOf('=');
        if (eq < 0 || eq == inner.length() - 1) return null;
        return inner.substring(eq + 1);
    }
}
