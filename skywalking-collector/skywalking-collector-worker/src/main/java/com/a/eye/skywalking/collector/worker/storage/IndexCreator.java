package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.worker.globaltrace.GlobalTraceIndex;
import com.a.eye.skywalking.collector.worker.node.NodeCompIndex;
import com.a.eye.skywalking.collector.worker.node.NodeMappingIndex;
import com.a.eye.skywalking.collector.worker.noderef.NodeRefIndex;
import com.a.eye.skywalking.collector.worker.noderef.NodeRefResSumIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentCostIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentExceptionIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentIndex;

/**
 * @author pengys5
 */
public enum IndexCreator {
    INSTANCE;

    public void create() {
        SegmentIndex segmentIndex = new SegmentIndex();
        segmentIndex.deleteIndex();

        GlobalTraceIndex globalTraceIndex = new GlobalTraceIndex();
        globalTraceIndex.deleteIndex();
        globalTraceIndex.createIndex();

        SegmentCostIndex segmentCostIndex = new SegmentCostIndex();
        segmentCostIndex.deleteIndex();
        segmentCostIndex.createIndex();

        SegmentExceptionIndex segmentExceptionIndex = new SegmentExceptionIndex();
        segmentExceptionIndex.deleteIndex();
        segmentExceptionIndex.createIndex();

        NodeCompIndex nodeCompIndex = new NodeCompIndex();
        nodeCompIndex.deleteIndex();
        nodeCompIndex.createIndex();

        NodeMappingIndex nodeMappingIndex = new NodeMappingIndex();
        nodeMappingIndex.deleteIndex();
        nodeMappingIndex.createIndex();

        NodeRefIndex nodeRefIndex = new NodeRefIndex();
        nodeRefIndex.deleteIndex();
        nodeRefIndex.createIndex();

        NodeRefResSumIndex nodeRefResSumIndex = new NodeRefResSumIndex();
        nodeRefResSumIndex.deleteIndex();
        nodeRefResSumIndex.createIndex();
    }
}
