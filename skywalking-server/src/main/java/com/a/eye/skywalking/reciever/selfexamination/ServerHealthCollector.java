package com.a.eye.skywalking.reciever.selfexamination;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.a.eye.skywalking.reciever.conf.Config;
import com.a.eye.skywalking.reciever.util.MachineUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerHealthCollector extends Thread {
    private Logger logger = LogManager.getLogger(ServerHealthCollector.class);

    private static Map<String, ServerHeathReading> heathReadings = new ConcurrentHashMap<String, ServerHeathReading>();

    private ServerHealthCollector() {
        super("ServerHealthCollector");
        this.setDaemon(true);
    }

    public static void init() {
        new ServerHealthCollector().start();
    }

    public static ServerHeathReading getCurrentHeathReading(String extraId) {
        String id = getId(extraId);
        if (!heathReadings.containsKey(id)) {
            synchronized (heathReadings) {
                if (!heathReadings.containsKey(id)) {
                    if (heathReadings.keySet().size() > 5000) {
                        throw new RuntimeException(
                                "use ServerHealthCollector illegal. There is an overflow trend of Server Health Collector Report Data.");
                    }
                    heathReadings.put(id, new ServerHeathReading(id));
                }
            }
        }
        return heathReadings.get(id);
    }

    private static String getId(String extraId) {
        return "SkyWalkingServer,M:" + MachineUtil.getHostDesc() + ",P:" + MachineUtil.getProcessNo() + ",T:" + Thread
                .currentThread().getName() + "(" + Thread.currentThread().getId() + ")" + (extraId == null ?
                "" :
                ",extra:" + extraId);
    }

    @Override
    public void run() {
        while (true) {
            try {
                Map<String, ServerHeathReading> heathReadingsSnapshot = heathReadings;
                heathReadings = new ConcurrentHashMap<String, ServerHeathReading>();
                String[] keyList = heathReadingsSnapshot.keySet().toArray(new String[0]);
                Arrays.sort(keyList);
                StringBuilder log = new StringBuilder();
                log.append("\n---------Server Health Collector Report---------\n");
                for (String key : keyList) {
                    log.append(heathReadingsSnapshot.get(key)).append("\n");
                }
                log.append("------------------------------------------------\n");

                logger.info(log);

                try {
                    Thread.sleep(Config.HealthCollector.REPORT_INTERVAL);
                } catch (InterruptedException e) {
                    logger.warn("sleep error.", e);
                }
            } catch (Throwable t) {
                logger.error("ServerHealthCollector report error.", t);
            }
        }
    }
}
