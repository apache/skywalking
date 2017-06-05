package org.skywalking.apm.agent.core.client;

import org.skywalking.apm.agent.core.queue.TraceSegmentProcessQueue;
import org.skywalking.apm.agent.core.boot.StatusBootService;
import org.skywalking.apm.trace.TraceSegment;

/**
 * The <code>CollectorClientService</code> is responsible for start {@link CollectorClient}.
 *
 * @author wusheng
 */
public class CollectorClientService extends StatusBootService {
    /**
     * Start a new {@link Thread} to get finished {@link TraceSegment} by {@link TraceSegmentProcessQueue#getCachedTraceSegments()}
     */
    @Override
    protected void bootUpWithStatus() throws Exception {
        Thread collectorClientThread = new Thread(new CollectorClient(), "collectorClientThread");
        collectorClientThread.start();
    }
}
