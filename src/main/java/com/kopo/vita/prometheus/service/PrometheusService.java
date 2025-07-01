package com.kopo.vita.prometheus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kopo.vita.prometheus.dto.ClusterMetricsDTO;
import com.kopo.vita.prometheus.dto.PodDTO;
import com.kopo.vita.prometheus.util.PrometheusResultParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import com.kopo.vita.prometheus.query.PrometheusQueries;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PrometheusService {

    private final WebClient webClient;

    public PrometheusService(@Value("${prometheus.base-url}") String baseUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public Mono<String> query(String promql) {
        return webClient.post()
                .uri("/api/v1/query")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("query", promql))
                .retrieve()
                .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                        response -> response.bodyToMono(String.class)
                                .flatMap(body -> {
                                    return Mono.error(new RuntimeException("Error: " + body));
                                }))
                .bodyToMono(String.class);
    }

    public Mono<ClusterMetricsDTO> getClusterMetrics() {
        Map<String, String> queries = Map.ofEntries(
                Map.entry("nodeReadyCount", PrometheusQueries.NODE_READY_COUNT),
                Map.entry("nodeCpuCapacity", PrometheusQueries.NODE_CPU_CAPACITY),
                Map.entry("nodeMemoryCapacity", PrometheusQueries.NODE_MEMORY_CAPACITY),
                Map.entry("nodeCpuAllocatable", PrometheusQueries.NODE_CPU_ALLOCATABLE),
                Map.entry("nodeMemoryAllocatable", PrometheusQueries.NODE_MEMORY_ALLOCATABLE),
                Map.entry("nodeCpuUsage", PrometheusQueries.NODE_CPU_USAGE),
                Map.entry("nodeMemoryUsage", PrometheusQueries.NODE_MEMORY_USAGE),
                Map.entry("podTotalCount", PrometheusQueries.POD_TOTAL_COUNT),
                Map.entry("podRunningCount", PrometheusQueries.POD_RUNNING_COUNT),
                Map.entry("podPendingCount", PrometheusQueries.POD_PENDING_COUNT),
                Map.entry("podFailedCount", PrometheusQueries.POD_FAILED_COUNT),
                Map.entry("volumeUsedBytes", PrometheusQueries.VOLUME_USED_BYTES),
                Map.entry("volumeCapacityBytes", PrometheusQueries.VOLUME_CAPACITY_BYTES),
                Map.entry("networkTransmit", PrometheusQueries.NETWORK_TRANSMIT),
                Map.entry("networkReceive", PrometheusQueries.NETWORK_RECEIVE)
        );

        List<Mono<Map.Entry<String, String>>> queryMonos = queries.entrySet().stream()
                .map(entry ->
                        query(entry.getValue())
                                .map(result -> Map.entry(entry.getKey(), result))
                )
                .collect(Collectors.toList());

        return Mono.zip(queryMonos, results -> {
            Map<String, String> rawResultMap = new LinkedHashMap<>();
            for (Object obj : results) {
                @SuppressWarnings("unchecked")
                Map.Entry<String, String> entry = (Map.Entry<String, String>) obj;
                rawResultMap.put(entry.getKey(), entry.getValue());
            }

            return convertToDto(rawResultMap);
        });
    }

    private ClusterMetricsDTO convertToDto(Map<String, String> rawMap) {
        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, Double> usedMap = PrometheusResultParser.extractPvcMetric(rawMap.get("volumeUsedBytes"), objectMapper);
        Map<String, Double> capacityMap = PrometheusResultParser.extractPvcMetric(rawMap.get("volumeCapacityBytes"), objectMapper);

        Map<String, Double> volumeUsagePercent = new LinkedHashMap<>();
        for (String pvc : usedMap.keySet()) {
            if (capacityMap.containsKey(pvc) && capacityMap.get(pvc) > 0) {
                double percent = (usedMap.get(pvc) / capacityMap.get(pvc)) * 100.0;
                volumeUsagePercent.put(pvc, percent);
            }
        }

        return ClusterMetricsDTO.builder()
                .nodeReadyCount(PrometheusResultParser.extractSingleValueAsLong(rawMap.get("nodeReadyCount"), objectMapper))
                .nodeCpuCapacity(PrometheusResultParser.extractSingleValueAsDouble(rawMap.get("nodeCpuCapacity"), objectMapper))
                .nodeCpuAllocatable(PrometheusResultParser.extractSingleValueAsDouble(rawMap.get("nodeCpuAllocatable"), objectMapper))
                .nodeCpuUsage(PrometheusResultParser.extractNodeMetric(rawMap.get("nodeCpuUsage"), objectMapper))
                .nodeMemoryCapacity(PrometheusResultParser.extractSingleValueAsDouble(rawMap.get("nodeMemoryCapacity"), objectMapper))
                .nodeMemoryAllocatable(PrometheusResultParser.extractSingleValueAsDouble(rawMap.get("nodeMemoryAllocatable"), objectMapper))
                .nodeMemoryUsage(PrometheusResultParser.extractNodeMetric(rawMap.get("nodeMemoryUsage"), objectMapper))
                .podTotalCount(PrometheusResultParser.extractSingleValueAsLong(rawMap.get("podTotalCount"), objectMapper))
                .podRunningCount(PrometheusResultParser.extractSingleValueAsLong(rawMap.get("podRunningCount"), objectMapper))
                .podPendingCount(PrometheusResultParser.extractSingleValueAsLong(rawMap.get("podPendingCount"), objectMapper))
                .podFailedCount(PrometheusResultParser.extractSingleValueAsLong(rawMap.get("podFailedCount"), objectMapper))
                .networkTransmit(PrometheusResultParser.extractNodeMetric(rawMap.get("networkTransmit"), objectMapper))
                .networkReceive(PrometheusResultParser.extractNodeMetric(rawMap.get("networkReceive"), objectMapper))
                .volumeUsagePercent(volumeUsagePercent)
                .build();
    }

    public Mono<List<PodDTO>> getPodList() {
        ObjectMapper objectMapper = new ObjectMapper();

        return query(PrometheusQueries.POD_LIST)
                .map(json -> PrometheusResultParser.extractPodDTOList(json, objectMapper));
    }

    public Mono<PodDTO> getPodInfo(String podName, String namespace) {
        ObjectMapper objectMapper = new ObjectMapper();

        // PromQL 쿼리 정의
        Map<String, String> queries = Map.of(
                "cpu", String.format(PrometheusQueries.POD_CPU_USAGE, podName, namespace),
                "memory", String.format(PrometheusQueries.POD_MEMORY_USAGE, podName, namespace),
                "net_rx", String.format(PrometheusQueries.POD_NETWORK_TRANSMIT, podName, namespace),
                "net_tx", String.format(PrometheusQueries.POD_NETWORK_RECEIVE, podName, namespace),
                "podInfo", String.format(PrometheusQueries.POD_INFO, podName, namespace),
                "created", String.format(PrometheusQueries.POD_CREATED, podName, namespace),
                "runningStatus", String.format(PrometheusQueries.POD_STATUS_RUNNING, podName, namespace),
                "restartCount", String.format(PrometheusQueries.POD_RESTART_COUNT, podName, namespace)
        );

        List<Mono<Map.Entry<String, String>>> queryMonos = queries.entrySet().stream()
                .map(entry -> query(entry.getValue())
                        .map(result -> Map.entry(entry.getKey(), result)))
                .collect(Collectors.toList());

        return Mono.zip(queryMonos, results -> {
            Map<String, String> rawResultMap = new LinkedHashMap<>();
            for (Object obj : results) {
                @SuppressWarnings("unchecked")
                Map.Entry<String, String> entry = (Map.Entry<String, String>) obj;
                rawResultMap.put(entry.getKey(), entry.getValue());
            }

            double cpu = PrometheusResultParser.extractSingleValueAsDouble(rawResultMap.get("cpu"), objectMapper);
            double memory = PrometheusResultParser.extractSingleValueAsDouble(rawResultMap.get("memory"), objectMapper);
            double netRx = PrometheusResultParser.extractSingleValueAsDouble(rawResultMap.get("net_rx"), objectMapper);
            double netTx = PrometheusResultParser.extractSingleValueAsDouble(rawResultMap.get("net_tx"), objectMapper);

            Map<String, Map<String, String>> podInfoLabels = PrometheusResultParser.extractLabels(rawResultMap.get("podInfo"), objectMapper);
            String nodeName = null;
            String uid = null;
            if (!podInfoLabels.isEmpty()) {
                Map<String, String> labels = podInfoLabels.values().iterator().next();
                nodeName = labels.get("node");
                uid = labels.get("uid");
            }

            Double createdEpoch = PrometheusResultParser.extractSingleValueAsDouble(rawResultMap.get("created"), objectMapper);
            String createdAt = createdEpoch != null ? Instant.ofEpochSecond(createdEpoch.longValue()).toString() : null;

            Double runningVal = PrometheusResultParser.extractSingleValueAsDouble(rawResultMap.get("runningStatus"), objectMapper);
            String status = (runningVal != null && runningVal > 0) ? "Running" : "Not Running";

            Long restartCount = PrometheusResultParser.extractSingleValueAsLong(rawResultMap.get("restartCount"), objectMapper);
            restartCount = restartCount != null ? restartCount : 0L;

            return PodDTO.builder()
                    .podName(podName)
                    .nameSpace(namespace)
                    .cpu(cpu)
                    .memory(memory)
                    .networkReceive(netRx)
                    .networkTransmit(netTx)
                    .nodeName(nodeName)
                    .uid(uid)
                    .createdAt(createdAt)
                    .status(status)
                    .restartCount(restartCount)
                    .build();
        });
    }
}
