package com.bank.mt.controller;

import com.bank.mt.domain.MtAggregation;
import com.bank.mt.domain.MtMessageOds;
import com.bank.mt.domain.OdsStatus;
import com.bank.mt.ingestion.MqIngestionStrategy;
import com.bank.mt.repository.MtAggregationRepository;
import com.bank.mt.repository.MtMessageOdsRepository;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/test")
public class OdsController {

    private final MtMessageOdsRepository odsRepository;
    private final MtAggregationRepository aggregationRepository;
    private final MqIngestionStrategy mqIngestion;

    public OdsController(MtMessageOdsRepository odsRepository,
                         MtAggregationRepository aggregationRepository,
                         MqIngestionStrategy mqIngestion) {
        this.odsRepository = odsRepository;
        this.aggregationRepository = aggregationRepository;
        this.mqIngestion = mqIngestion;
    }

    @GetMapping("/ods-messages")
    public List<MtMessageOds> getAllOdsMessages() {
        return odsRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    /**
     * Simulates receiving a message from MQ inbound queue.
     * In production, the @JmsListener in MqIngestionStrategy handles this automatically.
     */
    @PostMapping("/ods-messages")
    public ResponseEntity<Map<String, String>> submitOdsMessage(@RequestBody Map<String, String> body) {
        String rawMessage = body.get("rawMessage");
        mqIngestion.onMessage(rawMessage);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("status", "processed", "queue", "MT.INBOUND"));
    }

    @GetMapping("/ods-messages/stats")
    public Map<String, Long> getOdsStats() {
        List<MtMessageOds> all = odsRepository.findAll();
        Map<String, Long> stats = new LinkedHashMap<>();
        for (OdsStatus s : OdsStatus.values()) {
            stats.put(s.name(), all.stream().filter(m -> m.getStatus() == s).count());
        }
        stats.put("TOTAL", (long) all.size());
        return stats;
    }

    @GetMapping("/aggregations")
    public List<MtAggregation> getAllAggregations() {
        return aggregationRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }
}
