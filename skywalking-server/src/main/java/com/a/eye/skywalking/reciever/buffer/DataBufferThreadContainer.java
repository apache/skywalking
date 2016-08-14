package com.a.eye.skywalking.reciever.buffer;

import com.a.eye.skywalking.reciever.conf.Config;
import org.apache.commons.io.comparator.NameFileComparator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

import static com.a.eye.skywalking.reciever.conf.Config.Persistence.MAX_APPEND_EOF_FLAGS_THREAD_NUMBER;

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

    public static void init() throws InterruptedException {
        logger.info("Add EOF flags to the unprocessed data file last time.");
        File parentDir = new File(Config.Buffer.DATA_BUFFER_FILE_PARENT_DIR);
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
            CountDownLatch countDownLatch = new CountDownLatch(MAX_APPEND_EOF_FLAGS_THREAD_NUMBER);
            while (true) {
                if (end + step >= dataFileList.length) {
                    new AppendEOFFlagThread(Arrays.copyOfRange(dataFileList, start, dataFileList.length),
                            countDownLatch).start();
                    break;
                }
                end += step;
                new AppendEOFFlagThread(Arrays.copyOfRange(dataFileList, start, end), countDownLatch).start();
                start = end;
                logger.debug("start:" + start + "\tend:" + end);
            }

            countDownLatch.await();
        }
        logger.info("Data buffer thread size {} begin to init ", Config.Buffer.BUFFER_DEAL_THREAD_NUMBER);

        for (int i = 0; i < Config.Buffer.BUFFER_DEAL_THREAD_NUMBER; i++) {
            DataBufferThread dataBufferThread = new DataBufferThread(i);
            dataBufferThread.start();
            buffers.add(dataBufferThread);
        }
    }
}
