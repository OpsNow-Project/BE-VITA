package com.kopo.vita.loganalysis.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
public class MergedMetricsDTO {
    private Instant timestamp;
    private Double cpuUsage;
    private Double memoryUsage;
    private Double diskUsage;
    private Double httpTraffic;

    public MergedMetricsDTO(Instant timestamp,
                            Double cpuUsage,
                            Double memoryUsage,
                            Double diskUsage,
                            Double networkRate) {
        this.timestamp = timestamp;
        this.cpuUsage = cpuUsage;
        this.memoryUsage = memoryUsage;
        this.diskUsage = diskUsage;
        this.httpTraffic = networkRate;
    }
}
