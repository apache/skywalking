package com.a.eye.skywalking.reciever.peresistent;

import com.a.eye.skywalking.reciever.conf.Config;
import com.a.eye.skywalking.reciever.selfexamination.ServerHealthCollector;
import com.a.eye.skywalking.reciever.selfexamination.ServerHeathReading;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;

public class RegisterPersistenceThread extends Thread {
    private Logger logger = LogManager.getLogger(RegisterPersistenceThread.class);
    private File           offsetFile;
    private File           bakOffsetFile;
    private BufferedWriter offsetBufferWriter;
    private boolean isSwitch = true;

    public RegisterPersistenceThread() {
        super("RegisterPersistenceThread");
        File offsetParentDir = new File(Config.RegisterPersistence.REGISTER_FILE_PARENT_DIRECTORY);
        if (!offsetParentDir.exists()) {
            offsetParentDir.mkdirs();
        }

        offsetFile = new File(
                Config.RegisterPersistence.REGISTER_FILE_PARENT_DIRECTORY, Config.RegisterPersistence.REGISTER_FILE_NAME);
        bakOffsetFile = new File(
                Config.RegisterPersistence.REGISTER_FILE_PARENT_DIRECTORY, Config.RegisterPersistence.REGISTER_BAK_FILE_NAME);
        this.setDaemon(true);
    }

    @Override
    public void run() {

        while (true) {
            try {
                Thread.sleep(Config.RegisterPersistence.OFFSET_WRITTEN_FILE_WAIT_CYCLE);
            } catch (InterruptedException e) {
                logger.error("Sleep failure", e);
            }
            chooseOffsetFile();
            appendingTimestamp();
            wirteFileRegisterEntries();
            closeAndReleaseResource();
            ServerHealthCollector.getCurrentHeathReading(null)
                    .updateData(ServerHeathReading.INFO, "flush memory register to file.");
        }
    }

    private void chooseOffsetFile() {
        try {
            if (isSwitch) {
                offsetBufferWriter = new BufferedWriter(new FileWriter(offsetFile));
            } else {
                offsetBufferWriter = new BufferedWriter(new FileWriter(bakOffsetFile));
            }
            isSwitch = !isSwitch;
        } catch (IOException e) {
            logger.error("Write The offset file anomalies.");
        }
    }

    private void appendingTimestamp() {
        try {
            offsetBufferWriter.write(String.valueOf(System.currentTimeMillis()) + "\n");
        } catch (IOException e) {
            logger.error("Write The offset file anomalies.");
        }
    }

    private void wirteFileRegisterEntries() {
        Collection<FileRegisterEntry> fileRegisterEntries = MemoryRegister.instance().getEntries();
        for (FileRegisterEntry fileRegisterEntry : fileRegisterEntries) {
            try {
                offsetBufferWriter.write(fileRegisterEntry.toString() + "\n");
            } catch (IOException e) {
                logger.error("Write file register entry to offset file failure", e);
            }
        }
    }

    private void closeAndReleaseResource() {
        try {
            offsetBufferWriter.write("EOF\n");
            offsetBufferWriter.flush();
        } catch (IOException e) {
            logger.error("Flush offset file failure", e);
        } finally {
            try {
                offsetBufferWriter.close();
            } catch (IOException e) {
                logger.error("close offset file failure", e);
            }
        }
    }
}
