package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.worker.Const;
import java.lang.reflect.Field;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class MetricPersistenceTestCase {

    @Test
    public void testGetElseCreate() {
        String id = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";

        MetricPersistenceData metricPersistenceData = new MetricPersistenceData();
        metricPersistenceData.hold();
        MetricData metricData = metricPersistenceData.getOrCreate(id);
        metricData.set("Column_1", 10L);
        Assert.assertEquals(id, metricData.getId());

        MetricData metricData1 = metricPersistenceData.getOrCreate(id);
        Assert.assertEquals(10L, metricData1.asMap().get("Column_1"));
    }

    @Test
    public void testSize() {
        String id = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";

        MetricPersistenceData metricPersistenceData = new MetricPersistenceData();
        metricPersistenceData.hold();
        metricPersistenceData.getOrCreate(id);

        Assert.assertEquals(1, metricPersistenceData.getCurrentAndHold().size());
        String id_1 = "2016" + Const.ID_SPLIT + "B" + Const.ID_SPLIT + "C";
        metricPersistenceData.getOrCreate(id_1);
        Assert.assertEquals(2, metricPersistenceData.getCurrentAndHold().size());

        metricPersistenceData.getCurrentAndHold().clear();
        Assert.assertEquals(0, metricPersistenceData.getCurrentAndHold().size());
    }

    @Test
    public void hold() throws NoSuchFieldException, IllegalAccessException {
        MetricPersistenceData persistenceData = new MetricPersistenceData();
        persistenceData.hold();

        Field testAField = persistenceData.getClass().getDeclaredField("lockedWindowData");
        testAField.setAccessible(true);
        WindowData<JoinAndSplitData> windowData = (WindowData<JoinAndSplitData>)testAField.get(persistenceData);
        Assert.assertEquals(true, windowData.isHolding());
    }

    @Test
    public void release() throws NoSuchFieldException, IllegalAccessException {
        MetricPersistenceData persistenceData = new MetricPersistenceData();
        persistenceData.hold();

        Field testAField = persistenceData.getClass().getDeclaredField("lockedWindowData");
        testAField.setAccessible(true);
        WindowData<JoinAndSplitData> windowData = (WindowData<JoinAndSplitData>)testAField.get(persistenceData);
        Assert.assertEquals(true, windowData.isHolding());

        persistenceData.release();
        Assert.assertEquals(false, windowData.isHolding());
    }
}
