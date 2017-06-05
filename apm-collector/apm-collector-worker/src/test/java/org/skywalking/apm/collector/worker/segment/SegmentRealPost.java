package org.skywalking.apm.collector.worker.segment;

import org.skywalking.apm.collector.worker.segment.mock.SegmentMock;

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
        HttpClientTools.INSTANCE.post("http://localhost:12800/segments", cacheServiceSegmentAsString);

        String persistenceServiceSegmentAsString = mock.mockPersistenceServiceSegmentAsString();
        HttpClientTools.INSTANCE.post("http://localhost:12800/segments", persistenceServiceSegmentAsString);

        String portalServiceSegmentAsString = mock.mockPortalServiceSegmentAsString();
        HttpClientTools.INSTANCE.post("http://localhost:12800/segments", portalServiceSegmentAsString);

//        String specialSegmentAsString = mock.mockSpecialSegmentAsString();
//        HttpClientTools.INSTANCE.post("http://localhost:7001/segments", specialSegmentAsString);

    }
}
