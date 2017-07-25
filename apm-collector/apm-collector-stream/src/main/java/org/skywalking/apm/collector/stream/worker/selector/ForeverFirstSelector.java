package org.skywalking.apm.collector.stream.worker.selector;

import java.util.List;
import org.skywalking.apm.collector.stream.worker.WorkerRef;

/**
 * @author pengys5
 */
public class ForeverFirstSelector implements WorkerSelector<WorkerRef> {

    @Override public WorkerRef select(List<WorkerRef> members, Object message) {
        return members.get(0);
    }
}
