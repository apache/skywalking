package com.a.eye.skywalking.collector.worker.noderef.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.worker.noderef.NodeRefResSumIndex;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.TimeZone;

/**
 * @author pengys5
 */
public class NodeRefResSumDaySaveTestCase {
    private NodeRefResSumDaySave save;

    @Before
    public void init() {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        ClusterWorkerContext cluster = new ClusterWorkerContext(null);
        LocalWorkerContext local = new LocalWorkerContext();
        save = new NodeRefResSumDaySave(NodeRefResSumDaySave.Role.INSTANCE, cluster, local);
    }

    @Test
    public void testEsIndex() {
        Assert.assertEquals(NodeRefResSumIndex.INDEX, save.esIndex());
    }

    @Test
    public void testEsType() {
        Assert.assertEquals(NodeRefResSumIndex.TYPE_DAY, save.esType());
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeRefResSumDaySave.class.getSimpleName(), NodeRefResSumDaySave.Role.INSTANCE.roleName());
        Assert.assertEquals(HashCodeSelector.class.getSimpleName(), NodeRefResSumDaySave.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        NodeRefResSumDaySave.Factory factory = new NodeRefResSumDaySave.Factory();
        Assert.assertEquals(NodeRefResSumDaySave.class.getSimpleName(), factory.role().roleName());
        Assert.assertEquals(NodeRefResSumDaySave.class.getSimpleName(), factory.workerInstance(null).getClass().getSimpleName());
    }
}
