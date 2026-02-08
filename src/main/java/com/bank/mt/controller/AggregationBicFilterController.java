package com.bank.mt.controller;

import com.bank.mt.aggregation.AggregationFilter;
import com.bank.mt.domain.AggregationBicFilter;
import com.bank.mt.repository.AggregationBicFilterRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/aggregation-filters")
public class AggregationBicFilterController {

    private final AggregationBicFilterRepository repository;
    private final AggregationFilter aggregationFilter;

    public AggregationBicFilterController(AggregationBicFilterRepository repository,
                                           AggregationFilter aggregationFilter) {
        this.repository = repository;
        this.aggregationFilter = aggregationFilter;
    }

    @GetMapping
    public List<AggregationBicFilter> getAll() {
        return repository.findAll();
    }

    @PostMapping
    public ResponseEntity<AggregationBicFilter> create(@RequestBody AggregationBicFilter filter) {
        filter.setId(null);
        AggregationBicFilter saved = repository.save(filter);
        aggregationFilter.refreshCache();
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Filter not found");
        }
        repository.deleteById(id);
        aggregationFilter.refreshCache();
        return ResponseEntity.noContent().build();
    }
}
