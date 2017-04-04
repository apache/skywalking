package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.collector.worker.segment.persistence.SegmentSave;
import com.google.gson.JsonObject;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author pengys5
 */
public class SegmentSaveTestCase {

    @Test
    public void testAnalyse() throws Exception{
        SegmentSave segmentSave = Mockito.mock(SegmentSave.class);

        JsonObject segment_1 = new JsonObject();
        segmentSave.analyse(segment_1);
    }
}
