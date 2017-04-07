package com.a.eye.skywalking.collector.worker.noderef.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.noderef.NodeRefResSumIndex;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.TimeZone;

/**
 * @author pengys5
 */
public class NodeRefResSumHourSaveTestCase {

    private NodeRefResSumHourSave save;

    @Before
    public void init() {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        ClusterWorkerContext cluster = new ClusterWorkerContext(null);
        LocalWorkerContext local = new LocalWorkerContext();
        save = new NodeRefResSumHourSave(NodeRefResSumHourSave.Role.INSTANCE, cluster, local);
    }

    @Test
    public void testEsIndex() {
        Assert.assertEquals(NodeRefResSumIndex.Index, save.esIndex());
    }

    @Test
    public void testEsType() {
        Assert.assertEquals(NodeRefResSumIndex.Type_Hour, save.esType());
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeRefResSumHourSave.class.getSimpleName(), NodeRefResSumHourSave.Role.INSTANCE.roleName());
        Assert.assertEquals(HashCodeSelector.class.getSimpleName(), NodeRefResSumHourSave.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(NodeRefResSumHourSave.class.getSimpleName(), NodeRefResSumHourSave.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(NodeRefResSumHourSave.class.getSimpleName(), NodeRefResSumHourSave.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.Queue.NodeRef.NodeRefResSumHourSave.Size = testSize;
        Assert.assertEquals(testSize, NodeRefResSumHourSave.Factory.INSTANCE.queueSize());
    }
}
