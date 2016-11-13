package com.a.eye.skywalking.storage.data.file;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.storage.config.Config;
import com.a.eye.skywalking.storage.data.exception.DataFileOperatorCreateFailedException;
import com.a.eye.skywalking.storage.data.exception.SpanDataPersistenceFailedException;
import com.a.eye.skywalking.storage.data.exception.SpanDataReadFailedException;
import com.a.eye.skywalking.storage.data.index.IndexMetaInfo;
import com.a.eye.skywalking.storage.data.spandata.SpanData;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 数据文件
 */
public class DataFile {

    private static ILog logger = LogManager.getLogger(DataFile.class);
    private String           fileName;
    private long             currentOffset;
    private DataFileOperator operator;

    static {
        File dataFileDir = new File(Config.DataFile.BASE_PATH);
        if (!dataFileDir.exists()) {
            dataFileDir.mkdirs();
        }
    }

    public DataFile() {
        this.fileName = System.currentTimeMillis() + "";
        this.currentOffset = 0;
        operator = new DataFileOperator();
        createFile();
    }

    public DataFile(String fileName) {
        this.fileName = fileName;
        operator = new DataFileOperator();
        createFile();
    }

    public DataFile(File file) {
        this.fileName = file.getName();
        this.currentOffset = file.length();
        operator = new DataFileOperator();
        createFile();
    }

    private void createFile() {
        File dataFile = new File(Config.DataFile.BASE_PATH, fileName);
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                logger.error("Failed to create data file.", e);
                throw new DataFileOperatorCreateFailedException("Failed to create data file", e);
            }
        }
    }

    public boolean overLimitLength() {
        return currentOffset >= Config.DataFile.MAX_LENGTH;
    }

    public IndexMetaInfo write(SpanData data) {
        byte[] bytes = data.toByteArray();
        try {
            operator.getWriter().write(bytes);
            IndexMetaInfo metaInfo = new IndexMetaInfo(data, fileName, currentOffset, bytes.length);
            currentOffset += bytes.length;
            return metaInfo;
        } catch (IOException e) {
            throw new SpanDataPersistenceFailedException(e);
        }
    }

    public void flush() {
        try {
            operator.getWriter().flush();
        } catch (IOException e) {
            throw new SpanDataPersistenceFailedException(e);
        }
    }

    public byte[] read(long offset, int length) {
        byte[] data = new byte[length];
        try {
            operator.getReader().getChannel().position(offset);
            operator.getReader().read(data, 0, length);
            return data;
        } catch (IOException e) {
            throw new SpanDataReadFailedException(
                    "Failed to read dataFile[" + fileName + "], offset: " + offset + " " + "lenght: " + length, e);
        }
    }

    class DataFileOperator {
        private FileOutputStream writer;
        private FileInputStream  reader;

        public FileOutputStream getWriter() {

            if (writer == null) {
                try {
                    writer = new FileOutputStream(new File(Config.DataFile.BASE_PATH, fileName), true);
                } catch (IOException e) {
                    throw new DataFileOperatorCreateFailedException("Failed to create datafile output stream", e);
                }
            }

            return writer;
        }

        public FileInputStream getReader() {
            if (reader == null) {
                try {
                    reader = new FileInputStream(new File(Config.DataFile.BASE_PATH, fileName));
                } catch (IOException e) {
                    throw new DataFileOperatorCreateFailedException("Failed to create datafile input stream", e);
                }
            }

            return reader;
        }
    }
}
