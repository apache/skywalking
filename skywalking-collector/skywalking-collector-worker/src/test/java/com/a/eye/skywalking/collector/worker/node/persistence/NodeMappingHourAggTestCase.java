package com.a.eye.skywalking.collector.worker.node.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.WorkerRefs;
import com.a.eye.skywalking.collector.actor.selector.HashCodeSelector;
import com.a.eye.skywalking.collector.worker.Const;
import com.a.eye.skywalking.collector.worker.WorkerConfig;
import com.a.eye.skywalking.collector.worker.mock.RecordDataAnswer;
import com.a.eye.skywalking.collector.worker.storage.RecordData;
import com.a.eye.skywalking.collector.worker.tools.RecordDataTool;
import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.List;

import static org.mockito.Mockito.*;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({LocalWorkerContext.class})
@PowerMockIgnore({"javax.management.*"})
public class NodeMappingHourAggTestCase {

    private NodeMappingHourAgg nodeMappingHourAgg;
    private RecordDataAnswer recordDataAnswer;

    @Before
    public void init() throws Exception {
        ClusterWorkerContext clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);

        LocalWorkerContext localWorkerContext = PowerMockito.mock(LocalWorkerContext.class);
        WorkerRefs workerRefs = mock(WorkerRefs.class);

        recordDataAnswer = new RecordDataAnswer();
        doAnswer(recordDataAnswer).when(workerRefs).tell(Mockito.any(RecordData.class));

        when(localWorkerContext.lookup(NodeMappingHourSave.Role.INSTANCE)).thenReturn(workerRefs);
        nodeMappingHourAgg = new NodeMappingHourAgg(NodeMappingHourAgg.Role.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeMappingHourAgg.class.getSimpleName(), NodeMappingHourAgg.Role.INSTANCE.roleName());
        Assert.assertEquals(HashCodeSelector.class.getSimpleName(), NodeMappingHourAgg.Role.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(NodeMappingHourAgg.class.getSimpleName(), NodeMappingHourAgg.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(NodeMappingHourAgg.class.getSimpleName(), NodeMappingHourAgg.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());

        int testSize = 10;
        WorkerConfig.WorkerNum.Node.NodeMappingHourAgg.Value = testSize;
        Assert.assertEquals(testSize, NodeMappingHourAgg.Factory.INSTANCE.workerNum());
    }

    @Test
    public void testOnWork() throws Exception {
        String id = "2017" + Const.ID_SPLIT + "TestNodeMappingHourAgg";
        JsonObject record = new JsonObject();
        record.addProperty("Column", "TestData");

        RecordData recordData = new RecordData(id);
        recordData.setRecord(record);
        nodeMappingHourAgg.onWork(recordData);

        List<RecordData> recordDataList = recordDataAnswer.recordObj.getRecordData();
        RecordData data = RecordDataTool.INSTANCE.getRecord(recordDataList, id);
        Assert.assertEquals("TestNodeMappingHourAgg", data.getRecord().get("aggId").getAsString());
        Assert.assertEquals("TestData", data.getRecord().get("Column").getAsString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnWorkError() throws Exception {
        nodeMappingHourAgg.onWork(new Object());
    }
}
