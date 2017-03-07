package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.cluster.WorkersRefCenter;
import com.a.eye.skywalking.collector.queue.MessageHolder;
import com.lmax.disruptor.EventHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

/**
 * @author pengys5
 */
public abstract class AbstractMember implements EventHandler<MessageHolder> {

    private Logger logger = LogManager.getFormatterLogger(AbstractMember.class);

    private ActorRef actorRef;

    private ActorRef getSelf() {
        return actorRef;
    }

    public AbstractMember(ActorRef actorRef) {
        this.actorRef = actorRef;
    }

    public abstract void beTold(Object message) throws Exception;

    /**
     * Receive the message to analyse.
     *
     * @param message is the data send from the forward worker
     * @throws Exception is the exception thrown by that worker implementation processing
     */
    public abstract void receive(Object message) throws Exception;

    /**
     * Send analysed data to next Worker.
     *
     * @param targetWorkerProvider is the worker provider to create worker instance.
     * @param selector             is the selector to select a same role worker instance form cluster.
     * @param message              is the data used to send to next worker.
     * @throws Exception
     */
    public void tell(AbstractWorkerProvider targetWorkerProvider, WorkerSelector selector, Object message) throws Exception {
        logger.debug("worker provider: %s ,role name: %s", targetWorkerProvider.getClass().getName(), targetWorkerProvider.roleName());
        List<WorkerRef> availableWorks = WorkersRefCenter.INSTANCE.availableWorks(targetWorkerProvider.roleName());
        selector.select(availableWorks, message).tell(message, getSelf());
    }
}
