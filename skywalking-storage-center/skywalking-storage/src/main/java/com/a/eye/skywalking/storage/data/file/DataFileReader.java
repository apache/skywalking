package com.a.eye.skywalking.storage.data.file;

import com.a.eye.skywalking.storage.data.index.IndexMetaInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Created by xin on 2016/11/6.
 */
public class DataFileReader {
    private DataFile dataFile;

    public DataFileReader(String fileName) {
        dataFile = new DataFile(fileName);
    }

    public List<byte[]> read(List<IndexMetaInfo> metaInfo) {
        List<byte[]> metaData = new ArrayList<byte[]>();

        for (IndexMetaInfo indexMetaInfo : metaInfo){
            metaData.add(dataFile.read(indexMetaInfo.getOffset(), indexMetaInfo.getLength()));
        }

        return metaData;
    }
}
