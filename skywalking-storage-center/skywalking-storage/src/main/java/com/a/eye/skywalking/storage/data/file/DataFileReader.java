package com.a.eye.skywalking.storage.data.file;

import com.a.eye.skywalking.storage.config.Config;
import com.a.eye.skywalking.storage.data.exception.DataFileNotFoundException;
import com.a.eye.skywalking.storage.data.index.IndexMetaInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

public class DataFileReader {

    private static Logger logger = LogManager.getLogger(DataFileReader.class);

    private long            offset;
    private int             length;
    private String          fileName;
    private FileInputStream reader;

    public DataFileReader(IndexMetaInfo indexMetaInfo) throws DataFileNotFoundException {
        try {
            reader = new FileInputStream(new File(Config.DataFile.BASE_PATH, indexMetaInfo.getFileName()));
            this.offset = indexMetaInfo.getOffset();
            this.length = indexMetaInfo.getLength();
            this.fileName = indexMetaInfo.getFileName();
        } catch (FileNotFoundException e) {
            throw new DataFileNotFoundException(indexMetaInfo.getFileName() + " not found.", e);
        }
    }

    public byte[] read() {
        try {
            reader.getChannel().position(this.offset);
            byte[] dataByte = new byte[length];
            reader.read(dataByte, 0, length);
            return dataByte;
        } catch (IOException e) {
            logger.error("Failed to read file:{} position:{} length:{}", fileName, offset, length);
            return null;
        }
    }
}
