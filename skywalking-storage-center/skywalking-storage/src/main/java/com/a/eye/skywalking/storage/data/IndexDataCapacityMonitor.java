package com.a.eye.skywalking.storage.data;

import com.a.eye.skywalking.storage.block.index.BlockIndexEngine;
import com.a.eye.skywalking.storage.data.index.IndexDBConnector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicLong;

import static com.a.eye.skywalking.storage.config.Config.DataIndex.MAX_CAPACITY_PER_INDEX;

/**
 * Created by xin on 2016/11/6.
 */
public class IndexDataCapacityMonitor {

    private static Logger logger = LogManager.getLogger(IndexDataCapacityMonitor.class);

    private static Detector detector;

    public static void addIndexData(long timestamp, int size) {
        if (detector.isDetectFor(timestamp)) {
            detector.add(size);
        }
    }

    private static class Detector {

        private AtomicLong currentSize;
        private long       timestamp;

        public Detector(long timestamp) {
            this.timestamp = timestamp;
            currentSize = new AtomicLong();
        }

        public Detector(long timestamp, long currentSize) {
            this.currentSize = new AtomicLong(currentSize);
            this.timestamp = timestamp;
        }

        public boolean isDetectFor(long timestamp) {
            return this.timestamp == timestamp;
        }

        public void add(int updateRecordSize) {
            if (currentSize.addAndGet(updateRecordSize) > MAX_CAPACITY_PER_INDEX * 0.8) {
                notificationAddNewBlockIndex();
            }
        }
    }

    private static void notificationAddNewBlockIndex() {
        long timestamp = System.currentTimeMillis() + 5 * 60 * 1000;
        BlockIndexEngine.newUpdator().addRecord(timestamp);
        detector = new Detector(timestamp);
    }

    public static void start() {
        long timestamp = BlockIndexEngine.newFinder().findLastBlockIndex();

        //TODO: 为IndexDBConnector增加闭包执行函数
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
        } finally {
            if(dbConnector != null){
                dbConnector.close();
            }
        }
    }
}
