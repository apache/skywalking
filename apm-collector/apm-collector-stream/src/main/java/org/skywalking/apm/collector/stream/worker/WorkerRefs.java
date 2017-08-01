package org.skywalking.apm.collector.stream.worker;

import java.util.List;
import org.skywalking.apm.collector.stream.worker.selector.WorkerSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class WorkerRefs<T extends WorkerRef> {

    private final Logger logger = LoggerFactory.getLogger(WorkerRefs.class);

    private List<T> workerRefs;
    private WorkerSelector workerSelector;
    private Role role;

    protected WorkerRefs(List<T> workerRefs, WorkerSelector workerSelector) {
        this.workerRefs = workerRefs;
        this.workerSelector = workerSelector;
    }

    protected WorkerRefs(List<T> workerRefs, WorkerSelector workerSelector, Role role) {
        this.workerRefs = workerRefs;
        this.workerSelector = workerSelector;
        this.role = role;
    }

    public void tell(Object message) throws WorkerInvokeException {
        logger.debug("WorkerSelector instance of {}", workerSelector.getClass());
        workerSelector.select(workerRefs, message).tell(message);
    }
}
