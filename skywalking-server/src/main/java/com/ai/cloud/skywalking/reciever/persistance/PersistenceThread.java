package com.ai.cloud.skywalking.reciever.persistance;

import static com.ai.cloud.skywalking.reciever.conf.Config.Persistence.OFFSET_FILE_READ_BUFFER_SIZE;
import static com.ai.cloud.skywalking.reciever.conf.Config.Persistence.OFFSET_FILE_SKIP_LENGTH;
import static com.ai.cloud.skywalking.reciever.conf.Config.Persistence.SWITCH_FILE_WAIT_TIME;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.comparator.NameFileComparator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ai.cloud.skywalking.reciever.conf.Config;
import com.ai.cloud.skywalking.reciever.storage.StorageChainController;

public class PersistenceThread extends Thread {

    private Logger logger = LogManager.getLogger(PersistenceThread.class);

    @Override
    public void run() {
        int length;
        File file1;
        BufferedReader bufferedReader;
        int offset;
        StringBuffer data;
        while (true) {
            file1 = getDataFiles();
            if (file1 == null) {
                try {
                    Thread.sleep(SWITCH_FILE_WAIT_TIME);
                } catch (InterruptedException e) {
                    logger.error("Failure sleep", e);
                }
                continue;
            }

            try {
                bufferedReader = new BufferedReader(new FileReader(file1));
                offset = moveOffSet(file1, bufferedReader);
                if (logger.isDebugEnabled()) {
                    logger.debug("Get file[{}] offset [{}]", file1.getName(), offset);
                }
                char[] chars = new char[OFFSET_FILE_READ_BUFFER_SIZE];
                data = new StringBuffer(2048);
                boolean bool = true;
                length = 0;
                while (bool) {
                    if ((length = bufferedReader.read(chars, 0, chars.length)) == -1) {
                        MemoryRegister.instance().doRegisterStatus(new FileRegisterEntry(file1.getName(), offset,
                                FileRegisterEntry.FileRegisterEntryStatus.UNREGISTER));
                        break;
                    }
                    offset += length;
                    for (int i = 0; i < chars.length; i++) {
                        if (chars[i] != '\n') {
                            data.append(chars[i]);
                            continue;
                        }

                        if ("EOF".equals(data.toString())) {
                            bufferedReader.close();
                            logger.info("Data in file[{}] has been successfully processed", file1.getName());
                            boolean deleteSuccess = false;
                            while (!deleteSuccess) {
                                deleteSuccess = FileUtils.deleteQuietly(new File(file1.getParent(), file1.getName()));
                            }
                            logger.info("Delete file[{}] {}", file1.getName(), (deleteSuccess ? "success" : "failed"));
                            MemoryRegister.instance().unRegister(file1.getName());
                            bool = false;
                            break;
                        }

                        StorageChainController.doStorage(data.toString());
                        
                        data.delete(0, data.length());
                    }
                }
            } catch (FileNotFoundException e) {
                logger.error("The data file could not be found", e);
            } catch (IOException e) {
                logger.error("The data file could not be found", e);
            }

            try {
                Thread.sleep(SWITCH_FILE_WAIT_TIME);
            } catch (InterruptedException e) {
                logger.error("Failure sleep.", e);
            }
        }
    }

    private int moveOffSet(File file1, BufferedReader bufferedReader) throws IOException {
        int offset = MemoryRegister.instance().getOffSet(file1.getName());
        if (-1 == offset || offset == 0) {
            // 以前该文件没有被任何人处理过,需要重新注册
            MemoryRegister.instance().doRegisterStatus(new FileRegisterEntry(file1.getName(), 0,
                    FileRegisterEntry.FileRegisterEntryStatus.REGISTER));
            offset = 0;
        } else {
            char[] cha = new char[OFFSET_FILE_SKIP_LENGTH];
            int length = 0;
            while (length + OFFSET_FILE_SKIP_LENGTH < offset) {
                length += OFFSET_FILE_SKIP_LENGTH;
                bufferedReader.read(cha);
            }
            bufferedReader.read(cha, 0, Math.abs(offset - length));
            cha = null;
        }
        return offset;
    }

    private File getDataFiles() {
        File file1 = null;
        File parentDir = new File(Config.Buffer.DATA_BUFFER_FILE_PARENT_DIRECTORY);
        NameFileComparator sizeComparator = new NameFileComparator();
        File[] dataFileList = sizeComparator.sort(parentDir.listFiles());
        for (File file : dataFileList) {
            if (file.getName().startsWith(".")) {
                continue;
            }
            if (MemoryRegister.instance().isRegister(file.getName())) {
                if (logger.isDebugEnabled())
                    logger.debug("The file [{}] is being used by another thread ", file);
                continue;
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Begin to deal data file [{}]", file.getName());
            }
            file1 = file;
            break;
        }

        return file1;
    }
}
