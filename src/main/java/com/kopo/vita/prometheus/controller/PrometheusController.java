package com.kopo.vita.prometheus.controller;

import com.kopo.vita.prometheus.dto.ClusterMetricsDTO;
import com.kopo.vita.prometheus.service.PrometheusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PrometheusController {

    private final PrometheusService prometheusService;

    @GetMapping("/metrics/cluster-summary")
    public Mono<ClusterMetricsDTO> getClusterSummary() {
        return prometheusService.getClusterMetrics();
    }
}
