package org.skywalking.apm.collector.worker.segment.mock;

import com.google.gson.JsonArray;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.collector.queue.EndOfBatchCommand;
import org.skywalking.apm.collector.worker.AnalysisMember;
import org.skywalking.apm.collector.worker.segment.SegmentReceiver;
import org.skywalking.apm.collector.worker.segment.entity.SegmentDeserializeFromFile;
import org.skywalking.apm.collector.worker.tools.JsonFileReader;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.skywalking.apm.network.proto.UpstreamSegment;

/**
 * @author pengys5
 */
public class SegmentMock {
    private static String path = SegmentMock.class.getResource("/").getPath();

    private final String CacheServiceJsonFile = path + "/json/segment/post/normal/cache-service.json";
    private final String PersistenceServiceJsonFile = path + "/json/segment/post/normal/persistence-service.json";
    public static final String PortalServiceJsonFile = path + "/json/segment/grpc/normal/portal-service.json";

    private final String CacheServiceExceptionJsonFile = path + "/json/segment/post/exception/cache-service.json";
    private final String PortalServiceExceptionJsonFile = path + "/json/segment/post/exception/portal-service.json";

    private final String SpecialJsonFile = path + "/json/segment/post/special/special.json";

    public String loadJsonFile(String fileName) throws FileNotFoundException {
        return JsonFileReader.INSTANCE.read(path + fileName);
    }

    public String mockCacheServiceSegmentAsString() throws FileNotFoundException {
        return JsonFileReader.INSTANCE.readSegment(CacheServiceJsonFile);
    }

    public String mockCacheServiceExceptionSegmentAsString() throws FileNotFoundException {
        return JsonFileReader.INSTANCE.read(CacheServiceExceptionJsonFile);
    }

    public String mockPersistenceServiceSegmentAsString() throws FileNotFoundException {
        return JsonFileReader.INSTANCE.read(PersistenceServiceJsonFile);
    }

    public static List<UpstreamSegment> mockPortalServiceSegment() throws FileNotFoundException {
        List<UpstreamSegment> upstreamSegmentList = new LinkedList<>();
        JsonArray segmentArray = JsonFileReader.INSTANCE.parse(PortalServiceJsonFile).getAsJsonArray();
        segmentArray.forEach(segmentObj -> {
            UpstreamSegment upstreamSegment = UpstreamSegmentFromJson.INSTANCE.build(segmentObj.getAsJsonObject());
            upstreamSegmentList.add(upstreamSegment);
        });

        return upstreamSegmentList;
    }

    public List<SegmentReceiver.SegmentWithTimeSlice> mockCacheServiceExceptionSegmentTimeSlice() throws Exception {
        return createSegmentWithTimeSliceList(CacheServiceExceptionJsonFile);
    }

    public List<SegmentReceiver.SegmentWithTimeSlice> mockPortalServiceExceptionSegmentTimeSlice() throws Exception {
        return createSegmentWithTimeSliceList(PortalServiceExceptionJsonFile);
    }

    public List<SegmentReceiver.SegmentWithTimeSlice> mockCacheServiceSegmentSegmentTimeSlice() throws Exception {
        return createSegmentWithTimeSliceList(CacheServiceJsonFile);
    }

    public List<SegmentReceiver.SegmentWithTimeSlice> mockPersistenceServiceSegmentTimeSlice() throws Exception {
        return createSegmentWithTimeSliceList(PersistenceServiceJsonFile);
    }

    public List<SegmentReceiver.SegmentWithTimeSlice> mockPortalServiceSegmentSegmentTimeSlice() throws Exception {
        return createSegmentWithTimeSliceList(PortalServiceJsonFile);
    }

    private List<SegmentReceiver.SegmentWithTimeSlice> createSegmentWithTimeSliceList(
        String jsonFilePath) throws Exception {
        List<TraceSegmentObject> segmentList = SegmentDeserializeFromFile.INSTANCE.deserializeMultiple(jsonFilePath);

        List<SegmentReceiver.SegmentWithTimeSlice> segmentWithTimeSliceList = new ArrayList<>();
        for (TraceSegmentObject segment : segmentList) {
            SegmentReceiver.SegmentWithTimeSlice segmentWithTimeSlice = createSegmentWithTimeSlice(segment);
            segmentWithTimeSliceList.add(segmentWithTimeSlice);
        }
        return segmentWithTimeSliceList;
    }

    private SegmentReceiver.SegmentWithTimeSlice createSegmentWithTimeSlice(TraceSegmentObject segment) {
//        long minuteSlice = DateTools.getMinuteSlice(segment.getStartTime());
//        long hourSlice = DateTools.getHourSlice(segment.getStartTime());
//        long daySlice = DateTools.getDaySlice(segment.getStartTime());
//        int second = DateTools.getSecond(segment.getStartTime());

//        SegmentReceiver.SegmentWithTimeSlice segmentWithTimeSlice = new SegmentReceiver.SegmentWithTimeSlice(segment, minuteSlice, hourSlice, daySlice, second);
//        return segmentWithTimeSlice;
        return null;
    }

    public void executeAnalysis(AnalysisMember analysis) throws Exception {
        List<SegmentReceiver.SegmentWithTimeSlice> cacheServiceSegment = this.mockCacheServiceSegmentSegmentTimeSlice();
        for (SegmentReceiver.SegmentWithTimeSlice segmentWithTimeSlice : cacheServiceSegment) {
            analysis.analyse(segmentWithTimeSlice);
        }

        List<SegmentReceiver.SegmentWithTimeSlice> portalServiceSegment = this.mockPortalServiceSegmentSegmentTimeSlice();
        for (SegmentReceiver.SegmentWithTimeSlice segmentWithTimeSlice : portalServiceSegment) {
            analysis.analyse(segmentWithTimeSlice);
        }

        List<SegmentReceiver.SegmentWithTimeSlice> persistenceServiceSegment = this.mockPersistenceServiceSegmentTimeSlice();
        for (SegmentReceiver.SegmentWithTimeSlice segmentWithTimeSlice : persistenceServiceSegment) {
            analysis.analyse(segmentWithTimeSlice);
        }

        analysis.onWork(new EndOfBatchCommand());
    }
}
