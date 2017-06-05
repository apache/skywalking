package org.skywalking.apm.agent.core.collector.sender;

import com.google.gson.GsonBuilder;
import org.skywalking.apm.agent.core.conf.Config;
import org.skywalking.apm.trace.SegmentsMessage;

public class TraceSegmentSender extends HttpPostSender<SegmentsMessage> {
    @Override
    public String url() {
        return Config.Collector.Services.SEGMENT_REPORT;
    }

    @Override
    public String serializeData(SegmentsMessage data) {
        return new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().toJson(data);
    }

    @Override
    protected void dealWithResponse(int statusCode, String responseBody) {

    }
}
