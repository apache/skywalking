package com.a.eye.skywalking.collector.worker.noderef.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.noderef.NodeRefResSumIndex;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author pengys5
 */
public class NodeRefResSumDaySaveTestCase {
    private NodeRefResSumDaySave save;

    @Before
    public void init() {
        ClusterWorkerContext cluster = new ClusterWorkerContext(null);
        LocalWorkerContext local = new LocalWorkerContext();
        save = new NodeRefResSumDaySave(NodeRefResSumDaySave.Role.INSTANCE, cluster, local);
    }

    @Test
    public void testEsIndex() {
        Assert.assertEquals(NodeRefResSumIndex.Index, save.esIndex());
    }

    @Test
    public void testEsType() {
        Assert.assertEquals(NodeRefResSumIndex.Type_Day, save.esType());
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeRefResSumDaySave.class.getSimpleName(), NodeRefResSumDaySave.Role.INSTANCE.roleName());
        Assert.assertEquals(HashCodeSelector.class.getSimpleName(), NodeRefResSumDaySave.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(NodeRefResSumDaySave.class.getSimpleName(), NodeRefResSumDaySave.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(NodeRefResSumDaySave.class.getSimpleName(), NodeRefResSumDaySave.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.Queue.NodeRef.NodeRefResSumDaySave.Size = testSize;
        Assert.assertEquals(testSize, NodeRefResSumDaySave.Factory.INSTANCE.queueSize());
    }
}
