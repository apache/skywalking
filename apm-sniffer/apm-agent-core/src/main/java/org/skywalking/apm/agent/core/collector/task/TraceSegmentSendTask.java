package org.skywalking.apm.agent.core.collector.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.skywalking.apm.agent.core.boot.ServiceManager;
import org.skywalking.apm.agent.core.collector.Sender;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.agent.core.queue.TraceSegmentProcessQueue;
import org.skywalking.apm.trace.SegmentsMessage;
import org.skywalking.apm.trace.TraceSegment;

public class TraceSegmentSendTask extends SendAsSoonAsPossibleTask<SegmentsMessage> {

    private List<SegmentsMessage> sendData = new ArrayList<SegmentsMessage>();

    public TraceSegmentSendTask(Sender<SegmentsMessage> sender) {
        super(sender);
    }

    @Override
    public boolean needSleep() {
        boolean needSleep = sendData.size() == 0;
        sendData.clear();
        return needSleep;
    }

    @Override
    protected Collection<SegmentsMessage> sendData() {
        TraceSegmentProcessQueue segmentProcessQueue = ServiceManager.INSTANCE.findService(TraceSegmentProcessQueue.class);
        List<TraceSegment> cachedTraceSegments = segmentProcessQueue.getCachedTraceSegments();
        if (cachedTraceSegments.size() > 0 && instanceRegistrySuccess()) {
            SegmentsMessage message = null;
            int count = 0;
            for (TraceSegment segment : cachedTraceSegments) {
                if (message == null) {
                    sendData.add(message);
                    message = new SegmentsMessage();
                }
                segment.setInstanceId(Config.Agent.INSTANCE_ID);
                message.append(segment);
                if (count == Config.Collector.BATCH_SIZE) {
                    message = null;
                }
            }
        }

        return sendData;
    }

    private boolean instanceRegistrySuccess() {
        return Config.Agent.INSTANCE_ID >= 0;
    }
}
