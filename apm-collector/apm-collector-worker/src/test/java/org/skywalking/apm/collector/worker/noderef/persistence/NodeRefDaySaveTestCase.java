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
public class NodeRefDaySaveTestCase {

    private NodeRefDaySave save;

    @Before
    public void init() {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        ClusterWorkerContext cluster = new ClusterWorkerContext(null);
        LocalWorkerContext local = new LocalWorkerContext();
        save = new NodeRefDaySave(NodeRefDaySave.Role.INSTANCE, cluster, local);
    }

    @Test
    public void testEsIndex() {
        Assert.assertEquals(NodeRefIndex.INDEX, save.esIndex());
    }

    @Test
    public void testEsType() {
        Assert.assertEquals(NodeRefIndex.TYPE_DAY, save.esType());
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeRefDaySave.class.getSimpleName(), NodeRefDaySave.Role.INSTANCE.roleName());
        Assert.assertEquals(HashCodeSelector.class.getSimpleName(), NodeRefDaySave.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        NodeRefDaySave.Factory factory = new NodeRefDaySave.Factory();
        Assert.assertEquals(NodeRefDaySave.class.getSimpleName(), factory.role().roleName());
        Assert.assertEquals(NodeRefDaySave.class.getSimpleName(), factory.workerInstance(null).getClass().getSimpleName());
    }
}
