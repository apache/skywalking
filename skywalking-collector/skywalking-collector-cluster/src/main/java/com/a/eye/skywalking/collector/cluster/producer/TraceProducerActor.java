package com.a.eye.skywalking.collector.cluster.producer;

import static com.a.eye.skywalking.collector.cluster.message.TraceMessages.BACKEND_REGISTRATION;

import java.util.ArrayList;
import java.util.List;

import com.a.eye.skywalking.collector.cluster.message.TraceMessages.JobFailed;
import com.a.eye.skywalking.collector.cluster.message.TraceMessages.TransformationJob;
import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import org.springframework.context.annotation.Scope;

//#frontend
//@Named("TraceProducerActor")
@Scope("prototype")
public class TraceProducerActor extends UntypedActor {

    List<ActorRef> backends = new ArrayList<ActorRef>();
    int jobCounter = 0;

    @Override
    public void onReceive(Object message) {
        if ((message instanceof TransformationJob) && backends.isEmpty()) {
            TransformationJob job = (TransformationJob) message;
            getSender().tell(new JobFailed("Service unavailable, try again later", job), getSender());
        } else if (message instanceof TransformationJob) {
            TransformationJob job = (TransformationJob) message;
            jobCounter++;
            backends.get(jobCounter % backends.size()).forward(job, getContext());
        } else if (message.equals(BACKEND_REGISTRATION)) {
            getContext().watch(getSender());
            backends.add(getSender());
        } else if (message instanceof Terminated) {
            Terminated terminated = (Terminated) message;
            backends.remove(terminated.getActor());
        } else {
            unhandled(message);
        }
    }

}
//#frontend
