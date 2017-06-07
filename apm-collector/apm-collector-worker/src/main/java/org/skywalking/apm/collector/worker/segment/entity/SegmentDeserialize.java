package org.skywalking.apm.collector.worker.segment.entity;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author pengys5
 */
public enum SegmentDeserialize {
    INSTANCE;

    private final Gson gson = new Gson();

    public Segment deserializeSingle(String singleSegmentJsonStr) throws IOException {
        Segment segment = gson.fromJson(singleSegmentJsonStr, Segment.class);
        return segment;
    }

    public List<Segment> deserializeMultiple(String segmentJsonFile) throws Exception {
        List<Segment> segmentList = new ArrayList<>();
        streamReader(segmentList, new FileReader(segmentJsonFile));
        return segmentList;
    }

    private void streamReader(List<Segment> segmentList, FileReader fileReader) throws Exception {
        JsonArray segmentArray = gson.fromJson(fileReader, JsonArray.class);
        for (int i = 0; i < segmentArray.size(); i++) {
            Segment segment = gson.fromJson(segmentArray.get(i), Segment.class);
            segmentList.add(segment);
        }
    }
}
