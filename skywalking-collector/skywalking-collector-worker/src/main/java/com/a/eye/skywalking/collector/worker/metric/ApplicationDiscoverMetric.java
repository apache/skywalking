package com.a.eye.skywalking.collector.worker.metric;


import com.a.eye.skywalking.collector.actor.AbstractWorker;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.persistence.ApplicationMessage;
import com.a.eye.skywalking.collector.worker.persistence.ApplicationPersistenceFactory;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.tag.Tags;

/**
 * @author pengys5
 */
public class ApplicationDiscoverMetric extends AbstractWorker {

    @Override
    public void receive(Object message) throws Throwable {
        if (message instanceof TraceSegment) {
            TraceSegment traceSegment = (TraceSegment) message;
            String code = traceSegment.getApplicationCode();
            String component = Tags.COMPONENT.get(traceSegment.getSpans().get(0));
            String host = Tags.PEER_HOST.get(traceSegment.getSpans().get(0));
            int port = Tags.PEER_PORT.get(traceSegment.getSpans().get(0));
            String layer = Tags.SPAN_LAYER.get(traceSegment.getSpans().get(0));

            ApplicationMessage applicationMessage = new ApplicationMessage(code, component, host, layer);
            tell(new ApplicationPersistenceFactory(), RollingSelector.INSTANCE, applicationMessage);
        }
    }

}
