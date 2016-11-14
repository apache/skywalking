package com.a.eye.skywalking.storage.listener;

import com.a.eye.skywalking.health.report.HealthCollector;
import com.a.eye.skywalking.health.report.HeathReading;
import com.a.eye.skywalking.logging.api.ILog;
import com.a.eye.skywalking.logging.api.LogManager;
import com.a.eye.skywalking.network.grpc.Span;
import com.a.eye.skywalking.network.listener.TraceSearchListener;
import com.a.eye.skywalking.storage.data.SpanDataFinder;
import com.a.eye.skywalking.storage.data.spandata.SpanData;
import com.a.eye.skywalking.storage.data.spandata.SpanDataHelper;

import java.util.ArrayList;
import java.util.List;

public class SearchListener implements TraceSearchListener {

    private static ILog logger = LogManager.getLogger(SearchListener.class);

    @Override
    public List<Span> search(String traceId) {
        try {
            List<SpanData> data = SpanDataFinder.find(traceId);
            SpanDataHelper helper = new SpanDataHelper(data);
            List<Span> span = helper.category().mergeData();
            HealthCollector.getCurrentHeathReading("SearchListener")
                    .updateData(HeathReading.INFO, span.size() + "  spans was founded by trace Id [" + traceId + "].");
            return span;
        } catch (Exception e) {
            logger.error("Failed to search trace Id [{}]", traceId, e);
            HealthCollector.getCurrentHeathReading("SearchListener")
                    .updateData(HeathReading.ERROR, "Failed to search trace Id" + traceId + ".");
            return new ArrayList<Span>();
        }
    }
}
