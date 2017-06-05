package org.skywalking.apm.collector.worker.storage;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;

/**
 * @author pengys5
 */
public class JoinAndSplitPersistenceDataTestCase {

    @Test
    public void testGetElseCreate() {
        JoinAndSplitPersistenceData persistenceData = new JoinAndSplitPersistenceData();
        persistenceData.hold();
        JoinAndSplitData joinAndSplitData = persistenceData.getOrCreate("test");
        Assert.assertEquals("test", joinAndSplitData.getId());
    }

    @Test
    public void testSize() {
        JoinAndSplitPersistenceData persistenceData = new JoinAndSplitPersistenceData();
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
        JoinAndSplitPersistenceData persistenceData = new JoinAndSplitPersistenceData();
        persistenceData.hold();
        persistenceData.getOrCreate("test_1");
        Assert.assertEquals(1, persistenceData.getCurrentAndHold().size());
        persistenceData.getCurrentAndHold().clear();
        Assert.assertEquals(0, persistenceData.getCurrentAndHold().size());
    }

    @Test
    public void hold() throws NoSuchFieldException, IllegalAccessException {
        JoinAndSplitPersistenceData persistenceData = new JoinAndSplitPersistenceData();
        persistenceData.hold();

        Field testAField = persistenceData.getClass().getDeclaredField("lockedWindowData");
        testAField.setAccessible(true);
        WindowData<JoinAndSplitData> windowData = (WindowData<JoinAndSplitData>) testAField.get(persistenceData);
        Assert.assertEquals(true, windowData.isHolding());
    }

    @Test
    public void release() throws NoSuchFieldException, IllegalAccessException {
        JoinAndSplitPersistenceData persistenceData = new JoinAndSplitPersistenceData();
        persistenceData.hold();

        Field testAField = persistenceData.getClass().getDeclaredField("lockedWindowData");
        testAField.setAccessible(true);
        WindowData<JoinAndSplitData> windowData = (WindowData<JoinAndSplitData>) testAField.get(persistenceData);
        Assert.assertEquals(true, windowData.isHolding());

        persistenceData.release();
        Assert.assertEquals(false, windowData.isHolding());
    }
}
