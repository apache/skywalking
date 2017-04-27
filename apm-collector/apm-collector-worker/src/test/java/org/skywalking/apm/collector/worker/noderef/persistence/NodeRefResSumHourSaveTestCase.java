package org.skywalking.apm.collector.worker.noderef.persistence;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.selector.HashCodeSelector;
import org.skywalking.apm.collector.worker.noderef.NodeRefResSumIndex;

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
        Assert.assertEquals(NodeRefResSumIndex.INDEX, save.esIndex());
    }

    @Test
    public void testEsType() {
        Assert.assertEquals(NodeRefResSumIndex.TYPE_HOUR, save.esType());
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeRefResSumHourSave.class.getSimpleName(), NodeRefResSumHourSave.Role.INSTANCE.roleName());
        Assert.assertEquals(HashCodeSelector.class.getSimpleName(), NodeRefResSumHourSave.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        NodeRefResSumHourSave.Factory factory = new NodeRefResSumHourSave.Factory();
        Assert.assertEquals(NodeRefResSumHourSave.class.getSimpleName(), factory.role().roleName());
        Assert.assertEquals(NodeRefResSumHourSave.class.getSimpleName(), factory.workerInstance(null).getClass().getSimpleName());
    }
}
