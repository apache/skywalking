package org.skywalking.apm.collector.stream;

import java.util.List;
import org.skywalking.apm.collector.stream.selector.WorkerSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class WorkerRefs<T extends WorkerRef> {

    private final Logger logger = LoggerFactory.getLogger(WorkerRefs.class);

    private List<T> workerRefs;
    private WorkerSelector workerSelector;

    protected WorkerRefs(List<T> workerRefs, WorkerSelector workerSelector) {
        this.workerRefs = workerRefs;
        this.workerSelector = workerSelector;
    }

    public void tell(Object message) throws WorkerInvokeException {
        logger.debug("WorkerSelector instance of %s", workerSelector.getClass());
        workerSelector.select(workerRefs, message).tell(message);
    }

    public void ask(Object request, Object response) throws WorkerInvokeException {
        WorkerRef workerRef = workerSelector.select(workerRefs, request);
        if (workerRef instanceof LocalSyncWorkerRef) {
            ((LocalSyncWorkerRef)workerRef).ask(request, response);
        } else {
            throw new IllegalAccessError("only local sync worker can ask");
        }
    }
}
