package com.a.eye.skywalking.collector.worker.storage;

import org.junit.Assert;
import org.junit.Test;

import java.util.Iterator;
import java.util.Map;

/**
 * @author pengys5
 */
public class MergePersistenceDataTestCase {

    @Test
    public void testGetElseCreate() {
        MergePersistenceData persistenceData = new MergePersistenceData();
        MergeData mergeData = persistenceData.getElseCreate("test");
        Assert.assertEquals("test", mergeData.getId());
    }

    @Test
    public void testSize() {
        MergePersistenceData persistenceData = new MergePersistenceData();
        persistenceData.getElseCreate("test_1");
        Assert.assertEquals(1, persistenceData.size());
        persistenceData.getElseCreate("test_1");
        Assert.assertEquals(1, persistenceData.size());
        persistenceData.getElseCreate("test_2");
        Assert.assertEquals(2, persistenceData.size());
    }

    @Test
    public void testClear() {
        MergePersistenceData persistenceData = new MergePersistenceData();
        persistenceData.getElseCreate("test_1");
        Assert.assertEquals(1, persistenceData.size());
        persistenceData.clear();
        Assert.assertEquals(0, persistenceData.size());
    }

    @Test
    public void testPushOne() {
        MergePersistenceData persistenceData = new MergePersistenceData();
        persistenceData.getElseCreate("test_1");
        persistenceData.getElseCreate("test_2");
        persistenceData.getElseCreate("test_3");

        Assert.assertEquals(3, persistenceData.size());
        MergeData mergeData = persistenceData.pushOne();
        Assert.assertEquals("test_3", mergeData.getId());
        Assert.assertEquals(2, persistenceData.size());

        mergeData = persistenceData.pushOne();
        Assert.assertEquals("test_2", mergeData.getId());
        Assert.assertEquals(1, persistenceData.size());

        mergeData = persistenceData.pushOne();
        Assert.assertEquals("test_1", mergeData.getId());
        Assert.assertEquals(0, persistenceData.size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testForEach() {
        MergePersistenceData persistenceData = new MergePersistenceData();
        persistenceData.forEach(c -> System.out.println(c));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testSpliterator() {
        MergePersistenceData persistenceData = new MergePersistenceData();
        persistenceData.spliterator();
    }

    @Test
    public void testIterator() {
        MergePersistenceData persistenceData = new MergePersistenceData();
        persistenceData.getElseCreate("test_1");
        persistenceData.getElseCreate("test_2");
        persistenceData.getElseCreate("test_3");

        Iterator<Map.Entry<String, MergeData>> iterator = persistenceData.iterator();
        Assert.assertEquals("test_3", iterator.next().getKey());
        Assert.assertEquals("test_2", iterator.next().getKey());
        Assert.assertEquals("test_1", iterator.next().getKey());
    }
}
