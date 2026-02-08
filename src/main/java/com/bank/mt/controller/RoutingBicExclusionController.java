package com.bank.mt.controller;

import com.bank.mt.domain.RoutingBicExclusion;
import com.bank.mt.repository.RoutingBicExclusionRepository;
import com.bank.mt.routing.RoutingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/routing-exclusions")
public class RoutingBicExclusionController {

    private final RoutingBicExclusionRepository repository;
    private final RoutingService routingService;

    public RoutingBicExclusionController(RoutingBicExclusionRepository repository,
                                          RoutingService routingService) {
        this.repository = repository;
        this.routingService = routingService;
    }

    @GetMapping
    public List<RoutingBicExclusion> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<RoutingBicExclusion> create(@RequestBody RoutingBicExclusion exclusion) {
        exclusion.setId(null);
        RoutingBicExclusion saved = repository.save(exclusion);
        routingService.refreshCache();
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exclusion not found");
        }
        repository.deleteById(id);
        routingService.refreshCache();
        return ResponseEntity.noContent().build();
    }
}
