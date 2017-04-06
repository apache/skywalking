package com.a.eye.skywalking.collector.worker.segment.mock;

import com.a.eye.skywalking.collector.worker.segment.SegmentPost;
import com.a.eye.skywalking.collector.worker.tools.DateTools;
import com.a.eye.skywalking.collector.worker.tools.JsonFileReader;
import com.a.eye.skywalking.trace.SegmentsMessage;
import com.a.eye.skywalking.trace.TraceSegment;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author pengys5
 */
public class SegmentMock {

    private Logger logger = LogManager.getFormatterLogger(SegmentMock.class);

    private Gson gson = new Gson();
    private String path = this.getClass().getResource("/").getPath();

    private final String CacheServiceJsonFile = path + "/json/segment/post/normal/cache-service.json";
    private final String PersistenceServiceJsonFile = path + "/json/segment/post/normal/persistence-service.json";
    private final String PortalServiceJsonFile = path + "/json/segment/post/normal/portal-service.json";

    private final String CacheServiceExceptionJsonFile = path + "/json/segment/post/exception/cache-service.json";
    private final String PortalServiceExceptionJsonFile = path + "/json/segment/post/exception/portal-service.json";

    private final String SpecialJsonFile = path + "/json/segment/post/special/special.json";

    public String mockSpecialSegmentAsString() throws FileNotFoundException {
        return JsonFileReader.INSTANCE.read(SpecialJsonFile);
    }

    public String mockCacheServiceSegmentAsString() throws FileNotFoundException {
        return JsonFileReader.INSTANCE.read(CacheServiceJsonFile);
    }

    public String mockPersistenceServiceSegmentAsString() throws FileNotFoundException {
        return JsonFileReader.INSTANCE.read(PersistenceServiceJsonFile);
    }

    public String mockPortalServiceSegmentAsString() throws FileNotFoundException {
        return JsonFileReader.INSTANCE.read(PortalServiceJsonFile);
    }

    public String mockCacheServiceExceptionSegmentAsString() throws FileNotFoundException {
        return JsonFileReader.INSTANCE.read(CacheServiceExceptionJsonFile);
    }

    public String mockPortalServiceExceptionSegmentAsString() throws FileNotFoundException {
        return JsonFileReader.INSTANCE.read(PortalServiceExceptionJsonFile);
    }

    public List<SegmentPost.SegmentWithTimeSlice> mockSpecialSegmentTimeSlice() throws FileNotFoundException {
        String specialSegmentAsString = mockSpecialSegmentAsString();
        logger.debug(specialSegmentAsString);
        return createSegmentWithTimeSliceList(specialSegmentAsString);
    }

    public List<SegmentPost.SegmentWithTimeSlice> mockCacheServiceExceptionSegmentTimeSlice() throws FileNotFoundException {
        String cacheServiceExceptionSegmentAsString = mockCacheServiceExceptionSegmentAsString();
        logger.debug(cacheServiceExceptionSegmentAsString);
        return createSegmentWithTimeSliceList(cacheServiceExceptionSegmentAsString);
    }

    public List<SegmentPost.SegmentWithTimeSlice> mockPortalServiceExceptionSegmentTimeSlice() throws FileNotFoundException {
        String portalServiceExceptionSegmentAsString = mockPortalServiceExceptionSegmentAsString();
        logger.debug(portalServiceExceptionSegmentAsString);
        return createSegmentWithTimeSliceList(portalServiceExceptionSegmentAsString);
    }

    public List<SegmentPost.SegmentWithTimeSlice> mockCacheServiceSegmentSegmentTimeSlice() throws FileNotFoundException {
        String cacheServiceSegmentAsString = mockCacheServiceSegmentAsString();
        logger.debug(cacheServiceSegmentAsString);
        return createSegmentWithTimeSliceList(cacheServiceSegmentAsString);
    }

    public List<SegmentPost.SegmentWithTimeSlice> mockPersistenceServiceSegmentTimeSlice() throws FileNotFoundException {
        String persistenceServiceSegmentAsString = mockPersistenceServiceSegmentAsString();
        logger.debug(persistenceServiceSegmentAsString);
        return createSegmentWithTimeSliceList(persistenceServiceSegmentAsString);
    }

    public List<SegmentPost.SegmentWithTimeSlice> mockPortalServiceSegmentSegmentTimeSlice() throws FileNotFoundException {
        String portalServiceSegmentAsString = mockPortalServiceSegmentAsString();
        logger.debug(portalServiceSegmentAsString);
        return createSegmentWithTimeSliceList(portalServiceSegmentAsString);
    }

    private List<SegmentPost.SegmentWithTimeSlice> createSegmentWithTimeSliceList(String segmentJsonStr) {
        SegmentsMessage segmentsMessage = gson.fromJson(segmentJsonStr, SegmentsMessage.class);
        List<TraceSegment> segmentList = segmentsMessage.getSegments();

        List<SegmentPost.SegmentWithTimeSlice> segmentWithTimeSliceList = new ArrayList<>();
        for (TraceSegment newSegment : segmentList) {
            SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice = createSegmentWithTimeSlice(newSegment);
            segmentWithTimeSliceList.add(segmentWithTimeSlice);
        }
        return segmentWithTimeSliceList;
    }

    private SegmentPost.SegmentWithTimeSlice createSegmentWithTimeSlice(TraceSegment newSegment) {
        long minuteSlice = DateTools.getMinuteSlice(newSegment.getStartTime());
        long hourSlice = DateTools.getHourSlice(newSegment.getStartTime());
        long daySlice = DateTools.getDaySlice(newSegment.getStartTime());
        int second = DateTools.getSecond(newSegment.getStartTime());

        SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice = new SegmentPost.SegmentWithTimeSlice(newSegment, minuteSlice, hourSlice, daySlice, second);
        return segmentWithTimeSlice;
    }
}
