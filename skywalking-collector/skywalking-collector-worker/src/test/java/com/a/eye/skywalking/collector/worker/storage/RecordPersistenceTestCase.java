package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.worker.Const;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class RecordPersistenceTestCase {

    @Test
    public void testGetElseCreate() {
        String id = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";

        JsonObject record = new JsonObject();
        record.addProperty("Column_1", "Value_1");
        RecordPersistenceData recordPersistenceData = new RecordPersistenceData();
        recordPersistenceData.hold();

        RecordData recordData = recordPersistenceData.getOrCreate(id);
        recordData.setRecord(record);

        Assert.assertEquals(id, recordData.getId());

        RecordData recordData1 = recordPersistenceData.getOrCreate(id);
        Assert.assertEquals("Value_1", recordData1.getRecord().get("Column_1").getAsString());
    }

    @Test
    public void testClear() {
        String id_1 = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";
        String id_2 = "2016" + Const.ID_SPLIT + "B" + Const.ID_SPLIT + "C";
        RecordPersistenceData recordPersistenceData = new RecordPersistenceData();
        recordPersistenceData.hold();

        recordPersistenceData.getOrCreate(id_1);
        Assert.assertEquals(1, recordPersistenceData.getCurrentAndHold().size());
        recordPersistenceData.getOrCreate(id_2);
        Assert.assertEquals(2, recordPersistenceData.getCurrentAndHold().size());

        recordPersistenceData.getCurrentAndHold().clear();
        Assert.assertEquals(0, recordPersistenceData.getCurrentAndHold().size());
    }

    @Test
    public void hold() throws NoSuchFieldException, IllegalAccessException {
        RecordPersistenceData persistenceData = new RecordPersistenceData();
        persistenceData.hold();

        Field testAField = persistenceData.getClass().getDeclaredField("lockedWindowData");
        testAField.setAccessible(true);
        WindowData<MergeData> windowData = (WindowData<MergeData>)testAField.get(persistenceData);
        Assert.assertEquals(true, windowData.isHolding());
    }

    @Test
    public void release() throws NoSuchFieldException, IllegalAccessException {
        RecordPersistenceData persistenceData = new RecordPersistenceData();
        persistenceData.hold();

        Field testAField = persistenceData.getClass().getDeclaredField("lockedWindowData");
        testAField.setAccessible(true);
        WindowData<MergeData> windowData = (WindowData<MergeData>)testAField.get(persistenceData);
        Assert.assertEquals(true, windowData.isHolding());

        persistenceData.release();
        Assert.assertEquals(false, windowData.isHolding());
    }
}
