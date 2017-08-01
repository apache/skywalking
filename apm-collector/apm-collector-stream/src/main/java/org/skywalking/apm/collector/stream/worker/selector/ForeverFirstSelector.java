package org.skywalking.apm.collector.stream.worker.selector;

import java.util.List;
import org.skywalking.apm.collector.stream.worker.WorkerRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ForeverFirstSelector implements WorkerSelector<WorkerRef> {

    private final Logger logger = LoggerFactory.getLogger(ForeverFirstSelector.class);

    @Override public WorkerRef select(List<WorkerRef> members, Object message) {
        logger.debug("member size: {}", members.size());
        return members.get(0);
    }
}
