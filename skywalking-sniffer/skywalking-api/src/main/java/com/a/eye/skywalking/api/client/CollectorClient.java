package com.a.eye.skywalking.api.client;

import com.a.eye.skywalking.api.boot.ServiceManager;
import com.a.eye.skywalking.api.queue.TraceSegmentProcessQueue;
import com.a.eye.skywalking.logging.ILog;
import com.a.eye.skywalking.logging.LogManager;
import com.a.eye.skywalking.trace.TraceSegment;
import java.util.List;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClients;

/**
 * The <code>CollectorClient</code> runs as an independency thread.
 * It retrieves cached {@link TraceSegment} from {@link TraceSegmentProcessQueue},
 * and send to collector by HTTP-RESTFUL-SERVICE: POST /skywalking/trace/segment
 *
 * @author wusheng
 */
public class CollectorClient implements Runnable {
    private static ILog logger = LogManager.getLogger(CollectorClient.class);
    private static long SLEEP_TIME_MILLIS = 500;
    private CloseableHttpClient httpclient;

    public CollectorClient() {
        httpclient = HttpClients.custom()
            .setKeepAliveStrategy(new DefaultConnectionKeepAliveStrategy())
            .build();
    }

    @Override
    public void run() {
        while (true) {
            try {
                long sleepTime = -1;
                TraceSegmentProcessQueue segmentProcessQueue = ServiceManager.INSTANCE.findService(TraceSegmentProcessQueue.class);
                List<TraceSegment> cachedTraceSegments = segmentProcessQueue.getCachedTraceSegments();
                if (cachedTraceSegments.size() > 0) {
                    for (TraceSegment segment : cachedTraceSegments) {
                        /**
                         * No receiver found, means collector server is off-line.
                         */
                        sleepTime = SLEEP_TIME_MILLIS * 10;
                        break;
                    }
                } else {
                    sleepTime = SLEEP_TIME_MILLIS;
                }

                if (sleepTime > 0) {
                    try2Sleep(sleepTime);
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
