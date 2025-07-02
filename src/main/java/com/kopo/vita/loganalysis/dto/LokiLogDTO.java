package com.kopo.vita.loganalysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LokiLogDTO {
    private Instant timestamp;
    private String stream;
    private String line;

}