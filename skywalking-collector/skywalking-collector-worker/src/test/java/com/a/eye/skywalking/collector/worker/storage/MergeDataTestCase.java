package com.a.eye.skywalking.collector.worker.storage;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class MergeDataTestCase {

    @Test
    public void testConstruction() {
        String id = "Test";
        MergeData mergeData = new MergeData(id);

        Assert.assertEquals(id, mergeData.getId());
    }

    @Test
    public void testSetMergeData() {
        String id = "Test";
        MergeData mergeData = new MergeData(id);

        mergeData.setMergeData("Column_1", "Value_1");
        Assert.assertEquals("Value_1", mergeData.toMap().get("Column_1"));
        mergeData.setMergeData("Column_1", "Value_1");
        Assert.assertEquals("Value_1", mergeData.toMap().get("Column_1"));

        mergeData.setMergeData("Column_1", "Value_2");
        Assert.assertEquals("Value_2,Value_1", mergeData.toMap().get("Column_1"));

        mergeData.setMergeData("Column_2", "Value_3");
        Assert.assertEquals("Value_3", mergeData.toMap().get("Column_2"));
    }

    @Test
    public void testMerge() {
        String id = "Test";
        MergeData mergeData_1 = new MergeData(id);
        mergeData_1.setMergeData("Column_1", "Value_1");

        MergeData mergeData_2 = new MergeData(id);
        mergeData_2.setMergeData("Column_1", "Value_2");

        mergeData_1.merge(mergeData_2);
        Assert.assertEquals("Value_2,Value_1", mergeData_1.toMap().get("Column_1"));
    }

    @Test
    public void testMergeMap() {
        String id = "Test";
        MergeData mergeData_1 = new MergeData(id);
        mergeData_1.setMergeData("Column_1", "Value_1");

        Map<String, Object> dbData = new HashMap<>();
        dbData.put("Column_1", "Value_2");

        mergeData_1.merge(dbData);
        Assert.assertEquals("Value_2,Value_1", mergeData_1.toMap().get("Column_1"));
    }
}
