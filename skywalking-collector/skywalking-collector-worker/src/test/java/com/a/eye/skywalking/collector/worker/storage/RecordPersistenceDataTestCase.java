package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.worker.Const;
import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Test;

import java.util.Iterator;
import java.util.Map;

/**
 * @author pengys5
 */
public class RecordPersistenceDataTestCase {

    @Test
    public void testGetElseCreate() {
        String id = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";

        JsonObject record = new JsonObject();
        record.addProperty("Column_1", "Value_1");
        RecordPersistenceData recordPersistenceData = new RecordPersistenceData();
        RecordData recordData = recordPersistenceData.getElseCreate(id);
        recordData.setRecord(record);

        Assert.assertEquals(id, recordData.getId());

        RecordData recordData1 = recordPersistenceData.getElseCreate(id);
        Assert.assertEquals("Value_1", recordData1.getRecord().get("Column_1").getAsString());
    }

    @Test
    public void testClear() {
        String id_1 = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";
        String id_2 = "2016" + Const.ID_SPLIT + "B" + Const.ID_SPLIT + "C";
        RecordPersistenceData recordPersistenceData = new RecordPersistenceData();
        recordPersistenceData.getElseCreate(id_1);
        Assert.assertEquals(1, recordPersistenceData.size());
        recordPersistenceData.getElseCreate(id_2);
        Assert.assertEquals(2, recordPersistenceData.size());

        Assert.assertEquals(true, recordPersistenceData.hasNext());

        recordPersistenceData.clear();
        Assert.assertEquals(0, recordPersistenceData.size());

        Assert.assertEquals(false, recordPersistenceData.hasNext());
    }

    @Test
    public void testPushOne() {
        String id_1 = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";
        String id_2 = "2016" + Const.ID_SPLIT + "B" + Const.ID_SPLIT + "C";
        RecordPersistenceData recordPersistenceData = new RecordPersistenceData();
        JsonObject obj_1 = new JsonObject();
        obj_1.addProperty("Column_1", "Value_1");
        recordPersistenceData.getElseCreate(id_1).setRecord(obj_1);

        JsonObject obj_2 = new JsonObject();
        obj_2.addProperty("Column_2", "Value_2");
        recordPersistenceData.getElseCreate(id_2).setRecord(obj_2);

        RecordData recordData_2 = recordPersistenceData.pushOne();
        Assert.assertEquals("Value_2", recordData_2.getRecord().get("Column_2").getAsString());

        RecordData recordData_1 = recordPersistenceData.pushOne();
        Assert.assertEquals("Value_1", recordData_1.getRecord().get("Column_1").getAsString());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testForEach() {
        RecordPersistenceData recordPersistenceData = new RecordPersistenceData();
        recordPersistenceData.forEach(r -> System.out.println(r));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSpliterator() {
        RecordPersistenceData recordPersistenceData = new RecordPersistenceData();
        recordPersistenceData.spliterator();
    }

    @Test
    public void testIterator() {
        String id_1 = "2016" + Const.ID_SPLIT + "A" + Const.ID_SPLIT + "B";
        String id_2 = "2016" + Const.ID_SPLIT + "B" + Const.ID_SPLIT + "C";
        RecordPersistenceData recordPersistenceData = new RecordPersistenceData();
        JsonObject obj_1 = new JsonObject();
        obj_1.addProperty("Column_1", "Value_1");
        recordPersistenceData.getElseCreate(id_1).setRecord(obj_1);

        JsonObject obj_2 = new JsonObject();
        obj_2.addProperty("Column_2", "Value_2");
        recordPersistenceData.getElseCreate(id_2).setRecord(obj_2);

        Iterator<Map.Entry<String, RecordData>> iterator = recordPersistenceData.iterator();
        Assert.assertEquals("Value_2", iterator.next().getValue().getRecord().get("Column_2").getAsString());
        Assert.assertEquals("Value_1", iterator.next().getValue().getRecord().get("Column_1").getAsString());
    }
}
