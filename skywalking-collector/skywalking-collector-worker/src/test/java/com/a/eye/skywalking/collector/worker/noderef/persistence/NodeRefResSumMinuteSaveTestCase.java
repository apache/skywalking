package com.a.eye.skywalking.collector.worker.noderef.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.worker.config.WorkerConfig;
import com.a.eye.skywalking.collector.worker.noderef.NodeRefResSumIndex;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.TimeZone;

/**
 * @author pengys5
 */
public class NodeRefResSumMinuteSaveTestCase {

    private NodeRefResSumMinuteSave save;

    @Before
    public void init() {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        ClusterWorkerContext cluster = new ClusterWorkerContext(null);
        LocalWorkerContext local = new LocalWorkerContext();
        save = new NodeRefResSumMinuteSave(NodeRefResSumMinuteSave.Role.INSTANCE, cluster, local);
    }

    @Test
    public void testEsIndex() {
        Assert.assertEquals(NodeRefResSumIndex.Index, save.esIndex());
    }

    @Test
    public void testEsType() {
        Assert.assertEquals(NodeRefResSumIndex.Type_Minute, save.esType());
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeRefResSumMinuteSave.class.getSimpleName(), NodeRefResSumMinuteSave.Role.INSTANCE.roleName());
        Assert.assertEquals(HashCodeSelector.class.getSimpleName(), NodeRefResSumMinuteSave.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(NodeRefResSumMinuteSave.class.getSimpleName(), NodeRefResSumMinuteSave.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(NodeRefResSumMinuteSave.class.getSimpleName(), NodeRefResSumMinuteSave.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.Queue.NodeRef.NodeRefResSumMinuteSave.Size = testSize;
        Assert.assertEquals(testSize, NodeRefResSumMinuteSave.Factory.INSTANCE.queueSize());
    }
}
