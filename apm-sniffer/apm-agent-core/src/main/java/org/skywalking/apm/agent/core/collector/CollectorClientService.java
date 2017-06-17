package org.skywalking.apm.agent.core.collector;

import org.skywalking.apm.agent.core.boot.StatusBootService;
import org.skywalking.apm.agent.core.collector.sender.TraceSegmentSender;
import org.skywalking.apm.agent.core.collector.task.TraceSegmentSendTask;
import org.skywalking.apm.agent.core.queue.TraceSegmentProcessQueue;
import org.skywalking.apm.trace.TraceSegment;

/**
 * The <code>CollectorClientService</code> is responsible for start {@link TraceSegmentSendTask}.
 *
 * @author wusheng
 */
public class CollectorClientService extends StatusBootService {
    /**
     * Start a new {@link Thread} to get finished {@link TraceSegment} by {@link TraceSegmentProcessQueue#getCachedTraceSegments()}
     */
    @Override
    protected void bootUpWithStatus() throws Exception {
        new TraceSegmentSendTask(new TraceSegmentSender()).start();
    }
}
