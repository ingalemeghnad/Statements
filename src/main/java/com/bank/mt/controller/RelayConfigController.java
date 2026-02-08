package com.bank.mt.controller;

import com.bank.mt.domain.RelayConfig;
import com.bank.mt.repository.RelayConfigRepository;
import com.bank.mt.routing.RoutingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/relay-config")
public class RelayConfigController {

    private final RelayConfigRepository repository;
    private final RoutingService routingService;

    public RelayConfigController(RelayConfigRepository repository, RoutingService routingService) {
        this.repository = repository;
        this.routingService = routingService;
    }

    @GetMapping
    public List<RelayConfig> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<RelayConfig> create(@Valid @RequestBody RelayConfig config) {
        config.setId(null);
        RelayConfig saved = repository.save(config);
        routingService.refreshCache();
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public RelayConfig update(@PathVariable Long id, @Valid @RequestBody RelayConfig config) {
        RelayConfig existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Relay config not found"));
        existing.setAccountNumber(config.getAccountNumber());
        existing.setSenderBic(config.getSenderBic());
        existing.setReceiverBic(config.getReceiverBic());
        existing.setActive(config.isActive());
        RelayConfig saved = repository.save(existing);
        routingService.refreshCache();
        return saved;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Relay config not found");
        }
        repository.deleteById(id);
        routingService.refreshCache();
        return ResponseEntity.noContent().build();
    }
}
