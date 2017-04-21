package com.a.eye.skywalking.collector.worker.segment.mock;

import com.a.eye.skywalking.collector.queue.EndOfBatchCommand;
import com.a.eye.skywalking.collector.worker.AnalysisMember;
import com.a.eye.skywalking.collector.worker.segment.SegmentPost;
import com.a.eye.skywalking.collector.worker.segment.entity.Segment;
import com.a.eye.skywalking.collector.worker.segment.entity.SegmentDeserialize;
import com.a.eye.skywalking.collector.worker.tools.DateTools;
import com.a.eye.skywalking.collector.worker.tools.JsonFileReader;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author pengys5
 */
public class SegmentMock {
    private String path = this.getClass().getResource("/").getPath();

    private final String CacheServiceJsonFile = path + "/json/segment/post/normal/cache-service.json";
    private final String PersistenceServiceJsonFile = path + "/json/segment/post/normal/persistence-service.json";
    private final String PortalServiceJsonFile = path + "/json/segment/post/normal/portal-service.json";

    private final String CacheServiceExceptionJsonFile = path + "/json/segment/post/exception/cache-service.json";
    private final String PortalServiceExceptionJsonFile = path + "/json/segment/post/exception/portal-service.json";

    private final String SpecialJsonFile = path + "/json/segment/post/special/special.json";

    public String loadJsonFile(String fileName) throws FileNotFoundException {
        return JsonFileReader.INSTANCE.read(path + fileName);
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

    public List<SegmentPost.SegmentWithTimeSlice> mockCacheServiceExceptionSegmentTimeSlice() throws Exception {
        return createSegmentWithTimeSliceList(CacheServiceExceptionJsonFile);
    }

    public List<SegmentPost.SegmentWithTimeSlice> mockPortalServiceExceptionSegmentTimeSlice() throws Exception {
        return createSegmentWithTimeSliceList(PortalServiceExceptionJsonFile);
    }

    public List<SegmentPost.SegmentWithTimeSlice> mockCacheServiceSegmentSegmentTimeSlice() throws Exception {
        return createSegmentWithTimeSliceList(CacheServiceJsonFile);
    }

    public List<SegmentPost.SegmentWithTimeSlice> mockPersistenceServiceSegmentTimeSlice() throws Exception {
        return createSegmentWithTimeSliceList(PersistenceServiceJsonFile);
    }

    public List<SegmentPost.SegmentWithTimeSlice> mockPortalServiceSegmentSegmentTimeSlice() throws Exception {
        return createSegmentWithTimeSliceList(PortalServiceJsonFile);
    }

    private List<SegmentPost.SegmentWithTimeSlice> createSegmentWithTimeSliceList(String jsonFilePath) throws Exception {
        List<Segment> segmentList = SegmentDeserialize.INSTANCE.deserializeMultiple(jsonFilePath);

        List<SegmentPost.SegmentWithTimeSlice> segmentWithTimeSliceList = new ArrayList<>();
        for (Segment segment : segmentList) {
            SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice = createSegmentWithTimeSlice(segment);
            segmentWithTimeSliceList.add(segmentWithTimeSlice);
        }
        return segmentWithTimeSliceList;
    }

    private SegmentPost.SegmentWithTimeSlice createSegmentWithTimeSlice(Segment segment) {
        long minuteSlice = DateTools.getMinuteSlice(segment.getStartTime());
        long hourSlice = DateTools.getHourSlice(segment.getStartTime());
        long daySlice = DateTools.getDaySlice(segment.getStartTime());
        int second = DateTools.getSecond(segment.getStartTime());

        SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice = new SegmentPost.SegmentWithTimeSlice(segment, minuteSlice, hourSlice, daySlice, second);
        return segmentWithTimeSlice;
    }

    public void executeAnalysis(AnalysisMember analysis) throws Exception {
        List<SegmentPost.SegmentWithTimeSlice> cacheServiceSegment = this.mockCacheServiceSegmentSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : cacheServiceSegment) {
            analysis.analyse(segmentWithTimeSlice);
        }

        List<SegmentPost.SegmentWithTimeSlice> portalServiceSegment = this.mockPortalServiceSegmentSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : portalServiceSegment) {
            analysis.analyse(segmentWithTimeSlice);
        }

        List<SegmentPost.SegmentWithTimeSlice> persistenceServiceSegment = this.mockPersistenceServiceSegmentTimeSlice();
        for (SegmentPost.SegmentWithTimeSlice segmentWithTimeSlice : persistenceServiceSegment) {
            analysis.analyse(segmentWithTimeSlice);
        }

        analysis.onWork(new EndOfBatchCommand());
    }
}
