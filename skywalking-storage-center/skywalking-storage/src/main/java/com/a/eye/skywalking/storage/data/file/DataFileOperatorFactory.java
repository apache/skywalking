package com.a.eye.skywalking.storage.data.file;

import com.a.eye.skywalking.storage.data.index.IndexMetaInfo;


public class DataFileOperatorFactory {
    public static DataFileReader getDataFileReader(IndexMetaInfo info) {
        return new DataFileReader(info);
    }
}
