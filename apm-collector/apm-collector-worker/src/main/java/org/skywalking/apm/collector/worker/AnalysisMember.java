package org.skywalking.apm.collector.worker;

import org.skywalking.apm.collector.actor.AbstractLocalAsyncWorker;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.Role;
import org.skywalking.apm.collector.queue.EndOfBatchCommand;
import org.skywalking.apm.collector.worker.config.CacheSizeConfig;

/**
 * @author pengys5
 */
public abstract class AnalysisMember extends AbstractLocalAsyncWorker {

    AnalysisMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    private int messageNum;

    public abstract void analyse(Object message);

    @Override
    public void preStart() throws ProviderNotFoundException {
        super.preStart();
    }

    @Override final public void onWork(Object message) {
        if (message instanceof EndOfBatchCommand) {
            aggregation();
        } else {
            messageNum++;
            analyse(message);

            if (messageNum >= CacheSizeConfig.Cache.Analysis.SIZE) {
                aggregation();
                messageNum = 0;
            }
        }
    }

    protected abstract void aggregation();
}
