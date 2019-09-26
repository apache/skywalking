package org.apache.skywalking.plugin.test.mockcollector.entity;

import java.util.HashMap;
import java.util.Map;

public class SegmentItems {
    private Map<String, SegmentItem> segmentItems;

    public SegmentItems() {
        this.segmentItems = new HashMap<>();
    }

    public SegmentItems addSegmentItem(int applicationId, Segment segment) {
        String applicationCode = ValidateData.INSTANCE.getRegistryItem().findApplicationCode(applicationId);
        SegmentItem segmentItem = segmentItems.get(applicationCode);
        if (segmentItem == null) {
            segmentItem = new SegmentItem(applicationCode);
            segmentItems.put(applicationCode, segmentItem);
        }
        segmentItem.addSegments(segment);
        return this;
    }

    public Map<String, SegmentItem> getSegmentItems() {
        return segmentItems;
    }
}
