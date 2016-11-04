package com.a.eye.skywalking.storage.data.file;

import com.a.eye.skywalking.storage.data.SpanData;
import com.a.eye.skywalking.storage.data.index.IndexMetaInfo;

import java.util.List;

public class DataFileWriter {

    private DataFile dataFile;

    public DataFileWriter() {
        dataFile = DataFilesManager.createNewDataFile();
    }

    public List<IndexMetaInfo> write(List<SpanData> spanData) {

        if (dataFile.overLimitLength()) {
            dataFile = DataFilesManager.createNewDataFile();
        }

        return null;
    }
}
