package com.a.eye.skywalking.storage.data;

import com.a.eye.datacarrier.consumer.IConsumer;
import com.a.eye.skywalking.storage.block.index.BlockIndexEngine;
import com.a.eye.skywalking.storage.data.file.DataFileWriter;
import com.a.eye.skywalking.storage.data.index.IndexMetaInfo;
import com.a.eye.skywalking.storage.data.index.IndexOperator;
import com.a.eye.skywalking.storage.data.index.IndexOperatorCache;

import java.util.List;
import java.util.Map;

public class SpanDataConsumer implements IConsumer<SpanData> {

    private IndexOperatorCache cache;
    private DataFileWriter     fileWriter;

    @Override
    public void consume(List<SpanData> data) {
        List<IndexMetaInfo> indexMetaInfo = fileWriter.write(data);

        Map<Long, List<IndexMetaInfo>> categorizedMetaInfo =
                IndexMetaInfoCategory.categorizeByDataIndexTime(indexMetaInfo, BlockIndexEngine.newFinder());

        for (Map.Entry<Long, List<IndexMetaInfo>> indexEntry : categorizedMetaInfo.entrySet()) {
            IndexOperator indexOperator = cache.get(indexEntry.getKey());
            indexOperator.update(indexEntry.getValue());
        }

    }

    @Override
    public void onError(List<SpanData> list, Throwable throwable) {

    }
}
