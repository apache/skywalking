package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;
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
 * @author pengys5
 */
public abstract class AbstractWorker<T> extends UntypedActor {

    final String workerRole;

    public AbstractWorker(String workerRole) {
        this.workerRole = workerRole;
    }

    public abstract void receive(Object message);

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

    public void tell(AbstractWorkerProvider targetWorkerProvider, WorkerSelector selector, T message) throws Throwable {
        List<WorkerRef> availableWorks = WorkersRefCenter.INSTANCE.availableWorks(targetWorkerProvider.roleName());
        selector.select(availableWorks, message).tell(message, getSelf());
    }

    void register(Member member) {
        if (member.getRoles().equals(WorkersListener.WorkName)) {
            WorkerListenerMessage.RegisterMessage registerMessage = new WorkerListenerMessage.RegisterMessage(workerRole);
            getContext().actorSelection(member.address() + "/user/" + WorkersListener.WorkName).tell(registerMessage, getSelf());
        }
    }
}
