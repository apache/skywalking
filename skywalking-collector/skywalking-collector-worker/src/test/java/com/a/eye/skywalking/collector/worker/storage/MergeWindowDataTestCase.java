package com.a.eye.skywalking.collector.worker.storage;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class MergeWindowDataTestCase {

    @Test
    public void testConstruction() {
        String id = "Test";
        JoinAndSplitData joinAndSplitData = new JoinAndSplitData(id);

        Assert.assertEquals(id, joinAndSplitData.getId());
    }

    @Test
    public void testSetMergeData() {
        String id = "Test";
        JoinAndSplitData joinAndSplitData = new JoinAndSplitData(id);

        joinAndSplitData.set("Column_1", "Value_1");
        Assert.assertEquals("Value_1", joinAndSplitData.asMap().get("Column_1"));
        joinAndSplitData.set("Column_1", "Value_1");
        Assert.assertEquals("Value_1", joinAndSplitData.asMap().get("Column_1"));

        joinAndSplitData.set("Column_1", "Value_2");
        Assert.assertEquals("Value_2,Value_1", joinAndSplitData.asMap().get("Column_1"));

        joinAndSplitData.set("Column_2", "Value_3");
        Assert.assertEquals("Value_3", joinAndSplitData.asMap().get("Column_2"));
    }

    @Test
    public void testMerge() {
        String id = "Test";
        JoinAndSplitData joinAndSplitData_1 = new JoinAndSplitData(id);
        joinAndSplitData_1.set("Column_1", "Value_1");

        JoinAndSplitData joinAndSplitData_2 = new JoinAndSplitData(id);
        joinAndSplitData_2.set("Column_1", "Value_2");

        joinAndSplitData_1.merge(joinAndSplitData_2);
        Assert.assertEquals("Value_2,Value_1", joinAndSplitData_1.asMap().get("Column_1"));
    }

    @Test
    public void testMergeMap() {
        String id = "Test";
        JoinAndSplitData joinAndSplitData_1 = new JoinAndSplitData(id);
        joinAndSplitData_1.set("Column_1", "Value_1");

        Map<String, Object> dbData = new HashMap<>();
        dbData.put("Column_1", "Value_2");

        joinAndSplitData_1.merge(dbData);
        Assert.assertEquals("Value_2,Value_1", joinAndSplitData_1.asMap().get("Column_1"));
    }
}
