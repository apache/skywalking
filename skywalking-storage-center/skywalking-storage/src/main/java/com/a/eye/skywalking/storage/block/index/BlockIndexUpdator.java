package com.a.eye.skywalking.storage.block.index;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.storage.block.index.exception.BlockIndexPersistenceFailedException;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.a.eye.skywalking.storage.config.Config.BlockIndex.DATA_FILE_INDEX_FILE_NAME;
import static com.a.eye.skywalking.storage.config.Config.BlockIndex.STORAGE_BASE_PATH;

public class BlockIndexUpdator {

    private static ILog logger = LogManager.getLogger(BlockIndexUpdator.class);
    private L1Cache l1Cache;
    private L2Cache l2Cache;

    public BlockIndexUpdator(L1Cache l1Cache, L2Cache l2Cache) {
        this.l1Cache = l1Cache;
        this.l2Cache = l2Cache;
    }

    public void addRecord(long timestamp) {
        logger.info("Updating index. timestamp:{}", timestamp);
        try {
            updateFile(timestamp);
            updateCache(timestamp);
        } catch (Exception e) {
            logger.error("Failed to add index record", e);
        }
    }

    private void updateCache(long timestamp) {
        l1Cache.add2Rebuild(timestamp);
        l2Cache.add2Rebuild(timestamp);
    }


    private void updateFile(long timestamp) throws BlockIndexPersistenceFailedException {
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(new File(STORAGE_BASE_PATH, DATA_FILE_INDEX_FILE_NAME)));
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
            indexFileReader = new BufferedReader(new FileReader(new File(STORAGE_BASE_PATH, DATA_FILE_INDEX_FILE_NAME)));
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

        Collections.reverse(indexData);
        l1Cache.init(indexData);
        l2Cache.init(indexData);
    }
}
