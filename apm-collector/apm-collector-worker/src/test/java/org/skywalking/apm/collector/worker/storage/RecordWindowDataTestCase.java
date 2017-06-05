package org.skywalking.apm.collector.worker.storage;

import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.collector.worker.Const;

/**
 * @author pengys5
 */
public class RecordWindowDataTestCase {

    @Test
    public void testConstruction() {
        String id_1 = "2017" + Const.ID_SPLIT + "B" + Const.ID_SPLIT + "C";
        RecordData recordData = new RecordData(id_1);

        Assert.assertEquals(id_1, recordData.getId());
        Assert.assertEquals("B" + Const.ID_SPLIT + "C", recordData.get().get("aggId").getAsString());
    }

    @Test
    public void testSetRecord() {
        String id_1 = "2017" + Const.ID_SPLIT + "B" + Const.ID_SPLIT + "C";
        RecordData recordData = new RecordData(id_1);

        JsonObject record = new JsonObject();
        record.addProperty("Column", "VALUE");
        recordData.set(record);

        Assert.assertEquals(id_1, recordData.getId());
        Assert.assertEquals("B" + Const.ID_SPLIT + "C", recordData.get().get("aggId").getAsString());
        Assert.assertEquals("VALUE", recordData.get().get("Column").getAsString());
    }
}
