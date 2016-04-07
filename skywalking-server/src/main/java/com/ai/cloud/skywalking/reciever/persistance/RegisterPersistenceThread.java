package com.ai.cloud.skywalking.reciever.persistance;

import com.ai.cloud.skywalking.reciever.selfexamination.ServerHealthCollector;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHeathReading;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;

import static com.ai.cloud.skywalking.reciever.conf.Config.RegisterPersistence.*;

public class RegisterPersistenceThread extends Thread {

    private Logger logger = LogManager
            .getLogger(RegisterPersistenceThread.class);

    private BufferedWriter writer;

    public RegisterPersistenceThread() {
        super("RegisterPersistenceThread");
        File offsetParentDir = new File(REGISTER_FILE_PARENT_DIRECTORY);
        if (!offsetParentDir.exists()){
            offsetParentDir.mkdirs();
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                Thread.sleep(OFFSET_WRITTEN_FILE_WAIT_CYCLE);
            } catch (InterruptedException e) {
                logger.error("Sleep failure", e);
            }

            try {
                File file = new File(REGISTER_FILE_PARENT_DIRECTORY,
                        REGISTER_FILE_NAME);
                File bakFile = new File(REGISTER_FILE_PARENT_DIRECTORY,
                        REGISTER_BAK_FILE_NAME);
                // 先删除备份文件
                if (bakFile.exists()) {
                    bakFile.delete();
                }

                // 将文件改名字
                file.renameTo(bakFile);

                //
                if (!file.exists()) {
                    file.createNewFile();
                }

                Collection<FileRegisterEntry> fileRegisterEntries = MemoryRegister
                        .instance().getEntries();
                try {
                    writer = new BufferedWriter(new FileWriter(file));
                } catch (IOException e) {
                    logger.error("Write The offset file anomalies.");
                }

                for (FileRegisterEntry fileRegisterEntry : fileRegisterEntries) {
                    try {
                        writer.write(fileRegisterEntry.toString() + "\n");
                    } catch (IOException e) {
                        logger.error(
                                "Write file register entry to offset file failure", e);
                    }
                }
                try {
                    writer.write("EOF\n");
                    writer.flush();
                } catch (IOException e) {
                    logger.error("Flush offset file failure", e);
                } finally {
                    try {
                        writer.close();
                    } catch (IOException e) {
                        logger.error("close offset file failure", e);
                    }
                }
            } catch (IOException e) {
                logger.error("Failed to back up offset file.", e);
            }


            ServerHealthCollector.getCurrentHeathReading(null).updateData(
                    ServerHeathReading.INFO, "flush memory register to file.");
        }
    }
}
