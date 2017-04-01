package com.a.eye.skywalking.collector.worker.segment.mock;

import com.a.eye.skywalking.collector.worker.tools.JsonFileReader;

import java.io.FileNotFoundException;

/**
 * @author pengys5
 */
public class SegmentMock {

    private String path = this.getClass().getResource("/").getPath();

    private final String CacheServiceJsonFile = path + "/json/segment/post/normal/cache-service.json";
    private final String PersistenceServiceJsonFile = path + "/json/segment/post/normal/persistence-service.json";
    private final String PortalServiceJsonFile = path + "/json/segment/post/normal/portal-service.json";

    public String mockCacheServiceSegmentAsString() throws FileNotFoundException {
        return JsonFileReader.INSTANCE.read(CacheServiceJsonFile);
    }

    public String mockPersistenceServiceSegmentAsString() throws FileNotFoundException {
        return JsonFileReader.INSTANCE.read(PersistenceServiceJsonFile);
    }

    public String mockPortalServiceSegmentAsString() throws FileNotFoundException {
        return JsonFileReader.INSTANCE.read(PortalServiceJsonFile);
    }
}
