package com.ai.cloud.skywalking.reciever.peresistent;

import com.ai.cloud.skywalking.reciever.conf.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.ai.cloud.skywalking.reciever.conf.Config.RegisterPersistence.*;

public class MemoryRegister {
    private Logger                         logger  = LogManager.getLogger(MemoryRegister.class);
    private Map<String, FileRegisterEntry> entries = new ConcurrentHashMap<String, FileRegisterEntry>();
    private File file;
    private static MemoryRegister memoryRegister = new MemoryRegister();

    public static MemoryRegister instance() {
        return memoryRegister;
    }

    public void updateOffSet(String fileName, int offset) {
        if (logger.isDebugEnabled()) {
            logger.debug("Register entry[{}] offset[{}] into the memory register", fileName, offset);
        }
        if (entries.containsKey(fileName)) {
            entries.get(fileName).setOffset(offset);
        }
    }

    public void unRegister(String fileName) {
        if (logger.isDebugEnabled()) {
            logger.debug("Unregister[{}] from the memory register", fileName);
        }
        if (entries.containsKey(fileName)) {
            entries.get(fileName).setStatus(FileRegisterEntry.FileRegisterEntryStatus.UNREGISTER);
        }
    }


    public void removeEntry(String fileName) {
        entries.remove(fileName);
    }

    public synchronized FileRegisterEntry doRegister(String fileName) {
        logger.debug("Begin to register File[{}]", fileName);
        FileRegisterEntry entry = null;
        // 已经存在entries.
        if (entries.containsKey(fileName)) {
            logger.debug("FileRegisterEntry[{}] Status:[{}]", entries.get(fileName).getStatus());
            // 已经被别的线程处理中
            if (entries.get(fileName).getStatus() == FileRegisterEntry.FileRegisterEntryStatus.REGISTER) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Entry[{}] has been register", fileName);
                }

            } else {
                // 没有被别的线程处理
                entry = entries.get(fileName);
                entry.setStatus(FileRegisterEntry.FileRegisterEntryStatus.REGISTER);
            }
        } else {
            // 以前没有被注册过的
            entry = new FileRegisterEntry(fileName, 0, FileRegisterEntry.FileRegisterEntryStatus.REGISTER);
            entries.put(fileName, entry);
        }

        return entry;
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
        // 在处理数据之前需要初始化处理文件的处理状态
        //去掉entries中无法与缓存数据文件匹配的文件
        File parentDir = new File(Config.Buffer.DATA_BUFFER_FILE_PARENT_DIR);
        //上次未处理的缓存数据文件，entries内的数据主要以缓存
        List<String> bufferFileNameList = Arrays.asList(parentDir.list());

        try {
            // 读取offset文件
            OffsetFile mainOffsetFile = new OffsetFile(REGISTER_FILE_PARENT_DIRECTORY + File.separator +
                    REGISTER_FILE_NAME, bufferFileNameList);
            OffsetFile backUpOffsetFile = new OffsetFile(REGISTER_FILE_PARENT_DIRECTORY + File.separator +
                    REGISTER_BAK_FILE_NAME, bufferFileNameList);

            if (mainOffsetFile.compare(backUpOffsetFile)) {
                entries.putAll(mainOffsetFile.getRegisterEntries());
            } else {
                entries.putAll(backUpOffsetFile.getRegisterEntries());
            }
        } catch (FileNotFoundException e) {
            logger.error("The offset file does not exist.", e);
            checkOffSetExists();
        } catch (IOException e) {
            logger.error("Read data from offset file failed.", e);
        }
    }
}
