package com.kopo.vita.k8scommand.controller;

import com.kopo.vita.k8scommand.dto.ExecRequest;
import com.kopo.vita.k8scommand.service.K8sCommandService;
import com.kopo.vita.loganalysis.service.LogAnalysisService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/cli")
public class K8sCommandController {

    private final K8sCommandService k8sService;
    private final LogAnalysisService logAnalysisService;

    public K8sCommandController(K8sCommandService k8sService, LogAnalysisService logAnalysisService) {
        this.k8sService = k8sService;
        this.logAnalysisService = logAnalysisService;
    }

    @PostMapping("/exec")
    public ResponseEntity<?> execCli(@RequestBody ExecRequest req) {
        Map<String, Object> response = new HashMap<>();
        response.put("command", req.getCommand());
        try {
            Object result = k8sService.execute(req.getCommand());
            response.put("result", result);
            response.put("success", true);
            logAnalysisService.clearLastAnalysis();
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("error", e.getMessage());
            response.put("success", false);
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            response.put("success", false);
            return ResponseEntity.status(500).body(response);
        }
    }

}
