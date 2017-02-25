package com.a.eye.skywalking.collector.actor;

import akka.actor.UntypedActor;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import com.a.eye.skywalking.collector.actor.router.WorkerRouter;
import com.a.eye.skywalking.collector.cluster.WorkerListenerMessage;
import com.a.eye.skywalking.collector.cluster.WorkersListener;

/**
 * @author pengys5
 */
public abstract class AbstractWorker extends UntypedActor {

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

    protected void tell(String workerRole, WorkerRouter router, Object message) throws Throwable {
        router.find(workerRole).tell(message, getSelf());
    }

    void register(Member member) {
        if (member.getRoles().equals(WorkersListener.WorkName)) {
            WorkerListenerMessage.RegisterMessage registerMessage = new WorkerListenerMessage.RegisterMessage(workerRole);
            getContext().actorSelection(member.address() + "/user/" + WorkersListener.WorkName).tell(registerMessage, getSelf());
        }
    }
}
