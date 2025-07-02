package com.kopo.vita.loganalysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * key: metric name or series identifier (e.g. instance label)
 * value: list of 샘플(timestamp + value)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrometheusMetricDTO {
    private Map<String, List<MetricSampleDTO>> metrics;
}
