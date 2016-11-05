package com.a.eye.skywalking.storage.data.file;

import com.a.eye.skywalking.storage.data.SpanData;
import com.a.eye.skywalking.storage.data.index.IndexMetaCollections;

import java.util.List;

public class DataFileWriter {

    private DataFile dataFile;

    public DataFileWriter() {
        dataFile = DataFilesManager.createNewDataFile();
    }

    public IndexMetaCollections write(List<SpanData> spanData) {
        if (dataFile.overLimitLength()) {
            dataFile = DataFilesManager.createNewDataFile();
        }

        IndexMetaCollections collections = new IndexMetaCollections();
        for (SpanData data : spanData) {
            collections.add(dataFile.write(data));
        }
        dataFile.flush();

        return collections;
    }
}
