package org.apache.skywalking.plugin.test.agent.tool.validator.entity;

import java.util.List;

/**
 * Created by xin on 2017/7/15.
 */
public interface SegmentItem {
    String applicationCode();

    String segmentSize();

    List<Segment> segments();
}
