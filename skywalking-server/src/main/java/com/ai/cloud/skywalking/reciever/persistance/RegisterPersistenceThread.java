package com.ai.cloud.skywalking.reciever.persistance;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.skywalking.reciever.selfexamination.ServerHealthCollector;
import com.ai.cloud.skywalking.reciever.selfexamination.ServerHeathReading;

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
	}

	@Override
	public void run() {
		while (true) {
			try {
				Thread.sleep(OFFSET_WRITTEN_FILE_WAIT_CYCLE);
			} catch (InterruptedException e) {
				logger.error("Sleep failure", e);
			}

			File file = new File(REGISTER_FILE_PARENT_DIRECTORY,
					REGISTER_FILE_NAME);
			File bakFile = new File(REGISTER_FILE_PARENT_DIRECTORY,
					REGISTER_BAK_FILE_NAME);
			try {
				FileUtils.copyFile(file, bakFile);
			} catch (IOException e) {
				logger.error("Sleep failure", e);
			}

			Collection<FileRegisterEntry> fileRegisterEntries = MemoryRegister
					.instance().getEntries();
			logger.debug("file Register Entries size [{}]",
					fileRegisterEntries.size());
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
							"Write file register entry to offset file failure",
							e);
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

			ServerHealthCollector.getCurrentHeathReading(null).updateData(
					ServerHeathReading.INFO, "flush memory register to file.");
		}
	}
}
