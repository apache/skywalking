package com.a.eye.skywalking.storage.data.file;

import java.util.ArrayList;
import java.util.List;

/**
 * 用于选择所有没有达到最大容量的数据文件
 */
public class UnFinishedDataFilePicker {

    private List<DataFile> dataFiles;

    public UnFinishedDataFilePicker(List<DataFile> dataFiles) {
        this.dataFiles = dataFiles;
    }

    public List<DataFile> pickUp() {
        List<DataFile> result = new ArrayList<DataFile>();
        for (DataFile file : dataFiles) {
            if (file.overLimitLength()){
                result.add(file);
            }
        }
        return result;
    }
}
