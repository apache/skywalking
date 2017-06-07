package org.skywalking.apm.trace;

import com.google.gson.Gson;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * The <code>SegmentsMessage</code> is a set of {@link TraceSegment},
 * this set provides a container, when several {@link TraceSegment}s are going to uplink to server.
 *
 * @author wusheng
 */
public class SegmentsMessage {
    private List<TraceSegment> segments;

    public SegmentsMessage() {
        segments = new LinkedList<TraceSegment>();
    }

    public void append(TraceSegment segment) {
        this.segments.add(segment);
    }

    public List<TraceSegment> getSegments() {
        return Collections.unmodifiableList(segments);
    }

    /**
     * This serialization mechanism started from 3.1, it is similar to network package.
     * The data protocol is
     *
     * segment1.json.length + ' '(one blank space) + segment1.json
     * + segment2.json.length + ' '(one blank space) + segment2.json
     * + etc.
     *
     * @param gson the serializer for {@link TraceSegment}
     * @return the string represents the <code>SegmentMessage</code>
     */
    public String serialize(Gson gson) {
        StringBuilder buffer = new StringBuilder();
        for (TraceSegment segment : segments) {
            String segmentJson = gson.toJson(segment);
            buffer.append(segmentJson.length()).append(' ').append(segmentJson);
        }
        return buffer.toString();
    }
}
