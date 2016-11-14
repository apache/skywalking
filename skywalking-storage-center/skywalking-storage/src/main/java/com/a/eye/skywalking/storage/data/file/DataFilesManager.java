package com.a.eye.skywalking.storage.data.file;

import com.a.eye.skywalking.storage.config.Config;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import static com.a.eye.skywalking.storage.util.PathResolver.getAbsolutePath;

/**
 * 管理数据文件，目前主要是用来加载所有未满的数据文件，以及创建数据文件
 */
public class DataFilesManager {

    private static ConcurrentLinkedDeque<DataFile> unFinishedDataFiles = new ConcurrentLinkedDeque<>();

    public static void init() {
        List<DataFile> allDataFile = new DataFileLoader(getAbsolutePath(Config.DataFile.PATH)).load();
        unFinishedDataFiles = new ConcurrentLinkedDeque<>(new UnFinishedDataFilePicker(allDataFile).pickUp());
    }

    public static DataFile createNewDataFile() {
        DataFile dataFile = unFinishedDataFiles.poll();
        if (dataFile == null) {
            dataFile = new DataFile();
        }

        return dataFile;
    }

}
