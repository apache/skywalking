package com.ai.cloud.skywalking.reciever.buffer;

import com.ai.cloud.skywalking.protocol.BufferFileEOFProtocol;
import com.ai.cloud.skywalking.protocol.TransportPackager;
import com.ai.cloud.skywalking.protocol.util.AtomicRangeInteger;
import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHealthCollector;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHeathReading;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

import static com.ai.cloud.skywalking.reciever.conf.Config.Buffer.DATA_CONFLICT_WAIT_TIME;
import static com.ai.cloud.skywalking.reciever.conf.Config.Buffer.PER_THREAD_MAX_BUFFER_NUMBER;

public class DataBufferThread extends Thread {

    private Logger             logger = LogManager.getLogger(DataBufferThread.class);
    private byte[][]           data   = new byte[PER_THREAD_MAX_BUFFER_NUMBER][];
    private AtomicRangeInteger index  = new AtomicRangeInteger(0, PER_THREAD_MAX_BUFFER_NUMBER);

    public DataBufferThread(int threadIdx) {
        super("DataBufferThread_" + threadIdx);
    }

    @Override
    public void run() {
        FileOutputStream fileOutputStream = null;
        int length = 0;
        while (true) {
            for (int i = 0; i < data.length; i++) {
                if (data[i] == null) {
                    continue;
                }

                if (fileOutputStream == null) {
                    fileOutputStream = acquiredNewBufferFileStream();
                }

                try {
                    fileOutputStream.write(BufferDataAssist.appendLengthAndSplit(data[i]));
                    length += data[i].length;
                    data[i] = null;
                } catch (IOException e) {
                    logger.error("Failed to write msg.", e);
                }

                if (length > Config.Buffer.BUFFER_FILE_MAX_LENGTH) {
                    closeCurrentBufferFile(fileOutputStream);
                    fileOutputStream = null;
                }
            }

            try {
                Thread.sleep(Config.Buffer.MAX_WAIT_TIME);
            } catch (InterruptedException e) {
                logger.error("Failed to sleep.", e);
            }

        }
    }

    private void closeCurrentBufferFile(FileOutputStream fileOutputStream) {
        try {
            fileOutputStream.flush();
            fileOutputStream.write(BufferDataAssist
                    .appendLengthAndSplit(TransportPackager.serialize(new BufferFileEOFProtocol())));
        } catch (IOException e) {
            logger.error("Failed to write msg.", e);
        } finally {
            try {
                fileOutputStream.flush();
                fileOutputStream.close();
            } catch (IOException e) {
                logger.error("Failed to flush and close file.", e);
            }
        }
    }

    private FileOutputStream acquiredNewBufferFileStream() {
        checkBufferDirIsExists();
        File outputFile = createNewBufferFile();
        return generateFileOutputStream(outputFile);
    }

    private FileOutputStream generateFileOutputStream(File outputFile) {
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(outputFile);
        } catch (FileNotFoundException e) {
            logger.error("Failed to create File:{}", outputFile.getName(), e);
        }
        return fileOutputStream;
    }

    private File createNewBufferFile() {
        File outputFile = new File(Config.Buffer.DATA_BUFFER_FILE_PARENT_DIR,
                System.currentTimeMillis() + "-" + UUID.randomUUID().toString());
        if (!outputFile.exists()) {
            try {
                outputFile.createNewFile();
                ServerHealthCollector.getCurrentHeathReading(null)
                        .updateData(ServerHeathReading.INFO, "Create new Buffer file[" + outputFile.getName() + "]");
            } catch (IOException e) {
                logger.error("Failed to create File:{}", outputFile.getName(), e);
            }
        }
        return outputFile;
    }

    private void checkBufferDirIsExists() {
        File outPutDir = new File(Config.Buffer.DATA_BUFFER_FILE_PARENT_DIR);
        if (!outPutDir.exists()) {
            outPutDir.mkdirs();
        }
    }

    public void saveTemporarily(byte[] s) {
        int i = index.getAndIncrement();
        while (data[i] != null) {
            try {
                ServerHealthCollector.getCurrentHeathReading(null).updateData(ServerHeathReading.WARNING,
                        "DataBuffer index[" + i + "] data collision, service pausing. ");
                Thread.sleep(DATA_CONFLICT_WAIT_TIME);
            } catch (InterruptedException e) {
                logger.error("Failure sleep.", e);
            }
        }
        ServerHealthCollector.getCurrentHeathReading(null)
                .updateData(ServerHeathReading.INFO, "DataBuffer reveiving data.");

        data[i] = s;
    }
}
