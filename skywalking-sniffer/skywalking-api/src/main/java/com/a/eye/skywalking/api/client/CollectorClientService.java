package com.a.eye.skywalking.api.client;

import com.a.eye.skywalking.api.boot.StatusBootService;
import com.a.eye.skywalking.api.queue.TraceSegmentProcessQueue;
import com.a.eye.skywalking.trace.TraceSegment;

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
