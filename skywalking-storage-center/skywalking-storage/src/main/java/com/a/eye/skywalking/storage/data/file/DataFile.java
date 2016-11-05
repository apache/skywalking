package com.a.eye.skywalking.storage.data.file;

import com.a.eye.skywalking.storage.config.Config;
import com.a.eye.skywalking.storage.data.SpanData;
import com.a.eye.skywalking.storage.data.exception.DataFileOperatorCreateFailedException;
import com.a.eye.skywalking.storage.data.exception.SpanDataPersistenceFailedException;
import com.a.eye.skywalking.storage.data.index.IndexMetaInfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 数据文件
 */
public class DataFile {

    private String           fileName;
    private long             currentOffset;
    private DataFileOperator operator;

    public DataFile() {
        this.fileName = System.currentTimeMillis() + "";
        this.currentOffset = 0;
        operator = new DataFileOperator();
    }

    public DataFile(String fileName, long offset) {
        this.fileName = fileName;
        this.currentOffset = offset;
        operator = new DataFileOperator();
    }

    public DataFile(File file) {
        this.fileName = file.getName();
        this.currentOffset = file.length();
        operator = new DataFileOperator();
    }

    public boolean overLimitLength() {
        return currentOffset >= Config.DataFile.MAX_LENGTH;
    }

    public IndexMetaInfo write(SpanData data) {
        byte[] bytes = data.toByteArray();
        try {
            operator.getWriter().write(bytes);
            IndexMetaInfo metaInfo = new IndexMetaInfo(fileName, currentOffset, bytes.length);
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

    class DataFileOperator {
        private FileOutputStream writer;
        private FileInputStream  reader;

        public FileOutputStream getWriter() {

            if (writer == null) {
                try {
                    writer = new FileOutputStream(new File(fileName));
                } catch (IOException e) {
                    throw new DataFileOperatorCreateFailedException("Failed to create datafile output stream", e);
                }
            }

            return writer;
        }

        public FileInputStream getReader() {
            if (reader == null) {
                try {
                    reader = new FileInputStream(new File(fileName));
                } catch (IOException e) {
                    throw new DataFileOperatorCreateFailedException("Failed to create datafile input stream", e);
                }
            }

            return reader;
        }
    }
}
