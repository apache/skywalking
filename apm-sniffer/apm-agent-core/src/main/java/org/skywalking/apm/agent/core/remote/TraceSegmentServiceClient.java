package org.skywalking.apm.agent.core.remote;

import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import java.util.List;
import org.skywalking.apm.agent.core.boot.BootService;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.context.TracingContext;
import org.skywalking.apm.agent.core.context.TracingContextListener;
import org.skywalking.apm.agent.core.context.trace.TraceSegment;
import org.skywalking.apm.agent.core.datacarrier.DataCarrier;
import org.skywalking.apm.agent.core.datacarrier.buffer.BufferStrategy;
import org.skywalking.apm.agent.core.datacarrier.consumer.IConsumer;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;
import org.skywalking.apm.network.collecor.proto.Downstream;
import org.skywalking.apm.network.trace.proto.TraceSegmentServiceGrpc;
import org.skywalking.apm.network.trace.proto.UpstreamSegment;

import static org.skywalking.apm.agent.core.conf.Config.Buffer.BUFFER_SIZE;
import static org.skywalking.apm.agent.core.conf.Config.Buffer.CHANNEL_SIZE;
import static org.skywalking.apm.agent.core.remote.GRPCChannelStatus.CONNECTED;

/**
 * @author wusheng
 */
public class TraceSegmentServiceClient implements BootService, IConsumer<TraceSegment>, TracingContextListener, GRPCChannelListener {
    private static final ILog logger = LogManager.getLogger(TraceSegmentServiceClient.class);

    private volatile DataCarrier<TraceSegment> carrier;
    private volatile TraceSegmentServiceGrpc.TraceSegmentServiceStub serviceStub;

    @Override
    public void beforeBoot() throws Throwable {
        ServiceManager.INSTANCE.findService(GRPCChannelManager.class).addChannelListener(this);
    }

    @Override
    public void boot() throws Throwable {
        carrier = new DataCarrier<TraceSegment>(CHANNEL_SIZE, BUFFER_SIZE);
        carrier.setBufferStrategy(BufferStrategy.IF_POSSIBLE);
        carrier.consume(this, 1);
    }

    @Override
    public void afterBoot() throws Throwable {
        TracingContext.ListenerManager.add(this);
    }

    @Override
    public void init() {

    }

    @Override
    public void consume(List<TraceSegment> data) {
        final GRPCStreamServiceStatus status = new GRPCStreamServiceStatus(false);
        StreamObserver<UpstreamSegment> upstreamSegmentStreamObserver = serviceStub.collect(new StreamObserver<Downstream>() {
            @Override
            public void onNext(Downstream downstream) {

            }

            @Override
            public void onError(Throwable throwable) {
                status.setStatus(true);
            }

            @Override
            public void onCompleted() {
                status.setStatus(true);
            }
        });

        try {
            for (TraceSegment segment : data) {
                //TODO
                // segment to PROTOBUF object
                upstreamSegmentStreamObserver.onNext(null);
            }
        } catch (Throwable t) {
            logger.error(t, "Send UpstreamSegment to collector fail.");
        }
        upstreamSegmentStreamObserver.onCompleted();

        status.wait4Finish(30 * 1000);

        if (logger.isDebugEnable()) {
            logger.debug("{} trace segments have been sent to collector.", data.size());
        }
    }

    @Override
    public void onError(List<TraceSegment> data, Throwable t) {
        logger.error(t, "Try to send {} trace segments to collector, with unexpected exception.", data.size());
    }

    @Override
    public void onExit() {

    }

    @Override
    public void afterFinished(TraceSegment traceSegment) {
        carrier.produce(traceSegment);
    }

    @Override
    public void statusChanged(GRPCChannelStatus status) {
        if (CONNECTED.equals(status)) {
            ManagedChannel channel = ServiceManager.INSTANCE.findService(GRPCChannelManager.class).getManagedChannel();
            serviceStub = TraceSegmentServiceGrpc.newStub(channel);
        }
    }
}
