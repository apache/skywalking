package org.skywalking.apm.collector.worker.segment.entity;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.network.proto.TraceSegmentObject;

/**
 * @author pengys5
 */
public enum SegmentDeserializeFromFile {
    INSTANCE;

    private final Gson gson = new Gson();

    /**
     * Read a json array file contains multiple segments.
     *
     * @param segmentJsonFile a segments json array file path
     * @return on {@link List <Segment>}
     * @throws Exception if json data illegal or file broken.
     */
    public List<TraceSegmentObject> deserializeMultiple(String segmentJsonFile) throws Exception {
        List<TraceSegmentObject> segmentList = new ArrayList<>();
        streamReader(segmentList, new FileReader(segmentJsonFile));
        return segmentList;
    }

    private void streamReader(List<TraceSegmentObject> segmentList, FileReader fileReader) throws Exception {
        JsonArray segmentArray = gson.fromJson(fileReader, JsonArray.class);
        for (int i = 0; i < segmentArray.size(); i++) {
            JsonObject segmentObj = segmentArray.get(i).getAsJsonObject();
        }
    }
}
