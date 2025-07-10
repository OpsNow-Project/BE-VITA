package com.kopo.vita.prometheus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kopo.vita.prometheus.dto.ClusterMetricsDTO;
import com.kopo.vita.prometheus.dto.PodDTO;
import com.kopo.vita.prometheus.query.PrometheusQueries;
import com.kopo.vita.prometheus.util.PrometheusResultParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.*;
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
                .bodyToMono(String.class);
    }

    public Mono<List<PodDTO>> getPodFullList() {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> queries = Map.of(
                "cpu", "sum(rate(container_cpu_usage_seconds_total{container!=\"\"}[5m])) by (namespace, pod)",
                "memory", "sum(container_memory_usage_bytes{container!=\"\"}) by (namespace, pod)",
                "netTx", "sum(rate(container_network_transmit_bytes_total{container!=\"\"}[5m])) by (namespace, pod)",
                "netRx", "sum(rate(container_network_receive_bytes_total{container!=\"\"}[5m])) by (namespace, pod)",
                "created", "max(kube_pod_created) by (namespace, pod)",
                "restarts", "sum(kube_pod_container_status_restarts_total) by (namespace, pod)",
                // (1) 모든 상태 phase 한 번에
                "phase", "kube_pod_status_phase",
                // (2) CrashLoopBackOff 등 특별한 상태
                "crash", "max(kube_pod_container_status_waiting_reason{reason=\"CrashLoopBackOff\"}) by (namespace, pod)",
                "info", "kube_pod_info"
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

            Map<String, Map<String, Double>> cpuMap = PrometheusResultParser.extractPodMetric(rawResultMap.get("cpu"), objectMapper);
            Map<String, Map<String, Double>> memoryMap = PrometheusResultParser.extractPodMetric(rawResultMap.get("memory"), objectMapper);
            Map<String, Map<String, Double>> netTxMap = PrometheusResultParser.extractPodMetric(rawResultMap.get("netTx"), objectMapper);
            Map<String, Map<String, Double>> netRxMap = PrometheusResultParser.extractPodMetric(rawResultMap.get("netRx"), objectMapper);
            Map<String, Map<String, Double>> createdMap = PrometheusResultParser.extractPodMetric(rawResultMap.get("created"), objectMapper);
            Map<String, Map<String, Double>> restartsMap = PrometheusResultParser.extractPodMetric(rawResultMap.get("restarts"), objectMapper);

            // (1) 모든 phase 정보
            Map<String, Map<String, Map<String, Double>>> phaseMap = PrometheusResultParser.extractPodPhaseMetric(rawResultMap.get("phase"), objectMapper);
            // (2) CrashLoopBackOff 상태
            Map<String, Map<String, Double>> crashMap = PrometheusResultParser.extractPodMetric(rawResultMap.get("crash"), objectMapper);

            List<PodDTO> podInfos = PrometheusResultParser.extractPodDTOList(rawResultMap.get("info"), objectMapper);

            List<PodDTO> fullList = podInfos.stream().map(pod -> {
                String ns = pod.getNameSpace();
                String name = pod.getPodName();

                double cpu = cpuMap.getOrDefault(ns, Collections.emptyMap()).getOrDefault(name, 0.0);
                double memory = memoryMap.getOrDefault(ns, Collections.emptyMap()).getOrDefault(name, 0.0) / 1024 / 1024;
                double netTx = netTxMap.getOrDefault(ns, Collections.emptyMap()).getOrDefault(name, 0.0);
                double netRx = netRxMap.getOrDefault(ns, Collections.emptyMap()).getOrDefault(name, 0.0);
                Double createdEpoch = createdMap.getOrDefault(ns, Collections.emptyMap()).getOrDefault(name, null);
                String createdAt = createdEpoch != null ? Instant.ofEpochSecond(createdEpoch.longValue()).toString() : null;
                Long restartCount = restartsMap.getOrDefault(ns, Collections.emptyMap()).getOrDefault(name, 0.0).longValue();

                // (1) 모든 phase 한 번에 처리
                String status = "Unknown";
                Map<String, Double> podPhases = phaseMap.getOrDefault(ns, Collections.emptyMap()).getOrDefault(name, Collections.emptyMap());
                // (2) CrashLoopBackOff 등 특별상태
                if (crashMap.getOrDefault(ns, Collections.emptyMap()).getOrDefault(name, 0.0) > 0) {
                    status = "CrashLoopBackOff";
                } else if (podPhases.getOrDefault("Failed", 0.0) > 0) {
                    status = "Failed";
                } else if (podPhases.getOrDefault("Pending", 0.0) > 0) {
                    status = "Pending";
                } else if (podPhases.getOrDefault("Succeeded", 0.0) > 0) {
                    status = "Succeeded";
                } else if (podPhases.getOrDefault("Running", 0.0) > 0) {
                    status = "Running";
                }

                return PodDTO.builder()
                        .podName(name)
                        .podId(pod.getPodId())
                        .nameSpace(ns)
                        .cpu(cpu)
                        .memory(memory)
                        .networkTransmit(netTx)
                        .networkReceive(netRx)
                        .createdAt(createdAt)
                        .restartCount(restartCount)
                        .status(status)
                        .nodeName(pod.getNodeName())
                        .uid(pod.getUid())
                        .build();
            }).collect(Collectors.toList());

            return fullList;
        });
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
