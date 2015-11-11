package com.ai.cloud.skywalking.reciever.buffer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

class AppendEOFFlagThread extends Thread {

    private Logger logger = LogManager.getLogger(AppendEOFFlagThread.class);
    private File[] dataBufferFiles;

    public AppendEOFFlagThread(File[] dataBufferFiles) {
        this.dataBufferFiles = dataBufferFiles;
    }

    @Override
    public void run() {
        BufferedWriter bufferedWriter = null;
        for (File file : dataBufferFiles) {
            try {
                logger.info("Add EOF flags to unprocessed data file[{}]", file.getName());
                bufferedWriter = new BufferedWriter(new FileWriter(new File(file.getParent(), file.getName()), true));
                bufferedWriter.write("EOF\n");
            } catch (IOException e) {
                logger.info("Add EOF flags to the unprocessed data file failed.", e);
            } finally {
                try {
                    bufferedWriter.flush();
                    bufferedWriter.close();
                } catch (IOException e) {

                }
            }
        }
    }
}
