package com.a.eye.skywalking.storage.data.file;

import com.a.eye.skywalking.storage.data.spandata.SpanData;
import com.a.eye.skywalking.storage.data.index.IndexMetaCollection;

import java.util.List;

public class DataFileWriter {

    private DataFile dataFile;

    public DataFileWriter() {
        dataFile = DataFilesManager.createNewDataFile();
    }

    public IndexMetaCollection write(List<SpanData> spanData) {
        if (dataFile.overLimitLength()) {
            this.close();
            dataFile = DataFilesManager.createNewDataFile();
        }

        IndexMetaCollection collections = new IndexMetaCollection();
        for (SpanData data : spanData) {
            collections.add(dataFile.write(data));
        }

        dataFile.flush();

        return collections;
    }

    public void close(){
        dataFile.close();
    }
}
