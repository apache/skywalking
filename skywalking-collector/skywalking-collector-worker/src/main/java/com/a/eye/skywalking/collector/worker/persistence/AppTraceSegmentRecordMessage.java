package com.a.eye.skywalking.collector.worker.persistence;

import com.a.eye.skywalking.collector.worker.TimeSliceMessage;

/**
 * @author pengys5
 */
public class AppTraceSegmentRecordMessage extends TimeSliceMessage {
    public AppTraceSegmentRecordMessage(String timeSlice) {
        super(timeSlice);
    }
}
