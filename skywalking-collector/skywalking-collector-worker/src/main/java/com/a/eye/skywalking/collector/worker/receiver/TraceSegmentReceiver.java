package com.a.eye.skywalking.collector.worker.receiver;

import com.a.eye.skywalking.collector.actor.AbstractWorker;
import com.a.eye.skywalking.trace.TraceSegment;

/**
 * @author pengys5
 */
public class TraceSegmentReceiver extends AbstractWorker {

    @Override
    public void receive(Object message) throws Throwable {
        if (message instanceof TraceSegment) {
            
        }
    }
}
