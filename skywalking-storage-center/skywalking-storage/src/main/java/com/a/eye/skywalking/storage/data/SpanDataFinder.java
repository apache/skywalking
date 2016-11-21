package com.a.eye.skywalking.storage.data;

import com.a.eye.skywalking.network.grpc.TraceId;
import com.a.eye.skywalking.storage.data.file.DataFileReader;
import com.a.eye.skywalking.storage.data.index.*;
import com.a.eye.skywalking.storage.data.index.IndexOperator;
import com.a.eye.skywalking.storage.data.index.IndexOperatorFactory;
import com.a.eye.skywalking.storage.data.spandata.SpanData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class SpanDataFinder {

    public static List<SpanData> find(TraceId traceId) {
        IndexMetaCollection indexMetaCollection = fetchIndexMetaInfos(traceId);

        if (indexMetaCollection == null) {
            return new ArrayList<SpanData>();
        }

        Iterator<IndexMetaGroup<String>> iterator =
                IndexMetaCollections.group(indexMetaCollection, new GroupKeyBuilder<String>() {
                    @Override
                    public String buildKey(IndexMetaInfo metaInfo) {
                        return metaInfo.getFileName().fileName();
                    }
                }).iterator();

        List<SpanData> result = new ArrayList<SpanData>();
        while (iterator.hasNext()) {
            IndexMetaGroup<String> group = iterator.next();
            DataFileReader reader = null;
            try {
                reader = new DataFileReader(group.getKey());
                result.addAll(reader.read(group.getMetaInfo()));
            } finally {
                if (reader != null) {
                    reader.close();
                }
            }
        }

        return result;
    }

    private static IndexMetaCollection fetchIndexMetaInfos(TraceId traceId) {
        IndexMetaCollection indexMetaCollection = new IndexMetaCollection();
        IndexOperator indexOperator = null;
        try {
            indexOperator = IndexOperatorFactory.getIndexOperatorFromPool();
            indexMetaCollection =
                    indexOperator.findIndex(traceId.getSegmentsList().toArray(new Long[traceId.getSegmentsCount()]));
        } finally {
            if (indexOperator != null)
                IndexOperatorFactory.returnIndexOperator(indexOperator);
        }
        return indexMetaCollection;
    }
}
