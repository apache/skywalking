package com.a.eye.skywalking.storage.data.file;

import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.network.grpc.AckSpan;
import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.storage.data.index.IndexMetaInfo;
import com.a.eye.skywalking.storage.data.spandata.AckSpanData;
import com.a.eye.skywalking.storage.data.spandata.RequestSpanData;
import com.a.eye.skywalking.storage.data.spandata.SpanData;
import com.a.eye.skywalking.storage.data.spandata.SpanType;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xin on 2016/11/6.
 */
public class DataFileReader {
    private static ILog logger = LogManager.getLogger(DataFileReader.class);
    private DataFile dataFile;

    public DataFileReader(String fileName) {
        dataFile = new DataFile(fileName);
    }

    public List<SpanData> read(List<IndexMetaInfo> metaInfo) {
        List<SpanData> metaData = new ArrayList<SpanData>();

        for (IndexMetaInfo indexMetaInfo : metaInfo) {
            byte[] dataByte = dataFile.read(indexMetaInfo.getOffset(), indexMetaInfo.getLength());
            try {
                if (indexMetaInfo.getSpanType() == SpanType.RequestSpan) {
                    metaData.add(new RequestSpanData(RequestSpan.parseFrom(dataByte)));
                } else {
                    metaData.add(new AckSpanData(AckSpan.parseFrom(dataByte)));
                }
            } catch (Exception e) {
                logger.error("Failed to conver to data", e);
            }
        }

        return metaData;
    }
}
