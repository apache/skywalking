package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.collector.worker.segment.mock.SegmentMock;

/**
 * @author pengys5
 */
public class SegmentRealPost {

    public static void main(String[] args) throws Exception {
        SegmentMock mock = new SegmentMock();
//        String cacheServiceExceptionSegmentAsString = mock.mockCacheServiceExceptionSegmentAsString();
//        HttpClientTools.INSTANCE.post("http://localhost:7001/segments", cacheServiceExceptionSegmentAsString);
//
//        String portalServiceExceptionSegmentAsString = mock.mockPortalServiceExceptionSegmentAsString();
//        HttpClientTools.INSTANCE.post("http://localhost:7001/segments", portalServiceExceptionSegmentAsString);

        String cacheServiceSegmentAsString = mock.mockCacheServiceSegmentAsString();
        HttpClientTools.INSTANCE.post("http://localhost:7001/segments", cacheServiceSegmentAsString);

        String persistenceServiceSegmentAsString = mock.mockPersistenceServiceSegmentAsString();
        HttpClientTools.INSTANCE.post("http://localhost:7001/segments", persistenceServiceSegmentAsString);

        String portalServiceSegmentAsString = mock.mockPortalServiceSegmentAsString();
        HttpClientTools.INSTANCE.post("http://localhost:7001/segments", portalServiceSegmentAsString);

//        String specialSegmentAsString = mock.mockSpecialSegmentAsString();
//        HttpClientTools.INSTANCE.post("http://localhost:7001/segments", specialSegmentAsString);

    }
}
