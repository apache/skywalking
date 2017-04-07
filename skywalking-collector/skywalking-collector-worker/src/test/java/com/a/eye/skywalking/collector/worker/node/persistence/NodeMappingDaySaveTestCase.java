package com.a.eye.skywalking.collector.worker.node.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.node.NodeMappingIndex;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author pengys5
 */
public class NodeMappingDaySaveTestCase {

    private NodeMappingDaySave save;

    @Before
    public void init() {
        ClusterWorkerContext cluster = new ClusterWorkerContext(null);
        LocalWorkerContext local = new LocalWorkerContext();
        save = new NodeMappingDaySave(NodeMappingDaySave.Role.INSTANCE, cluster, local);
    }

    @Test
    public void testEsIndex() {
        Assert.assertEquals(NodeMappingIndex.Index, save.esIndex());
    }

    @Test
    public void testEsType() {
        Assert.assertEquals(NodeMappingIndex.Type_Day, save.esType());
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeMappingDaySave.class.getSimpleName(), NodeMappingDaySave.Role.INSTANCE.roleName());
        Assert.assertEquals(HashCodeSelector.class.getSimpleName(), NodeMappingDaySave.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(NodeMappingDaySave.class.getSimpleName(), NodeMappingDaySave.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(NodeMappingDaySave.class.getSimpleName(), NodeMappingDaySave.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.Queue.Node.NodeMappingDaySave.Size = testSize;
        Assert.assertEquals(testSize, NodeMappingDaySave.Factory.INSTANCE.queueSize());
    }
}
