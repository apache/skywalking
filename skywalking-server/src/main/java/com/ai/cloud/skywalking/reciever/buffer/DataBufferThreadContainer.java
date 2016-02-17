package com.ai.cloud.skywalking.reciever.buffer;

import static com.ai.cloud.skywalking.reciever.conf.Config.Persistence.MAX_APPEND_EOF_FLAGS_THREAD_NUMBER;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.apache.commons.io.comparator.NameFileComparator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.skywalking.reciever.conf.Config;

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
        // 判断数据缓存文件是否存在，如果不存在，则创建
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }
        NameFileComparator sizeComparator = new NameFileComparator();
        File[] dataFileList = sizeComparator.sort(parentDir.listFiles());
        logger.info("Pending file number :" + dataFileList.length);
        if (dataFileList.length > 0) {
            int step = (int) Math.ceil(dataFileList.length * 1.0 / MAX_APPEND_EOF_FLAGS_THREAD_NUMBER);

            int start = 0, end = 0;
            while (true) {
                if (end + step >= dataFileList.length) {
                    new AppendEOFFlagThread(Arrays.copyOfRange(dataFileList, start, dataFileList.length)).start();
                    break;
                }
                end += step;
                new AppendEOFFlagThread(Arrays.copyOfRange(dataFileList, start, end)).start();
                start = end;
                logger.debug("start:" + start + "\tend:" + end);
            }
        }
        logger.info("Data buffer thread size {} begin to init ", Config.Server.
                MAX_DEAL_DATA_THREAD_NUMBER);
        for (int i = 0; i < Config.Server.MAX_DEAL_DATA_THREAD_NUMBER; i++) {
            DataBufferThread dataBufferThread = new DataBufferThread(i);
            dataBufferThread.start();
            buffers.add(dataBufferThread);
        }
    }
}
