package com.a.eye.skywalking.storage.data.file;

import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
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
        int failedCount = 0;
        try {
            for (SpanData data : spanData) {
                try {
                    collections.add(dataFile.write(data));
                }catch (Throwable e){
                    failedCount++;
                }
            }
        }finally {
            dataFile.flush();
        }

        if (failedCount > 0) {
            HealthCollector.getCurrentHeathReading("DataFileWriter").updateData(HeathReading.ERROR ,"Failed to write %s span to data file.", Integer.valueOf(failedCount));
        }

        return collections;
    }

    public void close(){
        dataFile.close();
    }
}
