package com.kopo.vita.loganalysis.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class LogFormatter {
    public static final ObjectMapper MAPPER = new ObjectMapper();

    // 필요한 부분만 뽑아낼 정규식
    private static final Pattern METRIC_PATTERN = Pattern.compile(
            "CPU 부하:\\s*([\\d.]+),\\s*JVM 메모리:\\s*([\\d.]+)%,.*?스레드 수:\\s*(\\d+),\\s*프로세서 수:\\s*(\\d+),.*?디스크:\\s*[^:]+:\\s*([\\d.]+)%"
    );

    /**
     * raw Loki API 응답(JSON 배열)을 아래 포맷으로 바꿔줍니다.
     * [
     *   {
     *     "ts":"2025-07-01T07:55:27.352Z",
     *     "cpu부하":"0.01",
     *     "jvm메모리":"70.97",
     *     "스레드수":"27",
     *     "프로세서수":"32",
     *     "디스크사용률":"5.8"
     *   },
     *   …
     * ]
     */
    public static List<Map<String, String>> formatRawLokiLogs(String rawJsonArray) throws Exception {
        JsonNode arr = MAPPER.readTree(rawJsonArray);

        return StreamSupport.stream(arr.spliterator(), false)
                .map(node -> {
                    String ts = node.path("timestamp").asText();
                    String line = node.path("line").asText("");
                    // CRLF 제거
                    line = line.replace("\r", "").replace("\n", "");
                    // prefix 제거
                    line = line.replaceFirst("^.*?--- .*?\\s*:\\s*", "");

                    Matcher m = METRIC_PATTERN.matcher(line);
                    if (m.find()) {
                        Map<String, String> out = new LinkedHashMap<>();
                        out.put("ts", ts);
                        out.put("cpu부하",    m.group(1));
                        out.put("jvm메모리",  m.group(2));
                        out.put("스레드수",    m.group(3));
                        out.put("프로세서수",  m.group(4));
                        out.put("디스크사용률", m.group(5));
                        return out;
                    }
                    // 매칭 안 되면 null 리턴
                    return null;
                })
                // null 또는 ts 외에 필드가 없는 Map 제거
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

}
