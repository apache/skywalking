package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.queue.EndOfBatchCommand;

/**
 * @author pengys5
 */
public abstract class AnalysisMember extends AbstractLocalAsyncWorker {

    AnalysisMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    public abstract void analyse(Object message) throws Exception;

    @Override
    public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override final public void onWork(Object message) throws Exception {
        if (message instanceof EndOfBatchCommand) {
            aggregation();
        } else {
            try {
                analyse(message);
            } catch (Exception e) {
                saveException(e);
            }
        }
    }

    protected abstract void aggregation() throws Exception;
}
