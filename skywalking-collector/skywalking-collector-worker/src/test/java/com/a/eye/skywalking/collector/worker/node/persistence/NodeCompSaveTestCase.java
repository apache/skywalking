package com.a.eye.skywalking.collector.worker.node.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.worker.config.WorkerConfig;
import com.a.eye.skywalking.collector.worker.node.NodeCompIndex;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.TimeZone;

/**
 * @author pengys5
 */
public class NodeCompSaveTestCase {

    private NodeCompSave save;

    @Before
    public void init() {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        ClusterWorkerContext cluster = new ClusterWorkerContext(null);
        LocalWorkerContext local = new LocalWorkerContext();
        save = new NodeCompSave(NodeCompSave.Role.INSTANCE, cluster, local);
    }

    @Test
    public void testEsIndex() {
        Assert.assertEquals(NodeCompIndex.INDEX, save.esIndex());
    }

    @Test
    public void testEsType() {
        Assert.assertEquals(NodeCompIndex.TYPE_RECORD, save.esType());
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeCompSave.class.getSimpleName(), NodeCompSave.Role.INSTANCE.roleName());
        Assert.assertEquals(HashCodeSelector.class.getSimpleName(), NodeCompSave.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(NodeCompSave.class.getSimpleName(), NodeCompSave.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(NodeCompSave.class.getSimpleName(), NodeCompSave.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.Queue.Node.NodeCompSave.SIZE = testSize;
        Assert.assertEquals(testSize, NodeCompSave.Factory.INSTANCE.queueSize());
    }
}
