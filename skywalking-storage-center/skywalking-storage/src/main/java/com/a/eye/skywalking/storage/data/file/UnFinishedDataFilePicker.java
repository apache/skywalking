package com.a.eye.skywalking.storage.data.file;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 用于选择所有没有达到最大容量的数据文件
 */
public class UnFinishedDataFilePicker {

    private static ILog logger = LogManager.getLogger(UnFinishedDataFilePicker.class);

    private List<DataFile> dataFiles;

    public UnFinishedDataFilePicker(List<DataFile> dataFiles) {
        this.dataFiles = dataFiles;
    }

    public List<DataFile> pickUp() {
        List<DataFile> result = new ArrayList<DataFile>();
        for (DataFile file : dataFiles) {
            if (!file.overLimitLength()) {
                result.add(file);
            }
        }
        logger.info("Unfinished files: [{}].", result);
        return result;
    }
}
