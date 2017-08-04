package org.skywalking.apm.collector.stream.worker.impl;

import java.util.ArrayList;
import java.util.List;

/**
 * @author pengys5
 */
public enum ExchangeWorkerContainer {
    INSTANCE;

    private List<ExchangeWorker> exchangeWorkers = new ArrayList<>();

    public void addWorker(ExchangeWorker worker) {
        exchangeWorkers.add(worker);
    }

    public List<ExchangeWorker> getExchangeWorkers() {
        return exchangeWorkers;
    }
}
