package com.a.eye.skywalking.collector.worker.storage;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author pengys5
 */
public class MergePersistenceWindowDataTestCase {

    @Test
    public void testGetElseCreate() {
        MergePersistenceData persistenceData = new MergePersistenceData();
        persistenceData.hold();
        MergeData mergeData = persistenceData.getOrCreate("test");
        Assert.assertEquals("test", mergeData.getId());
    }

    @Test
    public void testSize() {
        MergePersistenceData persistenceData = new MergePersistenceData();
        persistenceData.hold();
        persistenceData.getOrCreate("test_1");
        Assert.assertEquals(1, persistenceData.getCurrentAndHold().size());
        persistenceData.getOrCreate("test_1");
        Assert.assertEquals(1, persistenceData.getCurrentAndHold().size());
        persistenceData.getOrCreate("test_2");
        Assert.assertEquals(2, persistenceData.getCurrentAndHold().size());
    }

    @Test
    public void testClear() {
        MergePersistenceData persistenceData = new MergePersistenceData();
        persistenceData.hold();
        persistenceData.getOrCreate("test_1");
        Assert.assertEquals(1, persistenceData.getCurrentAndHold().size());
        persistenceData.getCurrentAndHold().clear();
        Assert.assertEquals(0, persistenceData.getCurrentAndHold().size());
    }
}
