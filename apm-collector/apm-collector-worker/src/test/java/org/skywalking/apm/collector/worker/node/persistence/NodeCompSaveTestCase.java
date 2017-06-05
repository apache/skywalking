package org.skywalking.apm.collector.worker.node.persistence;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.selector.HashCodeSelector;
import org.skywalking.apm.collector.worker.node.NodeCompIndex;

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
        NodeCompSave.Factory factory = new NodeCompSave.Factory();
        Assert.assertEquals(NodeCompSave.class.getSimpleName(), factory.role().roleName());
        Assert.assertEquals(NodeCompSave.class.getSimpleName(), factory.workerInstance(null).getClass().getSimpleName());
    }
}
