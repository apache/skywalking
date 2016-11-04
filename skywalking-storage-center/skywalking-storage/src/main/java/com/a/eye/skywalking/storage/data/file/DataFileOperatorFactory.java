package com.a.eye.skywalking.storage.data.file;

import com.a.eye.skywalking.storage.data.exception.DataFileNotFoundException;
import com.a.eye.skywalking.storage.data.exception.FileReaderCreateFailedException;
import com.a.eye.skywalking.storage.data.index.IndexMetaInfo;

public class DataFileOperatorFactory {

    public static DataFileReader newReader(IndexMetaInfo indexMetaInfo) throws FileReaderCreateFailedException {
        try {
            return new DataFileReader(indexMetaInfo);
        } catch (DataFileNotFoundException e) {
            throw new FileReaderCreateFailedException("Cannot create DataFileReader.", e);
        }
    }

    public static DataFileWriter newWriter() {
        return new DataFileWriter();
    }
}
