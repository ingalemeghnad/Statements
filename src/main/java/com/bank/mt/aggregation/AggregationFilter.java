package com.bank.mt.aggregation;

import com.bank.mt.domain.AggregationBicFilter;
import com.bank.mt.domain.AggregationFilterType;
import com.bank.mt.domain.MtStatement;
import com.bank.mt.repository.AggregationBicFilterRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Determines whether a parsed message should go through aggregation/marshalling
 * or skip directly to statement routing.
 *
 * Each filter row has an optional message_type (NULL = all types).
 * Aggregation applies only when:
 *   1. The receiver BIC (8-char) matches an ALLOWED_BIC row for this message type
 *   2. The branch code is NOT matched by an EXCLUDED_BRANCH row for this message type
 */
@Component
public class AggregationFilter {

    private static final Logger log = LoggerFactory.getLogger(AggregationFilter.class);

    private final AggregationBicFilterRepository repository;

    private volatile List<AggregationBicFilter> cachedAllowed = new CopyOnWriteArrayList<>();
    private volatile List<AggregationBicFilter> cachedExcluded = new CopyOnWriteArrayList<>();

    public AggregationFilter(AggregationBicFilterRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    public void refreshCache() {
        cachedAllowed = new CopyOnWriteArrayList<>(
                repository.findByFilterTypeAndActiveTrue(AggregationFilterType.ALLOWED_BIC));
        cachedExcluded = new CopyOnWriteArrayList<>(
                repository.findByFilterTypeAndActiveTrue(AggregationFilterType.EXCLUDED_BRANCH));
        log.info("Aggregation filter cache refreshed: {} allowed BIC rules, {} excluded branch rules",
                cachedAllowed.size(), cachedExcluded.size());
    }

    public boolean shouldAggregate(MtStatement statement) {
        if (cachedAllowed.isEmpty()) {
            return true;
        }

        String receiverBic = statement.getReceiverBic();
        String msgType = statement.getMessageType();

        boolean bicAllowed = cachedAllowed.stream().anyMatch(f ->
                f.getBicValue().equalsIgnoreCase(receiverBic) && matchesType(f.getMessageType(), msgType));

        if (!bicAllowed) {
            log.debug("Skipping aggregation: BIC {} / type {} not in allowed list", receiverBic, msgType);
            return false;
        }

        String branch = statement.getReceiverBicBranch();
        if (branch != null && !branch.isBlank()) {
            boolean branchExcluded = cachedExcluded.stream().anyMatch(f ->
                    f.getBicValue().equalsIgnoreCase(branch) && matchesType(f.getMessageType(), msgType));
            if (branchExcluded) {
                log.debug("Skipping aggregation: branch {} excluded for type {}", branch, msgType);
                return false;
            }
        }

        return true;
    }

    private boolean matchesType(String filterType, String actualType) {
        return filterType == null || filterType.isBlank() || "*".equals(filterType)
                || filterType.equalsIgnoreCase(actualType);
    }
}
