package com.a.eye.skywalking.api.client;

import com.a.eye.skywalking.api.boot.BootService;
import com.a.eye.skywalking.api.boot.ServiceManager;
import com.a.eye.skywalking.api.queue.TraceSegmentProcessQueue;
import com.a.eye.skywalking.logging.ILog;
import com.a.eye.skywalking.logging.LogManager;
import com.a.eye.skywalking.trace.TraceSegment;
import java.util.List;

/**
 * @author wusheng
 */
public class CollectorClientService implements Runnable, BootService {
    private static ILog logger = LogManager.getLogger(CollectorClientService.class);
    private static long NO_DATA_SLEEP_TIME_MILLIS = 500;

    /**
     * Start a new {@link Thread} to get finished {@link TraceSegment} by {@link TraceSegmentProcessQueue#getCachedTraceSegments()}
     */
    @Override
    public void bootUp() {
        Thread collectorClientThread = new Thread(this, "collectorClientThread");
        collectorClientThread.start();
    }

    @Override
    public void run() {
        while (true) {
            try {
                TraceSegmentProcessQueue segmentProcessQueue = ServiceManager.INSTANCE.findService(TraceSegmentProcessQueue.class);
                List<TraceSegment> cachedTraceSegments = segmentProcessQueue.getCachedTraceSegments();
                if (cachedTraceSegments.size() > 0) {
                    for (TraceSegment segment : cachedTraceSegments) {
                        //TODO: wusheng
                        //send data
                    }
                    /**
                     * No sleep, when exist finished {@link TraceSegment}.
                     */
                } else {
                    try2Sleep(NO_DATA_SLEEP_TIME_MILLIS);
                }
            } catch (Throwable t) {
                logger.error(t, "Send trace segments to collector failure.");
            }
        }
    }

    /**
     * Try to sleep, and ignore the {@link InterruptedException}
     *
     * @param millis the length of time to sleep in milliseconds
     */
    private void try2Sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {

        }
    }
}
