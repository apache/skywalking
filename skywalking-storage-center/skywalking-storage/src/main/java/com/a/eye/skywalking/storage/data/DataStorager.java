package com.a.eye.skywalking.storage.data;

import com.a.eye.skywalking.storage.index.IndexData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * 主要用于源数据文件的缓存
 */
public class DataStorager {
    private static Logger logger = LogManager.getLogger(DataStorager.class);
    private DataFileWriter writer;

    public void start() {

    }

    public IndexData save(byte[] data) {

        // convert it to data;
//        IndexData index = new IndexData();

        try {
           int offset =  writer.write(data);
        } catch (IOException e) {
            logger.error("Failed to save data");
            return null;
        }

        return null;
    }
}
