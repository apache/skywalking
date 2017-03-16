package com.a.eye.skywalking.collector.actor;

import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * @author pengys5
 */
public class WorkerRefs<T extends WorkerRef> {

    private static Logger logger = LogManager.getFormatterLogger(WorkerRefs.class);

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
}
