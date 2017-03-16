package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.actor.AbstractLocalAsyncWorker;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.Role;
import com.a.eye.skywalking.collector.queue.EndOfBatchCommand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public abstract class AnalysisMember extends AbstractLocalAsyncWorker {

    private Logger logger = LogManager.getFormatterLogger(AnalysisMember.class);

    public AnalysisMember(Role role, ClusterWorkerContext clusterContext) throws Exception {
        super(role, clusterContext);
    }

    public abstract void analyse(Object message) throws Exception;

    @Override
    public void preStart() throws Exception {
    }

    @Override
    public void work(Object message) throws Exception {
        if (message instanceof EndOfBatchCommand) {
            aggregation();
        } else {
            analyse(message);
        }
    }

    protected abstract void aggregation() throws Exception;
}
