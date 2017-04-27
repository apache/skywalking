package org.skywalking.apm.collector.worker.noderef.persistence;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.selector.HashCodeSelector;
import org.skywalking.apm.collector.worker.noderef.NodeRefIndex;

import java.util.TimeZone;

/**
 * @author pengys5
 */
public class NodeRefHourSaveTestCase {

    private NodeRefHourSave save;

    @Before
    public void init() {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        ClusterWorkerContext cluster = new ClusterWorkerContext(null);
        LocalWorkerContext local = new LocalWorkerContext();
        save = new NodeRefHourSave(NodeRefHourSave.Role.INSTANCE, cluster, local);
    }

    @Test
    public void testEsIndex() {
        Assert.assertEquals(NodeRefIndex.INDEX, save.esIndex());
    }

    @Test
    public void testEsType() {
        Assert.assertEquals(NodeRefIndex.TYPE_HOUR, save.esType());
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeRefHourSave.class.getSimpleName(), NodeRefHourSave.Role.INSTANCE.roleName());
        Assert.assertEquals(HashCodeSelector.class.getSimpleName(), NodeRefHourSave.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        NodeRefHourSave.Factory factory = new NodeRefHourSave.Factory();
        Assert.assertEquals(NodeRefHourSave.class.getSimpleName(), factory.role().roleName());
        Assert.assertEquals(NodeRefHourSave.class.getSimpleName(), factory.workerInstance(null).getClass().getSimpleName());
    }
}
