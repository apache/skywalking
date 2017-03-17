package com.a.eye.skywalking.api.client;

import com.a.eye.skywalking.api.boot.ServiceManager;
import com.a.eye.skywalking.api.boot.StatusBootService;
import com.a.eye.skywalking.api.queue.TraceSegmentProcessQueue;
import com.a.eye.skywalking.collector.CollectorSystem;
import com.a.eye.skywalking.collector.actor.LookUp;
import com.a.eye.skywalking.collector.actor.WorkerNotFoundException;
import com.a.eye.skywalking.collector.actor.WorkerRefs;
import com.a.eye.skywalking.collector.role.TraceSegmentReceiverRole;
import com.a.eye.skywalking.logging.ILog;
import com.a.eye.skywalking.logging.LogManager;
import com.a.eye.skywalking.trace.TraceSegment;

import java.util.List;

/**
 * @author wusheng
 */
public class CollectorClientService extends StatusBootService implements Runnable {
    private static ILog logger = LogManager.getLogger(CollectorClientService.class);
    private static long SLEEP_TIME_MILLIS = 500;
    private LookUp clusterContext;

    /**
     * Start a new {@link Thread} to get finished {@link TraceSegment} by {@link TraceSegmentProcessQueue#getCachedTraceSegments()}
     */
    @Override
    protected void bootUpWithStatus() throws Exception {
        Thread collectorClientThread = new Thread(this, "collectorClientThread");
        collectorClientThread.start();
        CollectorSystem collectorSystem = new CollectorSystem();
        collectorSystem.boot();
        clusterContext = collectorSystem.getClusterContext();
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
                        try {
                            WorkerRefs workerRefs = clusterContext.lookup(TraceSegmentReceiverRole.INSTANCE);
                            workerRefs.tell(segment);
                        } catch (WorkerNotFoundException t) {
                            logger.error(t, "Role={} not found.", TraceSegmentReceiverRole.INSTANCE.roleName());
                            /**
                             * No receiver found, means collector server is off-line.
                             */
                            sleepTime = SLEEP_TIME_MILLIS * 10;
                            break;
                        }
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
