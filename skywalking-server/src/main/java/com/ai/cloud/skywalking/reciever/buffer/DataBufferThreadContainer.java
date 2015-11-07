package com.ai.cloud.skywalking.reciever.buffer;

import com.ai.cloud.skywalking.reciever.conf.Config;
import org.apache.commons.io.comparator.NameFileComparator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.ai.cloud.skywalking.reciever.conf.Config.Buffer.MAX_THREAD_NUMBER;

public class DataBufferThreadContainer {
    private static Logger logger = LogManager.getLogger(DataBufferThreadContainer.class);
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
        logger.info("Add EOF flags to the unprocessed data file last time.");
        File parentDir = new File(Config.Buffer.DATA_BUFFER_FILE_PARENT_DIRECTORY);
        NameFileComparator sizeComparator = new NameFileComparator();
        File[] dataFileList = sizeComparator.sort(parentDir.listFiles());
        BufferedWriter bufferedWriter;
        for (File file : dataFileList) {
            try {
                logger.info("Add EOF flags to unprocessed data file[{}]", file.getName());
                bufferedWriter = new BufferedWriter(new FileWriter(new File(file.getParent(), file.getName()), true));
                bufferedWriter.write("EOF\n");
                bufferedWriter.flush();
                bufferedWriter.close();
            } catch (IOException e) {
                logger.info("Add EOF flags to the unprocessed data file failed.", e);
            }
        }

        logger.info("Data buffer thread size {} begin to init ", MAX_THREAD_NUMBER);
        for (int i = 0; i < MAX_THREAD_NUMBER; i++) {
            DataBufferThread dataBufferThread = new DataBufferThread();
            dataBufferThread.start();
            buffers.add(dataBufferThread);
        }
    }

}
