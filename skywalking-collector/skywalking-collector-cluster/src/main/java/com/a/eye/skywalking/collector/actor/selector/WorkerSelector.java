package com.a.eye.skywalking.collector.actor.selector;

import com.a.eye.skywalking.collector.actor.AbstractWorker;
import com.a.eye.skywalking.collector.actor.WorkerRef;

import java.util.List;

/**
 * The <code>WorkerSelector</code> should be implemented by any class whose instances
 * are intended to provide select a {@link WorkerRef} from a {@link WorkerRef} list.
 * <p>
 * Actually, the <code>WorkerRef</code> is designed to provide a routing ability in the collector cluster
 *
 * @author pengys5
 */
public interface WorkerSelector<T extends WorkerRef> {

    /**
     * select a {@link WorkerRef} from a {@link WorkerRef} list.
     *
     * @param members given {@link WorkerRef} list, which size is greater than 0;
     * @param message the {@link AbstractWorker} is going to send.
     * @return the selected {@link WorkerRef}
     */
    T select(List<T> members, Object message);
}
