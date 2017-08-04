package org.skywalking.apm.collector.stream.worker.impl;

import org.skywalking.apm.collector.core.queue.EndOfBatchCommand;
import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorker;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.Role;
import org.skywalking.apm.collector.stream.worker.WorkerException;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public abstract class ExchangeWorker extends AbstractLocalAsyncWorker {

    private final Logger logger = LoggerFactory.getLogger(ExchangeWorker.class);

    private DataCache dataCache;

    public ExchangeWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
        dataCache = new DataCache();
    }

    @Override public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override protected final void onWork(Object message) throws WorkerException {
        if (message instanceof FlushAndSwitch) {
            if (dataCache.trySwitchPointer()) {
                dataCache.switchPointer();
            }
        } else if (message instanceof EndOfBatchCommand) {
        } else {
            if (dataCache.currentCollectionSize() <= 1000) {
                aggregate(message);
            }
        }
    }

    protected abstract void exchange(Data data);

    public final void exchangeLastData() {
        try {
            while (dataCache.getLast().isHolding()) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    logger.warn("thread wake up");
                }
            }
            dataCache.getLast().asMap().values().forEach(data -> {
                exchange(data);
            });
        } finally {
            dataCache.releaseLast();
        }
    }

    protected final void aggregate(Object message) {
        Data data = (Data)message;
        dataCache.hold();
        if (dataCache.containsKey(data.id())) {
            getRole().dataDefine().mergeData(data, dataCache.get(data.id()));
        } else {
            dataCache.put(data.id(), data);
        }
        dataCache.release();
    }
}
