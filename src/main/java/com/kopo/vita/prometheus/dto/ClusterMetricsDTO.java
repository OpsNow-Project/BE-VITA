package com.kopo.vita.prometheus.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ClusterMetricsDTO {

    private long nodeReadyCount;
    private double nodeCpuCapacity;
    private double nodeCpuAllocatable;
    private Map<String, Double> nodeCpuUsage;

    private double nodeMemoryCapacity;
    private double nodeMemoryAllocatable;
    private Map<String, Double> nodeMemoryUsage;

    private long podTotalCount;
    private long podRunningCount;
    private long podPendingCount;
    private long podFailedCount;

    private Map<String, Double> networkTransmit;
    private Map<String, Double> networkReceive;

    private Map<String, Double> volumeUsagePercent;  // PVC 이름별 사용률 (%)

}
