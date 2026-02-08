package com.bank.mt.delivery;

import com.bank.mt.domain.MtStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Stub MQ delivery adapter — placeholder for real MQ integration.
 * Activate by setting mt.delivery.mode=MQ.
 */
@Component
@ConditionalOnProperty(name = "mt.delivery.mode", havingValue = "MQ")
public class MqDeliveryAdapter implements DeliveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(MqDeliveryAdapter.class);

    @Override
    public void deliver(String destination, MtStatement statement) {
        log.info("MQ DELIVERY STUB → dest={} ref={} (not implemented)",
                destination, statement.getTransactionReference());
        throw new UnsupportedOperationException("MQ delivery not implemented in POC");
    }
}
