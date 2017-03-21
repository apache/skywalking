package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.worker.node.NodeIndex;
import com.a.eye.skywalking.collector.worker.nodeinst.NodeInstIndex;
import com.a.eye.skywalking.collector.worker.noderef.NodeRefIndex;

/**
 * @author pengys5
 */
public enum IndexCreator {
    INSTANCE;

    public void create() {
        NodeIndex nodeIndex = new NodeIndex();
        nodeIndex.deleteIndex();
        nodeIndex.createIndex();

        NodeInstIndex nodeInstIndex = new NodeInstIndex();
        nodeInstIndex.deleteIndex();
        nodeInstIndex.createIndex();

        NodeRefIndex nodeRefIndex = new NodeRefIndex();
        nodeRefIndex.deleteIndex();
        nodeRefIndex.createIndex();
    }
}
