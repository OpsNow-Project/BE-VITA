package com.kopo.vita.metricchart.dto;

import lombok.Data;

import java.time.Instant;

@Data
public class DataPointDTO {
    private Instant timestamp;
    private double value;

    public DataPointDTO(Instant timestamp, double value) {
        this.timestamp = timestamp;
        this.value = value;
    }
}
