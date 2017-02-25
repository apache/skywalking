package com.a.eye.skywalking.collector.actor.router;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.AbstractWorker;
import java.util.List;

/**
 * The <code>WorkerSelector</code> should be implemented
 * by any class whose instances are intended to provide select a {@link ActorRef} from a {@link ActorRef} list.
 * <p></p>
 * Actually, the <code>ActorRef</code> is designed to provide a routing ability in the collector cluster.
 *
 * @author wusheng
 */
public interface WorkerSelector<T> {
    /**
     * select a {@link ActorRef} from a {@link ActorRef} list.
     *
     * @param members given {@link ActorRef} list, which size is greater than 0;
     * @param message the {@link AbstractWorker} is going to send.
     * @return the selected {@link ActorRef}
     */
    ActorRef select(List<ActorRef> members, T message);
}
