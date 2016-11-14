package com.a.eye.skywalking.storage.block.index;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.storage.block.index.exception.BlockIndexPersistenceFailedException;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.a.eye.skywalking.storage.config.Config.BlockIndex.FILE_NAME;
import static com.a.eye.skywalking.storage.config.Config.BlockIndex.PATH;
import static com.a.eye.skywalking.storage.util.PathResolver.getAbsolutePath;

public class BlockIndexUpdator {

    private static ILog logger = LogManager.getLogger(BlockIndexUpdator.class);
    private L1Cache l1Cache;
    private L2Cache l2Cache;

    public BlockIndexUpdator(L1Cache l1Cache, L2Cache l2Cache) {
        this.l1Cache = l1Cache;
        this.l2Cache = l2Cache;
    }

    public void addRecord(long timestamp) {
        logger.info("Updating block index. index key:{}", timestamp);
        try {
            updateFile(timestamp);
            updateCache(timestamp);
        } catch (Exception e) {
            logger.error("Failed to add block index record", e);
        }
    }

    private void updateCache(long timestamp) {
        l1Cache.add2Rebuild(timestamp);
        l2Cache.add2Rebuild(timestamp);
    }


    private void updateFile(long timestamp) throws BlockIndexPersistenceFailedException {
        BufferedWriter writer = null;
        try {
            File blockIndexFile = getOrCreateBlockIndexFile();
            writer = new BufferedWriter(new FileWriter(blockIndexFile));
            writer.write(String.valueOf(timestamp));
            writer.newLine();
            writer.close();
        } catch (IOException e) {
            throw new BlockIndexPersistenceFailedException("Failed to save index[" + timestamp + "]", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    logger.error("Failed to close index file", e);
                }
            }
        }
    }


    void init() {
        List<Long> indexData = new ArrayList<>();
        BufferedReader indexFileReader = null;
        try {
            File blockIndexFile = getOrCreateBlockIndexFile();
            indexFileReader = new BufferedReader(new FileReader(blockIndexFile));
            String indexDataStr = null;
            while ((indexDataStr = indexFileReader.readLine()) != null) {
                indexData.add(Long.parseLong(indexDataStr));
            }
        } catch (IOException e) {
            logger.error("Failed to read index data.", e);
        } finally {
            if (indexFileReader != null) {
                try {
                    indexFileReader.close();
                } catch (IOException e) {
                    logger.error("Failed to close index file", e);
                }
            }
        }


        if (indexData.size() == 0) {
            if (logger.isDebugEnable()) {
                logger.debug("Any block index was not founded. will add new block index.", indexData.size());
            }
            //如果此前没有记录，则取之前五分钟到目前的数据
            addRecord(System.currentTimeMillis() - 5 * 60 * 1000);
            return;
        }

        if (logger.isDebugEnable()) {
            logger.debug("There are {} block index was founded. Begin to init L1Cache and L2Cache", indexData.size());
        }

        Collections.reverse(indexData);
        l1Cache.init(indexData);
        l2Cache.init(indexData);
    }

    public File getOrCreateBlockIndexFile() throws IOException {
        if (logger.isDebugEnable()) {

        }
        File blockIndexFile = new File(getAbsolutePath(PATH), FILE_NAME);

        if (!blockIndexFile.getParentFile().exists()) {
            blockIndexFile.getParentFile().mkdirs();
        }

        if (!blockIndexFile.exists()) {
            blockIndexFile.createNewFile();
        }
        return blockIndexFile;
    }
}
