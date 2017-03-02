package com.a.eye.skywalking.collector.actor;

import akka.actor.UntypedActor;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import com.a.eye.skywalking.collector.actor.selector.WorkerSelector;
import com.a.eye.skywalking.collector.cluster.WorkerListenerMessage;
import com.a.eye.skywalking.collector.cluster.WorkersListener;
import com.a.eye.skywalking.collector.cluster.WorkersRefCenter;

import java.util.List;

/**
 * Abstract implementation of the {@link akka.actor.UntypedActor} that represents an
 * analysis unit. <code>AbstractWorker</code> implementation process the message in
 * {@link #receive(Object)} method.
 * <p>
 * <p>
 * Subclasses must implement the abstract {@link #receive(Object)} method to process message.
 * Subclasses forbid to override the {@link #onReceive(Object)} method.
 * <p>
 * Here is an example on how to create and use an {@link AbstractWorker}:
 * <p>
 * {{{
 * public class SampleWorker extends AbstractWorker {
 *
 * @author pengys5
 * @Override public void receive(Object message) throws Throwable {
 * if (message.equals("Tell Next")) {
 * Object sendMessage = new Object();
 * tell(new NextSampleWorkerFactory(), RollingSelector.INSTANCE, sendMessage);
 * }
 * }
 * }
 * }}}
 */
public abstract class AbstractWorker<T> extends UntypedActor implements Worker{

    /**
     * Receive the message to analyse.
     *
     * @param message is the data send from the forward worker
     * @throws Throwable is the exception thrown by that worker implementation processing
     */
    public abstract void receive(Object message) throws Throwable;

    /**
     * Listening {@link ClusterEvent.MemberUp} and {@link ClusterEvent.CurrentClusterState}
     * cluster event, when event send from the member of {@link WorkersListener} then tell
     * the sender to register self.
     */
    @Override
    public void onReceive(Object message) throws Throwable {
        if (message instanceof ClusterEvent.CurrentClusterState) {
            ClusterEvent.CurrentClusterState state = (ClusterEvent.CurrentClusterState) message;
            for (Member member : state.getMembers()) {
                if (member.status().equals(MemberStatus.up())) {
                    register(member);
                }
            }
        } else if (message instanceof ClusterEvent.MemberUp) {
            ClusterEvent.MemberUp memberUp = (ClusterEvent.MemberUp) message;
            register(memberUp.member());
        } else {
            receive(message);
        }
    }

    /**
     * Send analysed data to next Worker.
     *
     * @param targetWorkerProvider is the worker provider to create worker instance.
     * @param selector             is the selector to select a same role worker instance form cluster.
     * @param message              is the data used to send to next worker.
     * @throws Throwable
     */
    public void tell(AbstractWorkerProvider targetWorkerProvider, WorkerSelector selector, T message) throws Throwable {
        if (targetWorkerProvider instanceof AbstractLocalWorkerProvider) {
            Worker worker = LocalSystem.actorFor(targetWorkerProvider.getClass(), targetWorkerProvider.roleName());
            worker.receive(message);
        } else if (targetWorkerProvider instanceof AbstractClusterWorkerProvider) {
            List<WorkerRef> availableWorks = WorkersRefCenter.INSTANCE.availableWorks(targetWorkerProvider.roleName());
            selector.select(availableWorks, message).tell(message, getSelf());
        }
    }

    /**
     * When member role is {@link WorkersListener#WorkName} then Select actor from context
     * and send register message to {@link WorkersListener}
     *
     * @param member is the new created or restart worker
     */
    void register(Member member) {
        if (member.getRoles().equals(WorkersListener.WorkName)) {
            WorkerListenerMessage.RegisterMessage registerMessage = new WorkerListenerMessage.RegisterMessage(getClass().getSimpleName());
            getContext().actorSelection(member.address() + "/user/" + WorkersListener.WorkName).tell(registerMessage, getSelf());
        }
    }
}
