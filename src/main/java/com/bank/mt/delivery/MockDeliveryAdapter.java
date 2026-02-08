package com.bank.mt.delivery;

import com.bank.mt.domain.DeliveryRecord;
import com.bank.mt.domain.MtStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mock delivery adapter for POC — stores all deliveries in memory.
 * Deliveries can be inspected via GET /test/deliveries.
 */
@Component
@ConditionalOnProperty(name = "mt.delivery.mode", havingValue = "MOCK", matchIfMissing = true)
public class MockDeliveryAdapter implements DeliveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(MockDeliveryAdapter.class);

    private final List<DeliveryRecord> deliveries = new CopyOnWriteArrayList<>();

    @Override
    public void deliver(String destination, MtStatement statement) {
        DeliveryRecord record = new DeliveryRecord(destination, statement);
        deliveries.add(record);
        log.info("MOCK DELIVERY → dest={} type={} ref={} acct={}",
                destination, statement.getMessageType(),
                statement.getTransactionReference(), statement.getAccountNumber());
    }

    public List<DeliveryRecord> getDeliveries() {
        return List.copyOf(deliveries);
    }

    public void clear() {
        deliveries.clear();
    }
}
