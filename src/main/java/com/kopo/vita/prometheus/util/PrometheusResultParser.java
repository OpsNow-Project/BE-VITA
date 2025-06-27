package com.kopo.vita.prometheus.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

import java.util.*;

@UtilityClass
public class PrometheusResultParser {

    public Map<String, Double> extractPvcMetric(String json, ObjectMapper mapper) {
        Map<String, Double> result = new LinkedHashMap<>();
        try {
            JsonNode items = mapper.readTree(json).path("data").path("result");
            for (JsonNode item : items) {
                String pvc = item.path("metric").path("persistentvolumeclaim").asText();
                double value = item.path("value").get(1).asDouble();
                result.put(pvc, value);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public Map<String, Double> extractNodeMetric(String json, ObjectMapper mapper) {
        Map<String, Double> result = new LinkedHashMap<>();
        try {
            JsonNode items = mapper.readTree(json).path("data").path("result");
            for (JsonNode item : items) {
                String node = item.path("metric").path("node").asText();
                double value = item.path("value").get(1).asDouble();
                result.put(node, value);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public long extractSingleValueAsLong(String json, ObjectMapper mapper) {
        try {
            JsonNode items = mapper.readTree(json).path("data").path("result");
            if (items.isArray() && items.size() > 0) {
                return items.get(0).path("value").get(1).asLong();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public double extractSingleValueAsDouble(String json, ObjectMapper mapper) {
        try {
            JsonNode items = mapper.readTree(json).path("data").path("result");
            if (items.isArray() && items.size() > 0) {
                return items.get(0).path("value").get(1).asDouble();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0.0;
    }

}
