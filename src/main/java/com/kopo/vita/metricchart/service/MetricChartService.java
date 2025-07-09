package com.kopo.vita.metricchart.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kopo.vita.metricchart.dto.DataPointDTO;
import com.kopo.vita.metricchart.dto.MetricDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class MetricChartService {

    private final RestTemplate restTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${prometheus.base-url}")
    private String prometheusUrl;

    private static final Duration RANGE = Duration.ofMinutes(20);
    private static final long STEP = 60L;

    public MetricChartService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<MetricDTO> getJvmCpuUsage() {
        return queryRange("process_cpu_usage * 100");
    }

    public List<MetricDTO> getJvmHeapUsage() {
        return queryRange("jvm_memory_used_bytes{area=\"heap\"} / (1024*1024)");
    }

    public List<MetricDTO> getHttpRequestRate() {
        return queryRange("sum by (uri) (rate(http_server_requests_seconds_count[1m]))");
    }

    public List<MetricDTO> getAppVolumeUsage() {
        return queryRange("100 * (1 - (disk_free_bytes{path=\"/app/.\"} / disk_total_bytes{path=\"/app/.\"}))");
    }

    private List<MetricDTO> queryRange(String promql) {
        Instant end   = Instant.now();
        Instant start = end.minus(RANGE);

        String url = prometheusUrl + "/api/v1/query_range";

        MultiValueMap<String,String> form = new LinkedMultiValueMap<>();
        form.add("query", promql);
        form.add("start", String.valueOf(start.getEpochSecond()));
        form.add("end",   String.valueOf(end.getEpochSecond()));
        form.add("step",  STEP + "s");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String,String>> requestEntity = new HttpEntity<>(form, headers);

        String json = restTemplate.postForObject(url, requestEntity, String.class);

        return parsePrometheusJson(json);
    }

    private List<MetricDTO> parsePrometheusJson(String json) {
        List<MetricDTO> list = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(json)
                    .path("data")
                    .path("result");
            for (JsonNode series : root) {
                String seriesName = series.path("metric").toString();
                // 필요시 metric 레이블만 추출해 가공 가능
                List<DataPointDTO> points = new ArrayList<>();
                for (JsonNode pair : series.path("values")) {
                    long ts = pair.get(0).asLong();
                    double val = pair.get(1).asDouble();
                    points.add(new DataPointDTO(Instant.ofEpochSecond(ts), val));
                }
                list.add(new MetricDTO(seriesName, points));
            }
        } catch (Exception e) {
            throw new RuntimeException("Prometheus JSON parsing failed", e);
        }
        return list;
    }
}