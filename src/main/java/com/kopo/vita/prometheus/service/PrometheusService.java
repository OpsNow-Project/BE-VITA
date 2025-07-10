package com.kopo.vita.prometheus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kopo.vita.prometheus.dto.PodDTO;
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

}
