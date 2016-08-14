package com.a.eye.skywalking.reciever.buffer;

import com.a.eye.skywalking.protocol.BufferFileEOFProtocol;
import com.a.eye.skywalking.protocol.TransportPackager;
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
                fileOutputStream.write(BufferDataAssist
                        .appendLengthAndSplit(TransportPackager.serialize(new BufferFileEOFProtocol())));
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
