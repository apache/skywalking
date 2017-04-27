package org.skywalking.apm.api.client;

import org.skywalking.apm.api.boot.StatusBootService;
import org.skywalking.apm.api.queue.TraceSegmentProcessQueue;
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
