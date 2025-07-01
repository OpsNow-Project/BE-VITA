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
                        당신은 숙련된 SRE(사이트 신뢰성 엔지니어)입니다.
                        아래 JSON 형식의 로그와 메트릭 데이터를 분석하여 **운영자에게 보고하는 전문가답게** 다음 JSON 스키마로만 결과를 출력해 주세요.
                
                        출력 스키마 예:
                        {
                          "situation": "현재 상태를 간결히 요약해 주세요. (존댓말, 200자 이내)",
                          "analysis": "상세 분석 결과를 전문가답게 존댓말로 설명해 주세요. (500자 이내)",
                          "rootCause": "가능한 원인을 논리적으로 존댓말로 기술해 주세요. (500자 이내)",
                          "recommendations": [
                            {
                              "description": "권장 조치 내용을 존댓말로 작성해 주세요.",
                              "commands": ["실제 Shell 또는 kubectl 명령어"]
                            }
                          ]
                        }
                
                        작성 지침:
                        - 반드시 위 JSON 스키마 형태로만 순수 JSON을 출력해 주세요.
                        - 코드블록(예: ```json)이나 주석은 절대 쓰지 말아 주세요.
                        - 말투는 항상 존댓말을 사용해 일정하게 유지해 주세요.
                        - 상황과 문제를 운영자 관점에서 심층 분석해 주세요.
                        - 로그와 메트릭에서 근거를 찾아서 명확히 제시해 주세요.
                        - Recommendations 항목에는 실제 명령어와 구체적 실행 방법을 포함해 주세요.
                        - 각 필드 글자 수 제한을 반드시 지켜 주세요.
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

