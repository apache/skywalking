package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.worker.node.NodeIndex;
import com.a.eye.skywalking.collector.worker.nodeinst.NodeInstIndex;
import com.a.eye.skywalking.collector.worker.noderef.NodeRefIndex;
import com.a.eye.skywalking.collector.worker.noderef.NodeRefResSumIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentIndex;

/**
 * @author pengys5
 */
public enum IndexCreator {
    INSTANCE;

    public void create() {
        SegmentIndex segmentIndex = new SegmentIndex();
        segmentIndex.deleteIndex();

        NodeIndex nodeIndex = new NodeIndex();
        nodeIndex.deleteIndex();
        nodeIndex.createIndex();

        NodeInstIndex nodeInstIndex = new NodeInstIndex();
        nodeInstIndex.deleteIndex();
        nodeInstIndex.createIndex();

        NodeRefIndex nodeRefIndex = new NodeRefIndex();
        nodeRefIndex.deleteIndex();
        nodeRefIndex.createIndex();

        NodeRefResSumIndex nodeRefResSumIndex = new NodeRefResSumIndex();
        nodeRefResSumIndex.deleteIndex();
        nodeRefResSumIndex.createIndex();
    }
}
