package com.a.eye.skywalking.storage.data;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 主要功能：
 * 1. 控制源数据的写入
 * 2. 当数据文件满足阈值，自动切换文件
 */
public class DataFileWriter {

    private FileOutputStream outputStream;
    private File             currentOriginDataFile;
    private String           bufferFileBasePath;

    public DataFileWriter(String bufferFileBasePath) {
        this.bufferFileBasePath = bufferFileBasePath;

    }

    public DataFileWriter(File bufferFile) {

    }

    public int write(byte[] data) throws IOException {
        outputStream.write(data);
        return 0;
    }

    private void convertFile() {

    }

    public void releaseResource() throws IOException {
        outputStream.flush();
        outputStream.close();
    }

}
