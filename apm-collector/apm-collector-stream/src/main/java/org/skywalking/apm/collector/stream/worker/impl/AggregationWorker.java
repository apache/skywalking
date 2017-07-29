package org.skywalking.apm.collector.stream.worker.impl;

import org.skywalking.apm.collector.core.queue.EndOfBatchCommand;
import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorker;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.Role;
import org.skywalking.apm.collector.stream.worker.WorkerException;
import org.skywalking.apm.collector.stream.worker.WorkerInvokeException;
import org.skywalking.apm.collector.stream.worker.WorkerNotFoundException;
import org.skywalking.apm.collector.stream.worker.WorkerRefs;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public abstract class AggregationWorker extends AbstractLocalAsyncWorker {

    private final Logger logger = LoggerFactory.getLogger(AggregationWorker.class);

    private DataCache dataCache;
    private int messageNum;

    public AggregationWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
        dataCache = new DataCache();
    }

    @Override public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override protected final void onWork(Object message) throws WorkerException {
        if (message instanceof EndOfBatchCommand) {
            sendToNext();
        } else {
            messageNum++;
            aggregate(message);

            if (messageNum >= 100) {
                sendToNext();
                messageNum = 0;
            }
        }
    }

    protected abstract WorkerRefs nextWorkRef(String id) throws WorkerNotFoundException;

    private void sendToNext() throws WorkerException {
        dataCache.switchPointer();
        while (dataCache.getLast().isHolding()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                throw new WorkerException(e.getMessage(), e);
            }
        }
        dataCache.getLast().asMap().forEach((id, data) -> {
            try {
                nextWorkRef(id).tell(data);
            } catch (WorkerNotFoundException | WorkerInvokeException e) {
                logger.error(e.getMessage(), e);
            }
        });
    }

    protected final void aggregate(Object message) {
        Data data = (Data)message;
        dataCache.hold();
        if (dataCache.containsKey(data.id())) {
            getClusterContext().getDataDefine(data.getDefineId()).mergeData(data, dataCache.get(data.id()));
        } else {
            dataCache.put(data.id(), data);
        }
        dataCache.release();
    }
}
