package com.kopo.vita.metricchart.controller;

import com.kopo.vita.metricchart.dto.MetricDTO;
import com.kopo.vita.metricchart.service.MetricChartService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/metrics")
public class MetricChartController {
    private final MetricChartService service;

    public MetricChartController(MetricChartService service) {
        this.service = service;
    }

    /** JVM 프로세스 CPU 사용률 (%) */
    @PostMapping("/jvm/cpu")
    public List<MetricDTO> jvmCpu() {
        return service.getJvmCpuUsage();
    }

    /** JVM 힙 사용량 (MiB) */
    @PostMapping("/jvm/heap")
    public List<MetricDTO> jvmHeap() {
        return service.getJvmHeapUsage();
    }

    /** HTTP Request Rate (1m) */
    @PostMapping("/http/request-rate")
    public List<MetricDTO> httpRequestRate() {
        return service.getHttpRequestRate();
    }

    /** /app/. 볼륨 사용률 (%) */
    @PostMapping("/disk/app-volume-usage")
    public List<MetricDTO> appVolume() {
        return service.getAppVolumeUsage();
    }
}