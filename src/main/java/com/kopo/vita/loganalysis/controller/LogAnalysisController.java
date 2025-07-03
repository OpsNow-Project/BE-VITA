package com.kopo.vita.loganalysis.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kopo.vita.loganalysis.service.GeminiClientService;
import com.kopo.vita.loganalysis.service.LogAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/log")
public class LogAnalysisController {

    private final GeminiClientService geminiClientService;
    private final ObjectMapper objectMapper;
    private final LogAnalysisService logservice;

    public LogAnalysisController(GeminiClientService gemini, ObjectMapper objectMapper, LogAnalysisService logservice) {
        this.geminiClientService = gemini;
        this.objectMapper = objectMapper;
        this.logservice = logservice;

    }

    @PostMapping("/analyze")
    public ResponseEntity<JsonNode> analyze() {
        try {
            ObjectNode payload = logservice.buildPayloadJson();
            ObjectNode geminiRequest = geminiClientService.buildGeminiRequest(payload);
            JsonNode analysis = geminiClientService.callGeminiAndParse(geminiRequest);
            logservice.setLastAnalysis(analysis);
            return ResponseEntity.ok(analysis);

        } catch (Exception e) {
            e.printStackTrace();
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", e.getMessage());
            return ResponseEntity.status(500).body(error);
        }
    }

    @GetMapping("/analyze")
    public ResponseEntity<JsonNode> getLastAnalysis() {
        JsonNode last = logservice.getLastAnalysis();
        if (last == null) {
            ObjectNode msg = objectMapper.createObjectNode();
            msg.put("message", "아직 분석된 결과가 없습니다.");
            return ResponseEntity.status(404).body(msg);
        }
        return ResponseEntity.ok(last);
    }

}
