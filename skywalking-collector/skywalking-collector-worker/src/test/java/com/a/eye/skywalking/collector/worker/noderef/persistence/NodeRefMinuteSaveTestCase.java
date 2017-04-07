package com.a.eye.skywalking.collector.worker.noderef.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.noderef.NodeRefIndex;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.TimeZone;

/**
 * @author pengys5
 */
public class NodeRefMinuteSaveTestCase {

    private NodeRefMinuteSave save;

    @Before
    public void init() {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        ClusterWorkerContext cluster = new ClusterWorkerContext(null);
        LocalWorkerContext local = new LocalWorkerContext();
        save = new NodeRefMinuteSave(NodeRefMinuteSave.Role.INSTANCE, cluster, local);
    }

    @Test
    public void testEsIndex() {
        Assert.assertEquals(NodeRefIndex.Index, save.esIndex());
    }

    @Test
    public void testEsType() {
        Assert.assertEquals(NodeRefIndex.Type_Minute, save.esType());
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeRefMinuteSave.class.getSimpleName(), NodeRefMinuteSave.Role.INSTANCE.roleName());
        Assert.assertEquals(HashCodeSelector.class.getSimpleName(), NodeRefMinuteSave.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(NodeRefMinuteSave.class.getSimpleName(), NodeRefMinuteSave.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(NodeRefMinuteSave.class.getSimpleName(), NodeRefMinuteSave.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.Queue.NodeRef.NodeRefMinuteSave.Size = testSize;
        Assert.assertEquals(testSize, NodeRefMinuteSave.Factory.INSTANCE.queueSize());
    }
}
