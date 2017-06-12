package org.skywalking.apm.collector.worker.segment.entity;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The <code>SegmentDeserialize</code> provides single segment json string deserialize and segment array file
 * deserialize.
 *
 * @author pengys5
 * @since v3.0-2017
 */
public enum SegmentDeserialize {
    INSTANCE;

    private final Gson gson = new Gson();

    /**
     * Single segment json string deserialize.
     *
     * @param singleSegmentJsonStr a segment json string
     * @return an {@link Segment}
     * @throws IOException if json string illegal or file broken.
     */
    public Segment deserializeSingle(String singleSegmentJsonStr) throws IOException {
        Segment segment = gson.fromJson(singleSegmentJsonStr, Segment.class);
        return segment;
    }

    /**
     * Read a json array file contains multiple segments.
     *
     * @param segmentJsonFile a segments json array file path
     * @return on {@link List<Segment>}
     * @throws Exception if json data illegal or file broken.
     */
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
