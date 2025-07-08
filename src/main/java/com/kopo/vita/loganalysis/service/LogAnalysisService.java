package com.kopo.vita.loganalysis.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kopo.vita.loganalysis.dto.LokiLogDTO;
import com.kopo.vita.loganalysis.dto.MergedMetricsDTO;
import com.kopo.vita.loganalysis.dto.MetricSampleDTO;
import com.kopo.vita.loganalysis.dto.PrometheusMetricDTO;
import com.kopo.vita.loganalysis.query.LogAnalysisQueries;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kopo.vita.loganalysis.util.LogFormatter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LogAnalysisService {

    @Value("${loki.base-url}")
    private String lokiBaseUrl;

    @Value("${prometheus.base-url}")
    private String prometheusBaseUrl;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;

    private JsonNode lastAnalysis;

    private static final String LOKI_QUERY_PATH = "/loki/api/v1/query_range";
    private static final String PROMETHEUS_QUERY_PATH = "/api/v1/query_range";
    private static final Map<String, String> QUERIES = Map.of(
            "cpu",     LogAnalysisQueries.CPU_USAGE,
            "memory",  LogAnalysisQueries.MEMORY_USAGE,
            "disk",    LogAnalysisQueries.DISK_USAGE,
            "traffic", LogAnalysisQueries.HTTP_TRAFFIC
    );

    public LogAnalysisService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 1) Loki 로그 조회
     */
    public List<Map<String,String>> fetchAndFormatLogs() throws Exception {
        List<LokiLogDTO> raw = fetchLogs();
        String rawJson = objectMapper.writeValueAsString(raw);
        return LogFormatter.formatRawLokiLogs(rawJson);
    }

    private List<LokiLogDTO> fetchLogs() throws IOException, InterruptedException {
        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofMinutes(10));
        long startNs = start.toEpochMilli() * 1_000_000;
        long endNs = end.toEpochMilli() * 1_000_000;

        String query = URLEncoder.encode("{app=\"testapp\"}", StandardCharsets.UTF_8);

        String endpoint = String.format("%s%s", lokiBaseUrl, LOKI_QUERY_PATH);

        String url = String.format(
                "%s?query=%s&start=%d&end=%d&limit=1000&direction=backward",
                endpoint, query, startNs, endNs
        );

        System.out.println("Loki URL = " + url);

        HttpResponse<String> resp = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        if (resp.statusCode() != 200) {
            throw new IOException("Loki HTTP status: " + resp.statusCode() + " / " + resp.body());
        }

        JsonNode root = objectMapper.readTree(resp.body())
                .path("data").path("result");
        List<LokiLogDTO> logs = new ArrayList<>();
        for (JsonNode serie : root) {
            String stream = serie.path("stream").toString();
            for (JsonNode entry : serie.path("values")) {
                LokiLogDTO dto = new LokiLogDTO();
                dto.setTimestamp(Instant.ofEpochMilli(entry.get(0).asLong() / 1_000_000));
                dto.setLine(entry.get(1).asText());
                dto.setStream(stream);
                logs.add(dto);
            }
        }
        return logs;
    }


    /**
     * 2) Prometheus 메트릭 조회 (10분 구간, 30초 스텝)
     */
    public Map<String, PrometheusMetricDTO> fetchAllMetrics() throws IOException, InterruptedException {
        Instant end = Instant.now();
        Instant start = end.minus(Duration.ofMinutes(10));
        Map<String, PrometheusMetricDTO> results = new HashMap<>();
        for (var entry : QUERIES.entrySet()) {
            PrometheusMetricDTO dto = fetchRange(entry.getValue(), start, end);
            results.put(entry.getKey(), dto);
        }
        return results;
    }

    private PrometheusMetricDTO fetchRange(String rawQuery, Instant start, Instant end)
            throws IOException, InterruptedException {
        String q = URLEncoder.encode(rawQuery, StandardCharsets.UTF_8);

        String endpoint = String.format("%s%s", prometheusBaseUrl, PROMETHEUS_QUERY_PATH);

        String url = String.format(
                "%s?query=%s&start=%d&end=%d&step=30s",
                endpoint, q, start.getEpochSecond(), end.getEpochSecond()
        );

        System.out.println("Prometheus URL = " + url);

        HttpResponse<String> resp = httpClient.send(
                HttpRequest.newBuilder().uri(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofString()
        );
        if (resp.statusCode() != 200) {
            throw new IOException("Prometheus HTTP status: " + resp.statusCode() + " / " + resp.body());
        }

        JsonNode root = objectMapper.readTree(resp.body())
                .path("data").path("result");
        PrometheusMetricDTO dto = new PrometheusMetricDTO();
        Map<String, List<MetricSampleDTO>> map = new HashMap<>();
        for (JsonNode serie : root) {
            String instance = serie.path("metric").path("instance").asText();
            List<MetricSampleDTO> samples = new ArrayList<>();
            for (JsonNode val : serie.path("values")) {
                MetricSampleDTO sample = new MetricSampleDTO();
                sample.setTimestamp(Instant.ofEpochSecond(val.get(0).asLong()));
                sample.setValue(val.get(1).asDouble());
                samples.add(sample);
            }
            map.put(instance, samples);
        }
        dto.setMetrics(map);
        return dto;
    }


    /**
     * 3) 메트릭 병합 (timestamp → {metricName→값} 맵)
     */
    public Map<Instant, Map<String, Double>> mergeMetrics(
            Map<String, PrometheusMetricDTO> metrics
    ) {
        Map<String, Map<Instant, Double>> byMetric = new HashMap<>();
        for (var e : metrics.entrySet()) {
            Map<Instant, Double> flat = e.getValue().getMetrics().values().stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toMap(
                            MetricSampleDTO::getTimestamp,
                            MetricSampleDTO::getValue,
                            (a, b) -> b
                    ));
            byMetric.put(e.getKey(), flat);
        }

        NavigableSet<Instant> allTs = new TreeSet<>(Comparator.naturalOrder());
        byMetric.values().forEach(flat -> allTs.addAll(flat.keySet()));

        Map<Instant, Map<String, Double>> merged = new LinkedHashMap<>();
        for (Instant ts : allTs) {
            Map<String, Double> row = new HashMap<>();
            for (String name : byMetric.keySet()) {
                row.put(name, byMetric.get(name).get(ts));
            }
            merged.put(ts, row);
        }
        return merged;
    }

    /**
     * 4) 병합 결과를 MergedMetricSampleDTO 리스트로 평탄화
     */
    public List<MergedMetricsDTO> flatten(
            Map<Instant, Map<String, Double>> merged
    ) {
        return merged.entrySet().stream()
                .map(e -> new MergedMetricsDTO(
                        e.getKey(),
                        e.getValue().get("cpu"),
                        e.getValue().get("memory"),
                        e.getValue().get("disk"),
                        e.getValue().get("traffic")
                ))
                .collect(Collectors.toList());
    }

    /**
     * 5) 최종 페이로드 생성
     */
    public ObjectNode buildPayloadJson() throws Exception {
        Instant now = Instant.now();
        Instant past = now.minus(Duration.ofMinutes(10));

        // 1) 포맷된 로그
        List<Map<String,String>> logs = fetchAndFormatLogs();

        // 2) 메트릭
        var rawMetrics = fetchAllMetrics();
        var merged     = mergeMetrics(rawMetrics);
        var series     = flatten(merged);

        // 3) summary
        Map<String, Map<String, Double>> summary = Map.of(
                "cpu",    buildStat(merged, "cpu"),
                "memory", buildStat(merged, "memory"),
                "disk",   buildStat(merged, "disk"),
                "network",buildStat(merged, "network")
        );

        Map<String,Object> payload = Map.of(
                "window", Map.of("from", past, "to", now),
                "metrics", Map.of("summary", summary, "series", series),
                "logs", logs
        );

        return objectMapper.valueToTree(payload);
    }

    private Map<String, Double> buildStat(Map<Instant, Map<String, Double>> merged, String key) {
        DoubleSummaryStatistics stats = merged.values().stream()
                .map(m -> m.get(key))
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();
        return Map.of(
                "avg", stats.getAverage(),
                "max", stats.getMax(),
                "min", stats.getMin()
        );
    }

    public JsonNode getLastAnalysis() {
        return lastAnalysis;
    }

    public void setLastAnalysis(JsonNode analysis) {
        this.lastAnalysis = analysis;
    }

    public void clearLastAnalysis() {
        this.lastAnalysis = null;
    }

}
