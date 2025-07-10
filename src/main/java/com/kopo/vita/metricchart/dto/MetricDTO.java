package com.kopo.vita.metricchart.dto;

import java.util.List;

public class MetricDTO {
    private String seriesName;
    private List<DataPointDTO> dataPoints;

    public MetricDTO(String seriesName, List<DataPointDTO> dataPoints) {
        this.seriesName = seriesName;
        this.dataPoints = dataPoints;
    }

    public String getSeriesName() {
        return seriesName;
    }

    public List<DataPointDTO> getDataPoints() {
        return dataPoints;
    }}
