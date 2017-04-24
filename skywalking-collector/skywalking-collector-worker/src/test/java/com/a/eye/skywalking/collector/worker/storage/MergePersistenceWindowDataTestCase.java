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
        persistenceData.holdData();
        MergeData mergeData = persistenceData.getElseCreate("test");
        Assert.assertEquals("test", mergeData.getId());
    }

    @Test
    public void testSize() {
        MergePersistenceData persistenceData = new MergePersistenceData();
        persistenceData.holdData();
        persistenceData.getElseCreate("test_1");
        Assert.assertEquals(1, persistenceData.getCurrentAndHold().size());
        persistenceData.getElseCreate("test_1");
        Assert.assertEquals(1, persistenceData.getCurrentAndHold().size());
        persistenceData.getElseCreate("test_2");
        Assert.assertEquals(2, persistenceData.getCurrentAndHold().size());
    }

    @Test
    public void testClear() {
        MergePersistenceData persistenceData = new MergePersistenceData();
        persistenceData.holdData();
        persistenceData.getElseCreate("test_1");
        Assert.assertEquals(1, persistenceData.getCurrentAndHold().size());
        persistenceData.getCurrentAndHold().clear();
        Assert.assertEquals(0, persistenceData.getCurrentAndHold().size());
    }
}
