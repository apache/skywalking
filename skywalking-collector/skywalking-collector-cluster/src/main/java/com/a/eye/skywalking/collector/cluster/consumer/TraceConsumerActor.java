package com.a.eye.skywalking.collector.cluster.consumer;

import static com.a.eye.skywalking.collector.cluster.message.TraceMessages.BACKEND_REGISTRATION;

import com.a.eye.skywalking.collector.cluster.message.TraceMessages.TransformationJob;
import com.a.eye.skywalking.collector.cluster.message.TraceMessages.TransformationResult;
import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent.CurrentClusterState;
import akka.cluster.ClusterEvent.MemberUp;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import org.springframework.context.annotation.Scope;

//#backend
//@Named("TraceConsumerActor")
@Scope("prototype")
public class TraceConsumerActor extends UntypedActor {

    Cluster cluster = Cluster.get(getContext().system());

    //subscribe to cluster changes, MemberUp
    @Override
    public void preStart() {
        cluster.subscribe(getSelf(), MemberUp.class);
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
            getSender().tell(new TransformationResult(job.getText().toUpperCase()),
                    getSelf());

        } else if (message instanceof CurrentClusterState) {
            System.out.print("##################################");
            CurrentClusterState state = (CurrentClusterState) message;
            for (Member member : state.getMembers()) {
                System.out.printf("###: " + member.status().toString());
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
        if (member.hasRole("frontend"))
            getContext().actorSelection(member.address() + "/user/frontend").tell(
                    BACKEND_REGISTRATION, getSelf());
    }
}
//#backend
