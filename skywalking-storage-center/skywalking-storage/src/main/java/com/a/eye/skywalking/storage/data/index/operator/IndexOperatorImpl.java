package com.a.eye.skywalking.storage.data.index.operator;

import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.storage.data.index.IndexMetaCollection;
import com.a.eye.skywalking.storage.data.index.IndexMetaInfo;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.xcontent.XContentBuilder;

import java.io.IOException;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

public class IndexOperatorImpl implements IndexOperator {

    private static ILog logger = LogManager.getLogger(IndexOperatorImpl.class);

    private TransportClient client;

    public IndexOperatorImpl(TransportClient client) {
        this.client = client;
    }

    @Override
    public void batchUpdate(IndexMetaCollection metaInfos) {
        BulkRequestBuilder requestBuilder = client.prepareBulk();
        for (IndexMetaInfo indexMetaInfo : metaInfos) {
            try {
                requestBuilder.add(client.prepareIndex("skywalking", "index").setSource(buildSource(indexMetaInfo)));
            } catch (Exception e) {
                logger.error("Failed to update index.", e);
                HealthCollector.getCurrentHeathReading("IndexOperator")
                        .updateData(HeathReading.ERROR, "Failed to " + "update index.");
            }
        }

        BulkResponse bulkRequest = requestBuilder.get();
        if (bulkRequest.hasFailures()) {
            HealthCollector.getCurrentHeathReading("IndexOperator").updateData(HeathReading.ERROR,
                    "Failed to " + "update index. Error message : " + bulkRequest.buildFailureMessage());
        }
    }

    private XContentBuilder buildSource(IndexMetaInfo indexMetaInfo) throws IOException {
        XContentBuilder xContentBuilder = jsonBuilder().startObject().field("traceid_s0", indexMetaInfo.getTraceId()[0])
                .field("traceid_s1", indexMetaInfo.getTraceId()[1]).field("traceid_s2", indexMetaInfo.getTraceId()[2])
                .field("traceid_s3", indexMetaInfo.getTraceId()[3]).field("traceid_s4", indexMetaInfo.getTraceId()[4])
                .field("traceid_s5", indexMetaInfo.getTraceId()[5]).field("fileName", indexMetaInfo.getFileName())
                .field("offset", indexMetaInfo.getOffset()).field("length", indexMetaInfo.getLength()).endObject();
        return xContentBuilder;
    }

    @Override
    public IndexMetaCollection findIndex(Long[] traceId) {
        return null;
    }

}
