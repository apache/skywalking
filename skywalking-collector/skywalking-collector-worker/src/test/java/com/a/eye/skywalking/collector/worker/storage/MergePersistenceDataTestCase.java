package com.a.eye.skywalking.collector.worker.storage;

import java.lang.reflect.Field;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author pengys5
 */
public class MergePersistenceDataTestCase {

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

    @Test
    public void hold() throws NoSuchFieldException, IllegalAccessException {
        MergePersistenceData persistenceData = new MergePersistenceData();
        persistenceData.hold();

        Field testAField = persistenceData.getClass().getDeclaredField("lockedWindowData");
        testAField.setAccessible(true);
        WindowData<MergeData> windowData = (WindowData<MergeData>)testAField.get(persistenceData);
        Assert.assertEquals(true, windowData.isHolding());
    }

    @Test
    public void release() throws NoSuchFieldException, IllegalAccessException {
        MergePersistenceData persistenceData = new MergePersistenceData();
        persistenceData.hold();

        Field testAField = persistenceData.getClass().getDeclaredField("lockedWindowData");
        testAField.setAccessible(true);
        WindowData<MergeData> windowData = (WindowData<MergeData>)testAField.get(persistenceData);
        Assert.assertEquals(true, windowData.isHolding());

        persistenceData.release();
        Assert.assertEquals(false, windowData.isHolding());
    }
}
