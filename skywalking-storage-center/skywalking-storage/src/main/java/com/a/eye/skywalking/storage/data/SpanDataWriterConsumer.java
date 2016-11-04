package com.a.eye.skywalking.storage.data;

import com.a.eye.datacarrier.consumer.IConsumer;
import com.a.eye.skywalking.storage.data.file.DataFileWriter;
import com.a.eye.skywalking.storage.data.index.IndexMetaInfo;
import com.a.eye.skywalking.storage.data.index.IndexOperator;
import com.a.eye.skywalking.storage.data.index.IndexOperatorFactory;

import java.util.List;

public class SpanDataWriterConsumer implements IConsumer<SpanData> {

    private DataFileWriter writer;

    @Override
    public void consume(List<SpanData> list) {
        for (SpanData data : list) {
            IndexOperator operator = IndexOperatorFactory.get(data.getTimestamp());
            IndexMetaInfo metaInfo = writer.write(data.convertToByte());
            operator.update(metaInfo);
        }
    }

    @Override
    public void onError(List<SpanData> list, Throwable throwable) {

    }
}
