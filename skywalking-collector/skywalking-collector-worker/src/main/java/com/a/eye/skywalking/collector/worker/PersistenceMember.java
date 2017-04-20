package com.a.eye.skywalking.collector.worker;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.queue.EndOfBatchCommand;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public abstract class PersistenceMember extends AbstractLocalAsyncWorker {

    private Logger logger = LogManager.getFormatterLogger(PersistenceMember.class);

    public PersistenceMember(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    public abstract String esIndex();

    public abstract String esType();

    public abstract void analyse(Object message) throws Exception;

    @Override final public void preStart() throws ProviderNotFoundException {

    }

    @Override final protected void onWork(Object message) throws Exception {
        if (message instanceof EndOfBatchCommand) {
            persistence();
        } else {
            analyse(message);
        }
    }

    protected abstract void persistence();
}
