package com.kopo.vita.loganalysis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class GeminiClientService {
    @Value("${gemini.endpoint}")
    private String endpoint;

    @Value("${gemini.apiKey}")
    private String apiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiClientService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient   = HttpClient.newHttpClient();
    }

    // gemini 한테 보낼 body 작성
    public ObjectNode buildGeminiRequest(ObjectNode payload) {
        ObjectNode root = objectMapper.createObjectNode();
        ArrayNode contents = root.putArray("contents");

        ObjectNode sysMsg = objectMapper.createObjectNode();
        sysMsg.put("role", "user");
        ArrayNode sysParts = sysMsg.putArray("parts");
        sysParts.addObject()
                .put("text",
                        """
                        You are a skilled SRE (Site Reliability Engineer). \s
                        Below JSON contains only metrics.series and metrics.summary from the last 10 minutes.

                        Requirements:
                        1. Detect all anomalies in cpuUsage, memoryUsage, diskUsage, httpTraffic:
                           – sudden spikes (e.g., ≥5× average),
                           – sustained trends (≥3 consecutive increases or decreases),
                           – deviations outside normal range (avg ± stddev).

                        2. For each anomaly:
                           – Identify the exact pod and container (use label “podName” and container[0]).
                           – Specify timestamp, metric value, baseline (summary.avg or summary.max) and multiplier/delta.
                           – Link to the closest log entry (by ts) and include its key fields (jvmMemory, thread count, GC pause if available).

                        3. In `recommendations`, for **each detected issue**, include:
                           – At least one **kubectl get/describe/logs** command to inspect the current state of the affected pod/container.
                           – A **kubectl patch** or **kubectl set env** command to apply a tangible fix (resource adjustment or JVM tuning).
                           – If a restart is needed, include **kubectl rollout restart** for the deployment.

                        Return **only** this JSON schema, with polite Korean text and strict character limits (no code blocks):

                        {
                          "situation": "200자 이내, 현재 상태 요약 (존댓말)",
                          "analysis": "500자 이내, 이상 발생 시점·파드·수치·로그 연계 분석 (존댓말)",
                          "rootCause": "500자 이내, 근본 원인 논리 서술 (존댓말)",
                          "recommendations": [
                            {
                              "description": "권장 조치 설명 (존댓말)",
                              "commands": [
                                "<issue에 맞는 kubectl get/describe/logs 명령어>",
                                "<issue에 맞는 kubectl patch 또는 set env 명령어>",
                                "<필요 시 kubectl rollout restart 명령어>"
                              ]
                            }
                          ]
                        }

                        Do not use code blocks in the output. 답변은 한국어로 해 주세요. \s

                                                """
                );
        contents.add(sysMsg);

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        ArrayNode userParts = userMsg.putArray("parts");
        userParts.addObject()
                .put("text", payload.toPrettyString());
        contents.add(userMsg);

        System.out.println(root);
        return root;
    }
    // gemini api 요청
    public String analyzeWithGemini(ObjectNode geminiRequest) throws IOException, InterruptedException {

        String uri = endpoint + "?key=" + apiKey;
        String body = objectMapper.writeValueAsString(geminiRequest);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("Gemini API error: " + resp.statusCode() + " / " + resp.body());
        }
        return resp.body();
    }

    // 받은 응답을 원하는 형태로 정제
    public JsonNode callGeminiAndParse(ObjectNode geminiRequest) throws IOException, InterruptedException {
        String geminiResponse = analyzeWithGemini(geminiRequest);

        // Gemini 응답 파싱
        JsonNode root = objectMapper.readTree(geminiResponse);
        String rawText = root
                .path("candidates").get(0)
                .path("content")
                .path("parts").get(0)
                .path("text")
                .asText();

        // 코드블럭 제거
        String cleaned = rawText
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();

        return objectMapper.readTree(cleaned);
    }

}

