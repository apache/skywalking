package org.skywalking.apm.collector.worker.node.persistence;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.selector.HashCodeSelector;
import org.skywalking.apm.collector.worker.node.NodeMappingIndex;

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
        Assert.assertEquals(NodeMappingIndex.INDEX, save.esIndex());
    }

    @Test
    public void testEsType() {
        Assert.assertEquals(NodeMappingIndex.TYPE_MINUTE, save.esType());
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeMappingMinuteSave.class.getSimpleName(), NodeMappingMinuteSave.Role.INSTANCE.roleName());
        Assert.assertEquals(HashCodeSelector.class.getSimpleName(), NodeMappingMinuteSave.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        NodeMappingMinuteSave.Factory factory = new NodeMappingMinuteSave.Factory();
        Assert.assertEquals(NodeMappingMinuteSave.class.getSimpleName(), factory.role().roleName());
        Assert.assertEquals(NodeMappingMinuteSave.class.getSimpleName(), factory.workerInstance(null).getClass().getSimpleName());
    }
}
