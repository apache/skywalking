package com.a.eye.skywalking.collector.worker.node.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.node.NodeMappingIndex;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.TimeZone;

/**
 * @author pengys5
 */
public class NodeMappingMinuteSaveTestCase {

    private NodeMappingMinuteSave save;

    @Before
    public void init() {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        
        ClusterWorkerContext cluster = new ClusterWorkerContext(null);
        LocalWorkerContext local = new LocalWorkerContext();
        save = new NodeMappingMinuteSave(NodeMappingMinuteSave.Role.INSTANCE, cluster, local);
    }

    @Test
    public void testEsIndex() {
        Assert.assertEquals(NodeMappingIndex.Index, save.esIndex());
    }

    @Test
    public void testEsType() {
        Assert.assertEquals(NodeMappingIndex.Type_Minute, save.esType());
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeMappingMinuteSave.class.getSimpleName(), NodeMappingMinuteSave.Role.INSTANCE.roleName());
        Assert.assertEquals(HashCodeSelector.class.getSimpleName(), NodeMappingMinuteSave.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(NodeMappingMinuteSave.class.getSimpleName(), NodeMappingMinuteSave.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(NodeMappingMinuteSave.class.getSimpleName(), NodeMappingMinuteSave.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.Queue.Node.NodeMappingMinuteSave.Size = testSize;
        Assert.assertEquals(testSize, NodeMappingMinuteSave.Factory.INSTANCE.queueSize());
    }
}
