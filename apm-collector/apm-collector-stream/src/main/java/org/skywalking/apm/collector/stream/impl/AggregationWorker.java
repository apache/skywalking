package org.skywalking.apm.collector.stream.impl;

import org.skywalking.apm.collector.core.queue.EndOfBatchCommand;
import org.skywalking.apm.collector.stream.AbstractLocalAsyncWorker;
import org.skywalking.apm.collector.stream.ClusterWorkerContext;
import org.skywalking.apm.collector.stream.LocalWorkerContext;
import org.skywalking.apm.collector.stream.ProviderNotFoundException;
import org.skywalking.apm.collector.stream.Role;
import org.skywalking.apm.collector.stream.WorkerException;
import org.skywalking.apm.collector.stream.impl.data.Data;
import org.skywalking.apm.collector.stream.impl.data.DataCache;

/**
 * @author pengys5
 */
public abstract class AggregationWorker extends AbstractLocalAsyncWorker {

    private DataCache dataCache;

    public AggregationWorker(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
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
