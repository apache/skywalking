package com.a.eye.skywalking.storage.data.file;

import com.a.eye.skywalking.storage.data.index.IndexMetaInfo;

public class DataFileWriter {

    private DataFile dataFile;

    public DataFileWriter() {

    }

    public IndexMetaInfo write(byte[] data) {
        if (dataFile.overLimitLength()) {
            convertDataFile();
        }

        long offset = dataFile.writeAndFlush(data);
        return new IndexMetaInfo(dataFile.getName(), offset, data.length);
    }

    private void convertDataFile() {
        dataFile.close();
        dataFile = new DataFile();
    }

}
