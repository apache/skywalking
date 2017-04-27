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
public class NodeMappingHourSaveTestCase {

    private NodeMappingHourSave save;

    @Before
    public void init() {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        ClusterWorkerContext cluster = new ClusterWorkerContext(null);
        LocalWorkerContext local = new LocalWorkerContext();
        save = new NodeMappingHourSave(NodeMappingHourSave.Role.INSTANCE, cluster, local);
    }

    @Test
    public void testEsIndex() {
        Assert.assertEquals(NodeMappingIndex.INDEX, save.esIndex());
    }

    @Test
    public void testEsType() {
        Assert.assertEquals(NodeMappingIndex.TYPE_HOUR, save.esType());
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeMappingHourSave.class.getSimpleName(), NodeMappingHourSave.Role.INSTANCE.roleName());
        Assert.assertEquals(HashCodeSelector.class.getSimpleName(), NodeMappingHourSave.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        NodeMappingHourSave.Factory factory = new NodeMappingHourSave.Factory();
        Assert.assertEquals(NodeMappingHourSave.class.getSimpleName(), factory.role().roleName());
        Assert.assertEquals(NodeMappingHourSave.class.getSimpleName(), factory.workerInstance(null).getClass().getSimpleName());
    }
}
