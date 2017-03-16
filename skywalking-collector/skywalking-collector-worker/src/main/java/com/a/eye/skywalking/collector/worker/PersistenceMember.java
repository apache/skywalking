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
public abstract class PersistenceMember extends AbstractLocalAsyncWorker {

    private Logger logger = LogManager.getFormatterLogger(PersistenceMember.class);

    public PersistenceMember(Role role, ClusterWorkerContext clusterContext) throws Exception {
        super(role, clusterContext);
    }

    public abstract String esIndex();

    public abstract String esType();

    public abstract void analyse(Object message) throws Exception;

    @Override
    public void preStart() throws Exception {
    }

    @Override
    public void work(Object message) throws Exception {
        if (message instanceof EndOfBatchCommand) {
            persistence();
        } else {
            analyse(message);
        }
    }

    protected abstract void persistence();
}
