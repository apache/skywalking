package org.apache.skywalking.apm.collector.cache.guava;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CacheUtilsUnitTest {

    private Cache<Integer, String> testCache = CacheBuilder.newBuilder().maximumSize(10).build();

    @Before
    public void init() {
        testCache.put(5, "five");
    }

    @Test
    public void retrieve() {
        String value = CacheUtils.retrieve(testCache, 5, () -> null);
        assertEquals(value, "five");

        value = CacheUtils.retrieve(testCache, 10, () -> "ten");
        assertEquals(value, "ten");
        assertEquals(value, testCache.getIfPresent(10)); //put into the cache

    }

    @Test
    public void retrieveOrElse() {
        String value = CacheUtils.retrieveOrElse(testCache, 12, () -> null, "twelve");
        assertEquals(value, "twelve");
    }
}