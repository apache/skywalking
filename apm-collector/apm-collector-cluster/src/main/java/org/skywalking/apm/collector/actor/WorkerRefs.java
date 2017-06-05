package org.skywalking.apm.collector.actor;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.selector.WorkerSelector;

import java.util.List;

/**
 * @author pengys5
 */
public class WorkerRefs<T extends WorkerRef> {

    private Logger logger = LogManager.getFormatterLogger(WorkerRefs.class);

    private List<T> workerRefs;
    private WorkerSelector workerSelector;

    protected WorkerRefs(List<T> workerRefs, WorkerSelector workerSelector) {
        this.workerRefs = workerRefs;
        this.workerSelector = workerSelector;
    }

    public void tell(Object message) throws Exception {
        logger.debug("WorkerSelector instance of %s", workerSelector.getClass());
        workerSelector.select(workerRefs, message).tell(message);
    }

    public void ask(Object request, Object response) throws Exception {
        WorkerRef workerRef = workerSelector.select(workerRefs, request);
        if (workerRef instanceof LocalSyncWorkerRef) {
            ((LocalSyncWorkerRef) workerRef).ask(request, response);
        } else {
            throw new IllegalAccessError("only local sync worker can ask");
        }
    }
}
