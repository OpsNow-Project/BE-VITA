package com.kopo.vita.loganalysis.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
public class MetricSampleDTO {
    private Instant timestamp;
    private Double value;

}
