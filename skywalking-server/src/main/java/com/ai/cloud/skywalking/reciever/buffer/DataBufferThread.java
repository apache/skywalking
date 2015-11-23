package com.ai.cloud.skywalking.reciever.buffer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.skywalking.reciever.selfexamination.ServerHealthCollector;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHeathReading;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ai.cloud.skywalking.reciever.conf.Config.Buffer.*;

public class DataBufferThread extends Thread {

    private Logger logger = LogManager.getLogger(DataBufferThread.class);
    private byte[][] data = new byte[PER_THREAD_MAX_BUFFER_NUMBER][];
    private File file;
    private FileOutputStream outputStream;
    private AtomicInteger index = new AtomicInteger();

    public DataBufferThread() {
    	super("DataBufferThread");
        try {
            file = new File(DATA_BUFFER_FILE_PARENT_DIRECTORY, getFileName());
            if (file.exists()) {
                file.createNewFile();
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Create buffer data file {}.", file.getName());
            }
            outputStream = new FileOutputStream(file, true);
        } catch (FileNotFoundException e) {
            logger.error("Data cache file cannot be created or written, please check the file system.", e);
            System.exit(-1);
        } catch (IOException e) {
            logger.error("Data cache file cannot be created or written, please check the file system.", e);
            System.exit(-1);
        }
    }

    @Override
    public void run() {
        boolean isWriteFailure;
        int index = 0;
        while (true) {
            boolean hasData2Flush = false;
            for (int i = 0; i < data.length; i++) {
                if (data[i] == null) {
                    continue;
                }
                hasData2Flush = true;
                isWriteFailure = true;
                while (isWriteFailure) {
                    try {
                        outputStream.write(data[i]);
                        outputStream.write("\n".getBytes());
                        isWriteFailure = false;
                    } catch (IOException e) {
                        logger.error("Write buffer data failed.", e);
                        try {
                            Thread.sleep(WRITE_DATA_FAILURE_RETRY_INTERVAL);
                        } catch (InterruptedException e1) {
                            logger.error("Failure sleep.", e);
                        }
                    }
                }
                if (index++ > FLUSH_NUMBER_OF_CACHE) {
                    try {
                        outputStream.flush();
                        ServerHealthCollector.getCurrentHeathReading(null).updateData(ServerHeathReading.INFO, "DataBuffer flush data to local file:" + file.getName());
                    } catch (IOException e) {
                        logger.error("Flush buffer data failed.", e);
                    } finally {
                        index = 0;
                    }
                }
                data[i] = null;

            }

            if (hasData2Flush){
                try {
                    outputStream.flush();
                    ServerHealthCollector.getCurrentHeathReading(null).updateData(ServerHeathReading.INFO, "DataBuffer flush data to local file:" + file.getName());
                } catch (IOException e) {
                    logger.error("Flush buffer data failed.", e);
                }
            }

            if (file.length() > DATA_FILE_MAX_LENGTH) {
            	switchFile();
            }

            if (!hasData2Flush) {
                try {
                    Thread.sleep(MAX_WAIT_TIME);
                } catch (InterruptedException e) {
                    logger.error("Failure sleep.", e);
                }
            }
        }
    }


    private String getFileName() {
        return System.currentTimeMillis() + "-" + UUID.randomUUID().toString().replaceAll("-", "");
    }

    private void switchFile() {
        String fileName = getFileName();

        try {
            outputStream.write("EOF\n".getBytes());
            outputStream.flush();
        } catch (IOException e) {
            logger.error("Write eof to cache data file.", e);
        } finally{
        	try {
        		outputStream.close();
        	} catch (IOException e) {
        		logger.error("close cache data failed.", e);
        	}
        	ServerHealthCollector.getCurrentHeathReading(null).updateData(ServerHeathReading.INFO, "DataBuffer close local file:" + file.getName());
        }
        logger.debug("Begin to switch the data file to {}.", fileName);
        try {
            file = new File(DATA_BUFFER_FILE_PARENT_DIRECTORY, fileName);
            outputStream = new FileOutputStream(file, true);
            ServerHealthCollector.getCurrentHeathReading(null).updateData(ServerHeathReading.INFO, "DataBuffer open new local file:" + file.getName());
        } catch (IOException e) {
        	ServerHealthCollector.getCurrentHeathReading(null).updateData(ServerHeathReading.ERROR, "DataBuffer open new local file failure.");
            logger.error("Switch data file failed.", e);
        }


    }

    public void saveTemporarily(byte[] s) {
        int i = Math.abs(index.getAndIncrement() % data.length);
        while (data[i] != null) {
            try {
            	ServerHealthCollector.getCurrentHeathReading(null).updateData(ServerHeathReading.WARNING, "DataBuffer index[" + i + "] data collision, service pausing. ");
                Thread.sleep(DATA_CONFLICT_WAIT_TIME);
            } catch (InterruptedException e) {
                logger.error("Failure sleep.", e);
            }
        }
        ServerHealthCollector.getCurrentHeathReading(null).updateData(ServerHeathReading.INFO, "DataBuffer reveiving data.");

        data[i] = s;
    }
}
