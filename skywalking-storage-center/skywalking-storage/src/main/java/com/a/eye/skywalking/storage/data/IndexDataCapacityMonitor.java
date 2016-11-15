package com.a.eye.skywalking.storage.data;

import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.storage.block.index.BlockIndexEngine;
import com.a.eye.skywalking.storage.data.index.IndexDBConnector;

import java.sql.SQLException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.a.eye.skywalking.storage.config.Config.DataIndex.SIZE;

/**
 * Created by xin on 2016/11/6.
 */
public class IndexDataCapacityMonitor {

    private static ILog logger = LogManager.getLogger(IndexDataCapacityMonitor.class);
    private static Detector detector;

    public static void addIndexData(long timestamp, int size) {
        if (detector.isDetectFor(timestamp)) {
            detector.add(size);
        }
    }

    private static class Detector extends TimerTask {

        private AtomicLong currentSize;
        private long       timestamp;
        private Timer timer = new Timer();

        public Detector(long timestamp) {
            this.timestamp = timestamp;
            currentSize = new AtomicLong();
            start();
        }

        public Detector(long timestamp, long currentSize) {
            this.currentSize = new AtomicLong(currentSize);
            this.timestamp = timestamp;
            start();
        }

        public void start() {
            timer.schedule(this, 0, TimeUnit.SECONDS.toMillis(30));
        }

        public void stop() {
            timer.cancel();
        }

        public boolean isDetectFor(long timestamp) {
            return this.timestamp == timestamp;
        }

        public void add(int updateRecordSize) {
            currentSize.addAndGet(updateRecordSize);
        }

        @Override
        public void run() {
            if (currentSize.get() > SIZE * 0.8) {
                stop();
                notificationAddNewBlockIndexAndCreateNewIndexDB();
                HealthCollector.getCurrentHeathReading("Index Data Capacity Detector").updateData(HeathReading.INFO,
                        "Detector is detecting the index [%d]. and the capacity of  {}  is %d", timestamp,
                        currentSize.get());
            }
        }
    }

    private static void notificationAddNewBlockIndexAndCreateNewIndexDB() {
        long timestamp = System.currentTimeMillis() + 5 * 60 * 1000;
        BlockIndexEngine.newUpdator().addRecord(timestamp);
        logger.info("Create a new Index DB [{}]", timestamp);
        createNewIndexDB(timestamp);
        detector = new Detector(timestamp);
    }

    private static void createNewIndexDB(long timestamp) {
        IndexDBConnector connector = new IndexDBConnector(timestamp);
        connector.close();
    }

    public static void start() {
        long timestamp = BlockIndexEngine.newFinder().findLastBlockIndex();

        IndexDBConnector dbConnector = null;
        try {
            dbConnector = new IndexDBConnector(timestamp);
            long count = 0;
            try {
                count = dbConnector.fetchIndexSize();
            } catch (SQLException e) {
                logger.error("Failed to to fetch index size from DB:{}", timestamp, e);
            }
            detector = new Detector(timestamp, count);
            logger.info("Index data capacity monitor started successfully!");
        } finally {
            if (dbConnector != null) {
                dbConnector.close();
            }
        }
    }


    public static void stop() {
        detector.stop();
    }
}
