package com.ai.cloud.skywalking.reciever.buffer;

import com.ai.cloud.skywalking.reciever.model.BufferDataPackagerGenerator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

class AppendEOFFlagThread extends Thread {
    private Logger logger = LogManager.getLogger(AppendEOFFlagThread.class);
    private File[]         dataBufferFiles;
    private CountDownLatch countDownLatch;

    public AppendEOFFlagThread(File[] dataBufferFiles, CountDownLatch countDownLatch) {
        super("AppendEOFFlagThread");
        this.dataBufferFiles = dataBufferFiles;
        this.countDownLatch = countDownLatch;
    }

    @Override
    public void run() {
        FileOutputStream fileOutputStream = null;
        for (File file : dataBufferFiles) {
            try {
                logger.info("Add EOF flags to unprocessed data file[{}]", file.getName());
                fileOutputStream = new FileOutputStream(new File(file.getParent(), file.getName()), true);
                fileOutputStream.write(BufferDataPackagerGenerator.generateEOFPackage());
            } catch (IOException e) {
                logger.info("Add EOF flags to the unprocessed data file failed.", e);
            } finally {
                try {
                    fileOutputStream.flush();
                    fileOutputStream.close();
                } catch (IOException e) {
                    logger.error("Flush data file failed", e);
                }
            }
            countDownLatch.countDown();
        }
    }
}
