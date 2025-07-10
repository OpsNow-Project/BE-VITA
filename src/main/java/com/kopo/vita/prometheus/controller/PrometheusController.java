package com.kopo.vita.prometheus.controller;

import com.kopo.vita.prometheus.dto.ClusterMetricsDTO;
import com.kopo.vita.prometheus.dto.PodDTO;
import com.kopo.vita.prometheus.service.PrometheusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PrometheusController {

    private final PrometheusService prometheusService;

//    @GetMapping("/api/metrics/cluster-summary")
//    public Mono<ClusterMetricsDTO> getClusterSummary() {
//        return prometheusService.getClusterMetrics();
//    }
//
//    @GetMapping("/api/pod/list")
//    public Mono<List<PodDTO>> getPodList() {
//        return prometheusService.getPodList();
//    }
//
//    @GetMapping("/api/pod/info")
//    public Mono<PodDTO> getPodInfo(String podName, String nameSpace) {
//        return prometheusService.getPodInfo(podName, nameSpace);
//    }
    @GetMapping("/api/pod/list-detail")
    public Mono<List<PodDTO>> getPodFullList() {
        return prometheusService.getPodFullList();
    }
}
