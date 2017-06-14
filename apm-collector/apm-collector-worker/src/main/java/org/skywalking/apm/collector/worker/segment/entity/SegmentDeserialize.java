package org.skywalking.apm.collector.worker.segment.entity;

import com.google.gson.Gson;
import java.io.IOException;

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
}
