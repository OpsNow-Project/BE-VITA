package com.kopo.vita.loganalysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiRequestDTO {
    private List<LokiLogDTO> logs;
    private PrometheusMetricDTO metrics;

}