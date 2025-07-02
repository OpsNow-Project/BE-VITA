package com.kopo.vita.loganalysis.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class LogFormatter {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    // CPU, JVM 메모리, 스레드 수, 프로세서 수, 디스크 사용률
    public static List<Map<String, String>> formatRawLokiLogs(String rawJsonArray) throws Exception {
        JsonNode arr = MAPPER.readTree(rawJsonArray);

        // 1) 시스템 메트릭 패턴
        Pattern METRIC_PATTERN = Pattern.compile(
                "CPU 부하:\\s*([\\d.]+),\\s*JVM 메모리:\\s*([\\d.]+)%,.*?스레드 수:\\s*(\\d+),\\s*프로세서 수:\\s*(\\d+),.*?디스크:\\s*[^:]+:\\s*([\\d.]+)%"
        );
        // 2) GC 로그 패턴
        Pattern GC_PATTERN = Pattern.compile(
                "\\[(?<uptime>[\\d.]+s)]\\[info.+?] GC\\((?<id>\\d+)\\) (?<type>[^\\[]+)\\s*(?<detail>.+)"
        );

        return StreamSupport.stream(arr.spliterator(), false)
                .map(node -> {
                    String ts = node.path("timestamp").asText();
                    String streamJson = node.path("stream").asText("{}");
                    Map<String,String> labels = null;
                    try {
                        labels = MAPPER.readValue(streamJson, new TypeReference<>(){});
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                    String line = node.path("line").asText().replaceAll("[\\r\\n]", "");
                    // 1) 시스템 메트릭 매칭
                    Matcher m1 = METRIC_PATTERN.matcher(line);
                    if (m1.find()) {
                        Map<String,String> out = new LinkedHashMap<>();
                        out.put("timestamp", ts);
                        out.put("app", labels.get("app"));
                        out.put("pod", extractPodName(labels.get("filename")));
                        out.put("cpuLoad", m1.group(1));
                        out.put("jvmMemory", m1.group(2));
                        out.put("threadCount", m1.group(3));
                        out.put("processorCount", m1.group(4));
                        out.put("diskUsage", m1.group(5));
                        return out;
                    }
                    // 2) GC 로그 매칭
                    Matcher m2 = GC_PATTERN.matcher(line);
                    if (m2.find()) {
                        Map<String,String> out = new LinkedHashMap<>();
                        out.put("timestamp", ts);
                        out.put("app", labels.get("app"));
                        out.put("pod", extractPodName(labels.get("filename")));
                        out.put("gcUptime", m2.group("uptime"));
                        out.put("gcId", m2.group("id"));
                        out.put("gcType", m2.group("type").trim());
                        out.put("gcDetail", m2.group("detail").trim());
                        return out;
                    }
                    // 다른 로그는 무시
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // filename 경로에서 파드 이름 추출 예시
    private static String extractPodName(String filename) {
        // default_namespace_이후부터 첫 번째 '/' 전까지
        int start = filename.indexOf('_') + 1;
        int end = filename.indexOf('/', start);
        return end > start ? filename.substring(start, end) : filename;
    }

}
