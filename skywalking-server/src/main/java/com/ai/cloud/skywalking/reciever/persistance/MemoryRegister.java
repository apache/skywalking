package com.ai.cloud.skywalking.reciever.persistance;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.ai.cloud.skywalking.reciever.conf.Config.RegisterPersistence.REGISTER_FILE_NAME;
import static com.ai.cloud.skywalking.reciever.conf.Config.RegisterPersistence.REGISTER_FILE_PARENT_DIRECTORY;

public class MemoryRegister {
    private Logger logger = LogManager.getLogger(MemoryRegister.class);
    private Map<String, FileRegisterEntry> entries = new ConcurrentHashMap<String, FileRegisterEntry>();
    private File file;
    private static MemoryRegister memoryRegister = new MemoryRegister();

    public static MemoryRegister instance() {
        return memoryRegister;
    }

    public synchronized void doRegisterStatus(FileRegisterEntry fileRegisterEntry) {
        if (logger.isDebugEnabled()) {
            logger.debug("Register entry[{}] into the memory register", fileRegisterEntry.getFileName());
        }
        entries.put(fileRegisterEntry.getFileName(), fileRegisterEntry);
    }

    public void unRegister(String fileName) {
        if (logger.isDebugEnabled()) {
            logger.debug("Unregister[{}] from the memory register", fileName);
        }
        entries.remove(fileName);
    }

    public synchronized boolean isRegister(String fileName) {
        if (entries.containsKey(fileName)) {
            if (entries.get(fileName).getStatue() == FileRegisterEntry.FileRegisterEntryStatus.REGISTER) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Entry[{}] has been register", fileName);
                }
                return true;
            }
        }
        return false;
    }


    public Collection<FileRegisterEntry> getEntries() {
        return entries.values();
    }

    public int getOffSet(String fileName) {
        if (entries.containsKey(fileName)) {
            return entries.get(fileName).getOffset();
        }
        return -1;
    }

    private void checkOffSetExists() {
        file = new File(REGISTER_FILE_PARENT_DIRECTORY, REGISTER_FILE_NAME);

        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException e) {
                logger.error("Create offset filed failed", e);
            }
        }
    }

    private MemoryRegister() {
        // 读取offset文件
        checkOffSetExists();
        BufferedReader reader;
        // 在处理数据之前需要初始化处理文件的处理状态
        try {
            reader = new BufferedReader(new FileReader(file));
            String offsetData;
            while ((offsetData = reader.readLine()) != null && !"EOF".equals(offsetData)) {
                String[] ss = offsetData.split("\t");
                entries.put(ss[0], new FileRegisterEntry(ss[0], Integer.valueOf(ss[1]), FileRegisterEntry.FileRegisterEntryStatus.UNREGISTER));
            }
        } catch (FileNotFoundException e) {
            logger.error("The offset file does not exist.", e);
            checkOffSetExists();
        } catch (IOException e) {
            logger.error("Read data from offset file failed.", e);
        }
    }
}
