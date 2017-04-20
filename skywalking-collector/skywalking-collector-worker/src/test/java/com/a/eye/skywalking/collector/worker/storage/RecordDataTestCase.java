package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.worker.Const;
import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class RecordDataTestCase {

    @Test
    public void testConstruction() {
        String id_1 = "2017" + Const.ID_SPLIT + "B" + Const.ID_SPLIT + "C";
        RecordData recordData = new RecordData(id_1);

        Assert.assertEquals(id_1, recordData.getId());
        Assert.assertEquals("B" + Const.ID_SPLIT + "C", recordData.getRecord().get("aggId").getAsString());
    }

    @Test
    public void testSetRecord() {
        String id_1 = "2017" + Const.ID_SPLIT + "B" + Const.ID_SPLIT + "C";
        RecordData recordData = new RecordData(id_1);

        JsonObject record = new JsonObject();
        record.addProperty("Column", "VALUE");
        recordData.setRecord(record);

        Assert.assertEquals(id_1, recordData.getId());
        Assert.assertEquals("B" + Const.ID_SPLIT + "C", recordData.getRecord().get("aggId").getAsString());
        Assert.assertEquals("VALUE", recordData.getRecord().get("Column").getAsString());
    }
}
