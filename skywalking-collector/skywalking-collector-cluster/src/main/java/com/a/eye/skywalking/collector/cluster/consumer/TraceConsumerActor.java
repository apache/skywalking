package com.a.eye.skywalking.collector.cluster.consumer;

import akka.cluster.ClusterEvent;
import com.a.eye.skywalking.collector.cluster.Const;
import com.a.eye.skywalking.collector.cluster.message.ActorRegisteMessage;
import com.a.eye.skywalking.collector.cluster.message.TraceMessages.TransformationJob;
import com.a.eye.skywalking.collector.cluster.message.TraceMessages.TransformationResult;
import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent.CurrentClusterState;
import akka.cluster.ClusterEvent.MemberUp;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import org.springframework.context.annotation.Scope;

//@Named("TraceConsumerActor")
@Scope("prototype")
public class TraceConsumerActor extends UntypedActor {

    Cluster cluster = Cluster.get(getContext().system());

    //subscribe to cluster changes, MemberUp
    @Override
    public void preStart() {
        cluster.subscribe(getSelf(), ClusterEvent.MemberEvent.class);
    }

    //re-subscribe when restart
    @Override
    public void postStop() {
        cluster.unsubscribe(getSelf());
    }

    @Override
    public void onReceive(Object message) {
        if (message instanceof TransformationJob) {
            TransformationJob job = (TransformationJob) message;
            getSender().tell(new TransformationResult(job.getText().toUpperCase()), getSelf());
        } else if (message instanceof CurrentClusterState) {
            CurrentClusterState state = (CurrentClusterState) message;
            for (Member member : state.getMembers()) {
                if (member.status().equals(MemberStatus.up())) {
                    register(member);
                }
            }
        } else if (message instanceof MemberUp) {
            MemberUp mUp = (MemberUp) message;
            register(mUp.member());

        } else {
            unhandled(message);
        }
    }

    void register(Member member) {
        System.out.println("register");
        if (member.hasRole(Const.Trace_Producer_Role)) {
            System.out.println("register: " + Const.Trace_Producer_Role);
            ActorRegisteMessage.RegisteMessage registeMessage = new ActorRegisteMessage.RegisteMessage(Const.Trace_Consumer_Role, "");
            getContext().actorSelection(member.address() + Const.Actor_Manager_Path).tell(registeMessage, getSelf());
        }
    }
}