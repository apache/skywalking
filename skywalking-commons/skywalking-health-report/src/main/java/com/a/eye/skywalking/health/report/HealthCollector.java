package com.a.eye.skywalking.health.report;


import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HealthCollector extends Thread {
    private static       ILog                      logger                  = LogManager.getLogger(HealthCollector.class);
    private static       Map<String, HeathReading> heathReadings           = new ConcurrentHashMap<String, HeathReading>();
    private static final long                      DEFAULT_REPORT_INTERVAL = 60 * 1000;
    private final long   reportInterval;
    private       String reporterName;

    private HealthCollector(String reporterName) {
        this(DEFAULT_REPORT_INTERVAL);
        this.reporterName = reporterName;
    }

    private HealthCollector(long reportInterval) {
        super("HealthCollector");
        this.setDaemon(true);
        this.reportInterval = reportInterval;
    }

    public static void init(String reporterName) {
        new HealthCollector(reporterName).start();
    }

    public static HeathReading getCurrentHeathReading(String extraId) {
        String id = getId(extraId);
        if (!heathReadings.containsKey(id)) {
            synchronized (heathReadings) {
                if (!heathReadings.containsKey(id)) {
                    if (heathReadings.keySet().size() > 5000) {
                        throw new RuntimeException("use HealthCollector illegal. There is an overflow trend of Server Health Collector Report Data.");
                    }
                    heathReadings.put(id, new HeathReading(id));
                }
            }
        }
        return heathReadings.get(id);
    }

    private static String getId(String extraId) {
        return "T:" + Thread.currentThread().getName() + "(" + Thread.currentThread().getId() + ")" + (extraId == null ? "" : ",extra:" + extraId);
    }

    @Override
    public void run() {
        while (true) {
            try {
                Map<String, HeathReading> heathReadingsSnapshot = heathReadings;
                heathReadings = new ConcurrentHashMap<String, HeathReading>();
                String[] keyList = heathReadingsSnapshot.keySet().toArray(new String[0]);
                Arrays.sort(keyList);
                StringBuilder log = new StringBuilder();
                log.append("\n---------" + reporterName + " Health Report---------\n");
                for (String key : keyList) {
                    log.append(heathReadingsSnapshot.get(key)).append("\n");
                }
                log.append("------------------------------------------------\n");

                logger.info(log.toString());

                try {
                    Thread.sleep(reportInterval);
                } catch (InterruptedException e) {
                    logger.warn("sleep error.", e);
                }
            } catch (Throwable t) {
                logger.error("HealthCollector report error.", t);
            }
        }
    }
}
