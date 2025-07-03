package com.kopo.vita.k8scommand.controller;

import com.kopo.vita.k8scommand.dto.ExecRequest;
import com.kopo.vita.k8scommand.service.K8sCommandService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/cli")
public class K8sCommandController {
    private final K8sCommandService k8sService;

    public K8sCommandController(K8sCommandService k8sService) {
        this.k8sService = k8sService;
    }

    @PostMapping("/exec")
    public ResponseEntity<?> execCli(@RequestBody ExecRequest req) {
        Map<String, Object> response = new HashMap<>();
        response.put("command", req.getCommand());
        try {
            Object result = k8sService.execute(req.getCommand());
            response.put("result", result);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        } catch (Exception e) {
            response.put("error", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

}
