package com.a.eye.skywalking.collector.cluster.producer;

import static com.a.eye.skywalking.collector.cluster.message.TraceMessages.BACKEND_REGISTRATION;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.cluster.Const;
import com.a.eye.skywalking.collector.cluster.manager.ActorCache;
import com.a.eye.skywalking.collector.cluster.message.TraceMessages.JobFailed;
import com.a.eye.skywalking.collector.cluster.message.TraceMessages.TransformationJob;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import org.springframework.context.annotation.Scope;

import java.util.List;

//#frontend
//@Named("TraceProducerActor")
@Scope("prototype")
public class TraceProducerActor extends UntypedActor {

    int jobCounter = 0;

    @Override
    public void onReceive(Object message) {
        List<ActorRef> actorList = ActorCache.roleToActor.get(Const.Trace_Consumer_Role);
        if (actorList == null) {
            System.out.println("actorList null");
        } else {
            System.out.println("size: " + actorList.size());
        }

        if ((message instanceof TransformationJob) && actorList == null) {
            TransformationJob job = (TransformationJob) message;
            getSender().tell(new JobFailed("Service unavailable, try again later", job), getSender());
        } else if (message instanceof TransformationJob) {
            TransformationJob job = (TransformationJob) message;
            jobCounter++;
            actorList.get(jobCounter % actorList.size()).forward(job, getContext());
        } else {
            unhandled(message);
        }
    }

}
//#frontend
