package com.bank.mt.delivery;

import com.bank.mt.domain.MtStatement;

/**
 * Pluggable delivery adapter.
 * Implementations handle the actual dispatch of processed statements.
 */
public interface DeliveryAdapter {

    void deliver(String destination, MtStatement statement);
}
