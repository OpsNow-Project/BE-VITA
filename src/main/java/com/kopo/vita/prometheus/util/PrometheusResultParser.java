package com.kopo.vita.prometheus.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kopo.vita.prometheus.dto.PodDTO;
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
            JsonNode root = mapper.readTree(json);
            JsonNode resultArray = root.path("data").path("result");

            if (resultArray.isArray() && resultArray.size() > 0) {
                JsonNode valueNode = resultArray.get(0).path("value");

                if (valueNode.isArray() && valueNode.size() == 2) {
                    String valueStr = valueNode.get(1).asText();
                    return Long.parseLong(valueStr);
                }
            }

        } catch (Exception e) {
            System.err.println("[extractSingleValueAsLong] Error while parsing JSON: " + e.getMessage());
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

    public List<PodDTO> extractPodDTOList(String json, ObjectMapper mapper) {
        List<PodDTO> podList = new ArrayList<>();
        try {
            JsonNode items = mapper.readTree(json).path("data").path("result");
            for (JsonNode item : items) {
                JsonNode metric = item.path("metric");

                String podName = metric.path("pod").asText(null);
                String podId = metric.path("uid").asText(null);
                String nameSpace = metric.path("namespace").asText(null);

                if (podName != null && !podName.isEmpty()
                        && podId != null && !podId.isEmpty()
                        && nameSpace != null && !nameSpace.isEmpty()) {
                    podList.add(PodDTO.builder()
                            .podName(podName)
                            .podId(podId)
                            .nameSpace(nameSpace)
                            .build());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return podList;
    }

    public static Map<String, Map<String, String>> extractLabels(String json, ObjectMapper objectMapper) {
        Map<String, Map<String, String>> result = new LinkedHashMap<>();

        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data").path("result");

            for (JsonNode item : data) {
                JsonNode metricNode = item.path("metric");
                Map<String, String> labels = new LinkedHashMap<>();
                metricNode.fieldNames().forEachRemaining(field -> {
                    labels.put(field, metricNode.path(field).asText());
                });

                // 키는 pod 이름 또는 pod+namespace 조합 등, 상황에 맞게 선택
                String key = labels.getOrDefault("pod", UUID.randomUUID().toString());
                result.put(key, labels);
            }

        } catch (Exception e) {
            e.printStackTrace(); // 로깅 시스템으로 대체 가능
        }

        return result;
    }

}
