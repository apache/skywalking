package org.skywalking.apm.collector.stream.worker.impl;

import org.skywalking.apm.collector.core.queue.EndOfBatchCommand;
import org.skywalking.apm.collector.stream.worker.AbstractLocalAsyncWorker;
import org.skywalking.apm.collector.stream.worker.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.worker.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.worker.Role;
import org.skywalking.apm.collector.stream.worker.WorkerException;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataCache;

/**
 * @author pengys5
 */
public abstract class AggregationWorker extends AbstractLocalAsyncWorker {

    private DataCache dataCache;

    public AggregationWorker(Role role, ClusterWorkerContext clusterContext) {
        super(role, clusterContext);
        dataCache = new DataCache();
    }

    private int messageNum;

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

    protected abstract void sendToNext();

    protected final void aggregate(Object message) {
        Data data = (Data)message;
        if (dataCache.containsKey(data.id())) {
            getClusterContext().getDataDefine(data.getDefineId()).mergeData(data, dataCache.get(data.id()));
        } else {
            dataCache.put(data.id(), data);
        }
    }
}
