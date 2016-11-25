package com.a.eye.skywalking.storage.data.file;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DataFileLoader {

    private String basePath;

    public DataFileLoader(String basePath) {
        this.basePath = basePath;
    }

    public List<DataFile> load() {
        File dataFileDir = new File(basePath);

        if (!dataFileDir.exists()) {
            dataFileDir.mkdirs();
        }

        List<DataFile> allDataFile = new ArrayList<DataFile>();
        for (File fileEntry : dataFileDir.listFiles()) {
            if (fileEntry.getName().split("_").length == 8)
                allDataFile.add(new DataFile(fileEntry));
        }
        return allDataFile;
    }


}
