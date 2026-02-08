package com.bank.mt.controller;

import com.bank.mt.delivery.MockDeliveryAdapter;
import com.bank.mt.domain.DeliveryRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Test-only endpoints for inspecting mock deliveries.
 * Accessible without authentication for POC convenience.
 */
@RestController
@RequestMapping("/test")
public class TestController {

    private final MockDeliveryAdapter mockAdapter;

    public TestController(MockDeliveryAdapter mockAdapter) {
        this.mockAdapter = mockAdapter;
    }

    @GetMapping("/deliveries")
    public List<DeliveryRecord> getDeliveries() {
        return mockAdapter.getDeliveries();
    }

    @DeleteMapping("/deliveries")
    public ResponseEntity<Map<String, String>> clearDeliveries() {
        mockAdapter.clear();
        return ResponseEntity.ok(Map.of("status", "cleared"));
    }
}
