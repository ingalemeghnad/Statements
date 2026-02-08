package com.bank.mt.aggregation;

import com.bank.mt.domain.AggregationFilterType;
import com.bank.mt.domain.MtStatement;
import com.bank.mt.repository.AggregationBicFilterRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Determines whether a parsed message should go through aggregation
 * or skip directly to routing.
 *
 * Reads filter configuration from the aggregation_bic_filter table.
 * Aggregation applies only when:
 *   1. The receiver BIC (8-char) is in the ALLOWED_BIC list
 *   2. The branch code (chars 9-11 of full BIC) is NOT in the EXCLUDED_BRANCH list
 */
@Component
public class AggregationFilter {

    private static final Logger log = LoggerFactory.getLogger(AggregationFilter.class);

    private final AggregationBicFilterRepository repository;

    private volatile Set<String> allowedReceiverBics = Set.of();
    private volatile Set<String> excludedBranchCodes = Set.of();

    public AggregationFilter(AggregationBicFilterRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void refreshCache() {
        allowedReceiverBics = repository.findByFilterTypeAndActiveTrue(AggregationFilterType.ALLOWED_BIC)
                .stream()
                .map(f -> f.getBicValue().toUpperCase())
                .collect(Collectors.toSet());
        excludedBranchCodes = repository.findByFilterTypeAndActiveTrue(AggregationFilterType.EXCLUDED_BRANCH)
                .stream()
                .map(f -> f.getBicValue().toUpperCase())
                .collect(Collectors.toSet());
        log.info("Aggregation filter cache refreshed: allowed BICs={}, excluded branches={}",
                allowedReceiverBics, excludedBranchCodes);
    }

    /**
     * Returns true if the message should be aggregated.
     */
    public boolean shouldAggregate(MtStatement statement) {
        // If no allowed BICs configured, aggregate everything (backward compat)
        if (allowedReceiverBics.isEmpty()) {
            return true;
        }

        String receiverBic = statement.getReceiverBic();
        if (receiverBic == null || !allowedReceiverBics.contains(receiverBic.toUpperCase())) {
            log.debug("Skipping aggregation: receiver BIC {} not in allowed list", receiverBic);
            return false;
        }

        String branch = statement.getReceiverBicBranch();
        if (branch != null && !branch.isBlank() && excludedBranchCodes.contains(branch.toUpperCase())) {
            log.debug("Skipping aggregation: branch {} is excluded for BIC {}", branch, receiverBic);
            return false;
        }

        return true;
    }
}
