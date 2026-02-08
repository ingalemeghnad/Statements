package com.bank.mt.controller;

import com.bank.mt.domain.RoutingRule;
import com.bank.mt.domain.RuleSource;
import com.bank.mt.repository.RoutingRuleRepository;
import com.bank.mt.routing.RoutingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/routing-rules")
public class RoutingRuleController {

    private final RoutingRuleRepository repository;
    private final RoutingService routingService;

    public RoutingRuleController(RoutingRuleRepository repository, RoutingService routingService) {
        this.repository = repository;
        this.routingService = routingService;
    }

    @GetMapping
    public List<RoutingRule> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<RoutingRule> create(@Valid @RequestBody RoutingRule rule) {
        rule.setId(null);
        rule.setSource(RuleSource.UI);
        RoutingRule saved = repository.save(rule);
        routingService.refreshCache();
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public RoutingRule update(@PathVariable Long id, @Valid @RequestBody RoutingRule rule) {
        RoutingRule existing = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found"));
        existing.setAccountNumber(rule.getAccountNumber());
        existing.setMessageType(rule.getMessageType());
        existing.setSenderBic(rule.getSenderBic());
        existing.setReceiverBic(rule.getReceiverBic());
        existing.setDestinationQueue(rule.getDestinationQueue());
        existing.setSecondaryDestinations(rule.getSecondaryDestinations());
        existing.setActive(rule.isActive());
        RoutingRule saved = repository.save(existing);
        routingService.refreshCache();
        return saved;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Rule not found");
        }
        repository.deleteById(id);
        routingService.refreshCache();
        return ResponseEntity.noContent().build();
    }
}
