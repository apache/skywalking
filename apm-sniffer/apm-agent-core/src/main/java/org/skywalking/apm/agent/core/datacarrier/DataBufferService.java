package org.skywalking.apm.agent.core.datacarrier;

import java.util.List;
import org.skywalking.apm.agent.core.boot.BootService;
import org.skywalking.apm.agent.core.context.TracingContext;
import org.skywalking.apm.agent.core.context.TracingContextListener;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.datacarrier.buffer.BufferStrategy;
import org.skywalking.apm.agent.core.datacarrier.consumer.IConsumer;

import static org.skywalking.apm.agent.core.conf.Config.Buffer.BUFFER_SIZE;
import static org.skywalking.apm.agent.core.conf.Config.Buffer.CHANNEL_SIZE;

/**
 * @author wusheng
 */
public class DataBufferService implements BootService, IConsumer<TraceSegment>, TracingContextListener {
    private volatile DataCarrier<TraceSegment> carrier;

    @Override
    public void bootUp() throws Throwable {
        carrier = new DataCarrier<TraceSegment>(CHANNEL_SIZE, BUFFER_SIZE);
        carrier.setBufferStrategy(BufferStrategy.IF_POSSIBLE);
        carrier.consume(this, 1);
        TracingContext.ListenerManager.add(this);
    }

    @Override
    public void init() {

    }

    @Override
    public void consume(List<TraceSegment> data) {

    }

    @Override
    public void onError(List<TraceSegment> data, Throwable t) {

    }

    @Override
    public void onExit() {

    }

    @Override
    public void afterFinished(TraceSegment traceSegment) {
        carrier.produce(traceSegment);
    }
}
