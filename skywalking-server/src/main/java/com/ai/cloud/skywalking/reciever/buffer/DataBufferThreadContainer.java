package com.ai.cloud.skywalking.reciever.buffer;

import com.ai.cloud.skywalking.reciever.conf.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class DataBufferThreadContainer {

    private static Logger                 logger  = LogManager.getLogger(DataBufferThreadContainer.class);
    private static List<DataBufferThread> buffers = new ArrayList<DataBufferThread>();

    private DataBufferThreadContainer() {
    }

    public static DataBufferThread getDataBufferThread() {
        if (buffers.size() == 0) {
            throw new RuntimeException("Data buffer thread pool is not init");
        }
        return buffers.get(ThreadLocalRandom.current().nextInt(buffers.size()));
    }

    public static void init() {
        for (int i = 0; i < Config.Server.MAX_DEAL_DATA_THREAD_NUMBER; i++) {
            DataBufferThread dataBufferThread = new DataBufferThread(i);
            dataBufferThread.start();
            buffers.add(dataBufferThread);
        }
    }
}
