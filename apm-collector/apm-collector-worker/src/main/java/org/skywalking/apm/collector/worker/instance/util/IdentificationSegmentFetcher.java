package org.skywalking.apm.collector.worker.instance.util;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public enum IdentificationSegmentFetcher {
    INSTANCE;

    private ScheduledFuture task;
    private Logger logger = LogManager.getLogger(IdentificationSegmentFetcher.class);
    private ESLock esLock = new ESLock();

    public void fetchSegment(Listener listener) {
        for (int index = 0; index < 3; index++) {
            boolean updateSuccess = esLock.tryLock((start, end) -> {
                listener.success(new IdentificationSegment(start, end));
            });

            if (updateSuccess) {
                return;
            } else {
                try {
                    Thread.sleep(new Random().nextInt(1000));
                } catch (InterruptedException e) {
                }
            }
        }

        listener.failed();
    }

    public void fetchSegmentInBackGround(final IDSequence cache) {
        if (task != null) {
            logger.info("Fetch segment task already running.");
            return;
        }

        task = Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> fetchSegment(new Listener() {
            @Override
            public void failed() {
                logger.warn("Failed to fetch Identification segment.");
            }

            @Override
            public void success(IdentificationSegment segment) {
                cache.state = IDSequence.State.NORMAL;
                cache.segment = segment;
                if (task != null) {
                    task.cancel(false);
                    task = null;
                }
            }
        }), 0, 500, TimeUnit.MILLISECONDS);
    }

    interface Listener {

        void failed();

        void success(IdentificationSegment segment);
    }
}
