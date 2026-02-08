package com.bank.mt.controller;

import com.bank.mt.ruleloader.RuleLoaderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rules")
public class RuleLoaderController {

    private final RuleLoaderService ruleLoaderService;

    public RuleLoaderController(RuleLoaderService ruleLoaderService) {
        this.ruleLoaderService = ruleLoaderService;
    }

    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reloadFromFile() {
        int count = ruleLoaderService.loadRulesFromFile();
        return ResponseEntity.ok(Map.of("loaded", count, "source", "FILE"));
    }
}
